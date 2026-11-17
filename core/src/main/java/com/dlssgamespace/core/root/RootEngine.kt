package com.dlssgamespace.core.root

import android.os.Build
import com.dlssgamespace.core.security.RootSecurityValidator
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

interface SuShellRunner {
  suspend fun runCommand(executable: String, args: List<String>, timeoutMs: Long): RootCommandResult
}

class ProcessSuShellRunner : SuShellRunner {
  override suspend fun runCommand(
    executable: String,
    args: List<String>,
    timeoutMs: Long,
  ): RootCommandResult =
    withContext(Dispatchers.IO) {
      val started = System.currentTimeMillis()
      val fullCommand = (listOf(executable) + args).joinToString(" ")
      var process: Process? = null
      try {
        process = ProcessBuilder(listOf(executable) + args).redirectErrorStream(false).start()
        val result =
          withTimeoutOrNull(timeoutMs) {
            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            RootCommandResult(
              command = fullCommand,
              exitCode = exitCode,
              stdout = stdout,
              stderr = stderr,
              durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L),
              succeeded = exitCode == 0,
            )
          }
        if (result != null) {
          result
        } else {
          process.destroyForcibly()
          RootCommandResult(
            command = fullCommand,
            exitCode = -1,
            stdout = "",
            stderr = "Command timed out after ${timeoutMs}ms",
            durationMs = timeoutMs,
            succeeded = false,
            blockedReason = "TIMEOUT",
          )
        }
      } catch (t: Throwable) {
        runCatching { process?.destroyForcibly() }
        RootCommandResult(
          command = fullCommand,
          exitCode = -1,
          stdout = "",
          stderr = t.message ?: "Execution failed",
          durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L),
          succeeded = false,
        )
      }
    }
}

class RootEngine(
  private val controlRepository: SystemControlRepository,
  private val shellRunner: SuShellRunner = ProcessSuShellRunner(),
) {

  private val _environment = MutableStateFlow(readStaticDeviceEnvironment())
  val environment: StateFlow<RootEnvironmentInfo> = _environment.asStateFlow()

  private var detectedSuBinary: String? = null

  suspend fun detectEnvironment(requestRootPrompt: Boolean = false): RootEnvironmentInfo =
    withContext(Dispatchers.IO) {
      val current = _environment.value
      _environment.value = current.copy(accessState = RootAccessState.CHECKING)

      val suPath = findSuBinary()
      detectedSuBinary = suPath

      val kernelVersionFull =
        readDirectOrNull("/proc/version") ?: System.getProperty("os.version").orEmpty()
      val kernelRelease = readDirectOrNull("/proc/sys/kernel/osrelease") ?: kernelVersionFull.take(48)
      val arch = Build.SUPPORTED_ABIS?.firstOrNull() ?: System.getProperty("os.arch") ?: "N/A"
      val busyBoxCandidate = findBusyBoxBinary()

      if (suPath == null && !requestRootPrompt) {
        // Still detect manager indicators on filesystem if visible
        val (mgrType, mgrVer) = detectManagerNonInteractive(suPath = null, kernelVersionFull = kernelVersionFull)
        val updated =
          RootEnvironmentInfo(
            managerType = mgrType,
            managerVersion = mgrVer,
            suBinaryPath = "N/A",
            busyBoxAvailable = busyBoxCandidate != null,
            busyBoxPath = busyBoxCandidate ?: "N/A",
            busyBoxVersion = "N/A",
            kernelRelease = kernelRelease.ifBlank { "N/A" },
            kernelVersionFull = kernelVersionFull.ifBlank { "N/A" },
            seLinuxMode = readDirectOrNull("/sys/fs/selinux/enforce")?.let { formatSeLinux(it) } ?: "N/A",
            architecture = arch,
            accessState = RootAccessState.UNAVAILABLE,
            lastCheckedAtMs = System.currentTimeMillis(),
          )
        _environment.value = updated
        return@withContext updated
      }

      if (!requestRootPrompt) {
        val (mgrType, mgrVer) = detectManagerNonInteractive(suPath, kernelVersionFull)
        val updated =
          _environment.value.copy(
            managerType = mgrType,
            managerVersion = mgrVer,
            suBinaryPath = suPath ?: "N/A",
            busyBoxAvailable = busyBoxCandidate != null,
            busyBoxPath = busyBoxCandidate ?: "N/A",
            kernelRelease = kernelRelease.ifBlank { "N/A" },
            kernelVersionFull = kernelVersionFull.ifBlank { "N/A" },
            seLinuxMode = readDirectOrNull("/sys/fs/selinux/enforce")?.let { formatSeLinux(it) } ?: "N/A",
            architecture = arch,
            accessState =
              if (current.isRootGranted) RootAccessState.GRANTED else RootAccessState.UNKNOWN,
            lastCheckedAtMs = System.currentTimeMillis(),
          )
        _environment.value = updated
        return@withContext updated
      }

      // Interactive validation: runs `su -c id`
      val executable = suPath ?: "su"
      val idResult = shellRunner.runCommand(executable, listOf("-c", "id"), 6_000L)
      logCommand("ROOT_VALIDATION", "su -c id", idResult)

      val granted = idResult.succeeded && idResult.stdout.contains("uid=0")
      val accessState =
        when {
          granted -> RootAccessState.GRANTED
          suPath == null && idResult.stderr.contains("No such file", ignoreCase = true) ->
            RootAccessState.UNAVAILABLE
          else -> RootAccessState.DENIED
        }

      var (mgrType, mgrVer) = detectManagerNonInteractive(suPath, kernelVersionFull)
      var resolvedBusyBox = busyBoxCandidate
      var busyBoxVer = "N/A"
      var seLinux = readDirectOrNull("/sys/fs/selinux/enforce")?.let { formatSeLinux(it) } ?: "N/A"
      var resolvedKernel = kernelRelease

      if (granted) {
        // Query su -v / su -V, ksud, magisk, busybox, getenforce via root safely
        val suVerRes = shellRunner.runCommand(executable, listOf("-v"), 3_000L)
        val suCodeRes = shellRunner.runCommand(executable, listOf("-V"), 3_000L)
        val ksudRes =
          shellRunner.runCommand(
            executable,
            listOf("-c", "if [ -x /data/adb/ksud ]; then /data/adb/ksud -V; elif command -v ksud >/dev/null 2>&1; then ksud -V; fi"),
            3_000L,
          )
        val magiskRes =
          shellRunner.runCommand(
            executable,
            listOf("-c", "if command -v magisk >/dev/null 2>&1; then magisk -v; fi"),
            3_000L,
          )

        val combinedSuTag = "${suVerRes.stdout} ${suCodeRes.stdout} ${ksudRes.stdout}".lowercase()
        when {
          ksudRes.stdout.isNotBlank() || combinedSuTag.contains("kernelsu") || combinedSuTag.contains("ksu") -> {
            mgrType =
              if (combinedSuTag.contains("next") || kernelVersionFull.contains("Next", ignoreCase = true)) {
                RootManagerType.KERNELSU_NEXT
              } else {
                RootManagerType.KERNELSU
              }
            mgrVer = ksudRes.stdout.ifBlank { suVerRes.stdout }.ifBlank { suCodeRes.stdout }.ifBlank { "Detected" }
          }
          magiskRes.stdout.isNotBlank() || combinedSuTag.contains("magisk") -> {
            mgrType = RootManagerType.MAGISK
            mgrVer = magiskRes.stdout.ifBlank { suVerRes.stdout }.ifBlank { "Detected" }
          }
          else -> {
            mgrType = RootManagerType.GENERIC_SU
            mgrVer = suVerRes.stdout.ifBlank { suCodeRes.stdout }.ifBlank { "su uid=0" }
          }
        }

        val bbRes =
          shellRunner.runCommand(
            executable,
            listOf(
              "-c",
              "for p in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /system/xbin/busybox /system/bin/busybox; do if [ -x \"\$p\" ]; then echo \"\$p|\$(\"\$p\" --help | head -n 1)\"; break; fi; done",
            ),
            3_000L,
          )
        if (bbRes.succeeded && bbRes.stdout.contains("|")) {
          val parts = bbRes.stdout.split("|", limit = 2)
          resolvedBusyBox = parts[0].trim()
          busyBoxVer = parts.getOrNull(1)?.trim()?.ifBlank { "Available" } ?: "Available"
        }

        val enforceRes = shellRunner.runCommand(executable, listOf("-c", "getenforce"), 2_500L)
        if (enforceRes.succeeded && enforceRes.stdout.isNotBlank()) {
          seLinux = enforceRes.stdout
        }

        val unameRes = shellRunner.runCommand(executable, listOf("-c", "uname -r"), 2_500L)
        if (unameRes.succeeded && unameRes.stdout.isNotBlank()) {
          resolvedKernel = unameRes.stdout
        }
      }

      val info =
        RootEnvironmentInfo(
          managerType = mgrType,
          managerVersion = mgrVer,
          suBinaryPath = suPath ?: if (granted) "su (PATH)" else "N/A",
          busyBoxAvailable = resolvedBusyBox != null,
          busyBoxPath = resolvedBusyBox ?: "N/A",
          busyBoxVersion = busyBoxVer,
          kernelRelease = resolvedKernel.ifBlank { "N/A" },
          kernelVersionFull = kernelVersionFull.ifBlank { "N/A" },
          seLinuxMode = seLinux,
          architecture = arch,
          accessState = accessState,
          lastCheckedAtMs = System.currentTimeMillis(),
        )
      _environment.value = info
      info
    }

  suspend fun readSysfsNode(path: String): String? {
    val validation = RootSecurityValidator.validateReadPath(path)
    if (validation !is RootSecurityValidator.ValidationResult.Valid) return null

    // Fast path: direct Java file read without spawning a root shell
    readDirectOrNull(validation.normalized)?.let { return it }

    // Fallback: root read only if root has been granted
    if (!_environment.value.isRootGranted) return null
    val su = detectedSuBinary ?: "su"
    val res = shellRunner.runCommand(su, listOf("-c", "cat '${validation.normalized}'"), 2_500L)
    return if (res.succeeded) res.stdout else null
  }

  suspend fun writeSysfsNode(
    path: String,
    value: String,
    category: String,
  ): RootCommandResult {
    if (!_environment.value.isRootGranted) {
      return RootCommandResult(
        command = "write $path=$value",
        exitCode = -1,
        stdout = "",
        stderr = "Root permission not granted (Limited Mode)",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "NO_ROOT",
      )
    }

    val pathCheck = RootSecurityValidator.validateWritePath(path)
    if (pathCheck is RootSecurityValidator.ValidationResult.Rejected) {
      val blocked =
        RootCommandResult(
          command = "write $path=$value",
          exitCode = -1,
          stdout = "",
          stderr = pathCheck.reason,
          durationMs = 0L,
          succeeded = false,
          blockedReason = "SECURITY_PATH_BLOCKED",
        )
      logCommand(category, blocked.command, blocked)
      return blocked
    }

    val valueCheck = RootSecurityValidator.validateWriteValue(value)
    if (valueCheck is RootSecurityValidator.ValidationResult.Rejected) {
      val blocked =
        RootCommandResult(
          command = "write $path=$value",
          exitCode = -1,
          stdout = "",
          stderr = valueCheck.reason,
          durationMs = 0L,
          succeeded = false,
          blockedReason = "SECURITY_VALUE_BLOCKED",
        )
      logCommand(category, blocked.command, blocked)
      return blocked
    }

    val safePath = (pathCheck as RootSecurityValidator.ValidationResult.Valid).normalized
    val safeVal = (valueCheck as RootSecurityValidator.ValidationResult.Valid).normalized
    val su = detectedSuBinary ?: "su"

    val script = "chmod 644 '$safePath' 2>/dev/null; printf '%s' '$safeVal' > '$safePath' && cat '$safePath'"
    val result = shellRunner.runCommand(su, listOf("-c", script), 3_500L)
    val verified = result.succeeded && (result.stdout == safeVal || result.stdout.contains(safeVal))
    val finalResult =
      result.copy(
        succeeded = verified,
        stderr =
          if (!verified && result.stderr.isBlank()) {
            "Kernel rejected value '$safeVal' (readback: '${result.stdout}')"
          } else {
            result.stderr
          },
      )
    logCommand(category, "printf '%s' '$safeVal' > $safePath", finalResult)
    return finalResult
  }

  suspend fun executeAllowlistedCommand(
    category: String,
    shellCommand: String,
    timeoutMs: Long = 3_500L,
  ): RootCommandResult {
    if (!_environment.value.isRootGranted) {
      return RootCommandResult(
        command = shellCommand,
        exitCode = -1,
        stdout = "",
        stderr = "Root permission not granted (Limited Mode)",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "NO_ROOT",
      )
    }
    val su = detectedSuBinary ?: "su"
    val res = shellRunner.runCommand(su, listOf("-c", shellCommand), timeoutMs)
    logCommand(category, shellCommand, res)
    return res
  }

  private suspend fun logCommand(category: String, cmd: String, res: RootCommandResult) {
    runCatching {
      val preview =
        when {
          res.succeeded -> res.stdout.take(180).ifBlank { "OK" }
          res.stderr.isNotBlank() -> res.stderr.take(180)
          else -> "Exit ${res.exitCode}"
        }
      controlRepository.recordRootLog(
        RootCommandLogEntity(
          timestampMs = System.currentTimeMillis(),
          category = category,
          command = cmd.take(240),
          exitCode = res.exitCode,
          durationMs = res.durationMs,
          succeeded = res.succeeded,
          outputPreview = preview,
        )
      )
    }
  }

  private fun readStaticDeviceEnvironment(): RootEnvironmentInfo {
    val arch = Build.SUPPORTED_ABIS?.firstOrNull() ?: "N/A"
    val kernel = readDirectOrNull("/proc/sys/kernel/osrelease") ?: "N/A"
    return RootEnvironmentInfo(
      kernelRelease = kernel,
      architecture = arch,
      accessState = RootAccessState.UNKNOWN,
    )
  }

  private fun detectManagerNonInteractive(
    suPath: String?,
    kernelVersionFull: String,
  ): Pair<RootManagerType, String> {
    val hasKsu =
      File("/data/adb/ksu").exists() ||
        File("/data/adb/ksud").exists() ||
        kernelVersionFull.contains("kernelsu", ignoreCase = true)
    val hasMagisk =
      File("/data/adb/magisk").exists() ||
        File("/sbin/.magisk").exists() ||
        (suPath?.contains("magisk", ignoreCase = true) == true)

    return when {
      hasKsu && kernelVersionFull.contains("next", ignoreCase = true) ->
        RootManagerType.KERNELSU_NEXT to "KernelSU-Next"
      hasKsu -> RootManagerType.KERNELSU to "KernelSU"
      hasMagisk -> RootManagerType.MAGISK to "Magisk"
      suPath != null -> RootManagerType.GENERIC_SU to "Detected"
      else -> RootManagerType.NONE to "N/A"
    }
  }

  private fun findSuBinary(): String? {
    val candidates =
      listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/magisk/su",
        "/debug_ramdisk/su",
      )
    candidates.firstOrNull { File(it).exists() }?.let { return it }

    val pathEnv = System.getenv("PATH") ?: return null
    for (dir in pathEnv.split(':')) {
      val f = File(dir, "su")
      if (f.exists()) return f.absolutePath
    }
    return null
  }

  private fun findBusyBoxBinary(): String? {
    val candidates =
      listOf(
        "/data/adb/magisk/busybox",
        "/data/adb/ksu/bin/busybox",
        "/system/xbin/busybox",
        "/system/bin/busybox",
        "/sbin/busybox",
      )
    return candidates.firstOrNull { File(it).exists() }
  }

  private fun readDirectOrNull(path: String): String? =
    runCatching {
        val file = File(path)
        if (file.exists() && file.canRead()) {
          file.readText().trim().takeIf { it.isNotEmpty() }
        } else {
          null
        }
      }
      .getOrNull()

  private fun formatSeLinux(raw: String): String =
    when (raw.trim()) {
      "1" -> "Enforcing"
      "0" -> "Permissive"
      else -> raw.trim()
    }
}
