package com.dlssgamespace.core.hardware

import android.app.ActivityManager
import android.content.Context
import com.dlssgamespace.core.root.RootCommandResult
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.security.RootSecurityValidator
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ProcessEntry(
  val pid: Int,
  val name: String,
  val rssKb: Long?,
  val nice: Int?,
)

data class MemoryManagerSnapshot(
  val totalRamBytes: Long?,
  val usedRamBytes: Long?,
  val availableRamBytes: Long?,
  val ramUsageFraction: Float?,
  val cachedBytes: Long?,
  val swapTotalBytes: Long?,
  val swapUsedBytes: Long?,
  val zramDiskSizeBytes: Long?,
  val zramOrigDataBytes: Long?,
  val zramComprDataBytes: Long?,
  val zramAlgorithm: String,
  val swappiness: Int?,
  val vfsCachePressure: Int?,
  val lmkMinFree: String,
  val topProcesses: List<ProcessEntry>,
)

class MemoryManager(
  private val context: Context,
  private val rootEngine: RootEngine,
  private val controlRepository: SystemControlRepository,
) {

  suspend fun readSnapshot(): MemoryManagerSnapshot =
    withContext(Dispatchers.IO) {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      val memInfo = ActivityManager.MemoryInfo()
      am?.getMemoryInfo(memInfo)

      val procMem = parseProcMemInfo()
      val totalRam = memInfo.totalMem.takeIf { it > 0L } ?: procMem["MemTotal"]
      val availRam = memInfo.availMem.takeIf { it >= 0L } ?: procMem["MemAvailable"]
      val usedRam = if (totalRam != null && availRam != null) (totalRam - availRam).coerceAtLeast(0L) else null
      val fraction =
        if (totalRam != null && usedRam != null && totalRam > 0L) {
          (usedRam.toDouble() / totalRam.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
          null
        }

      val swapTotal = procMem["SwapTotal"]
      val swapFree = procMem["SwapFree"]
      val swapUsed = if (swapTotal != null && swapFree != null) (swapTotal - swapFree).coerceAtLeast(0L) else null

      val zramExists = File("/sys/block/zram0").exists()
      val zramDisk = if (zramExists) rootEngine.readSysfsNode("/sys/block/zram0/disksize")?.toLongOrNull() else null
      val zramMmStat = if (zramExists) rootEngine.readSysfsNode("/sys/block/zram0/mm_stat") else null
      val mmParts = zramMmStat?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }.orEmpty()
      val zramOrig =
        mmParts.getOrNull(0)
          ?: if (zramExists) rootEngine.readSysfsNode("/sys/block/zram0/orig_data_size")?.toLongOrNull() else null
      val zramCompr =
        mmParts.getOrNull(1)
          ?: if (zramExists) rootEngine.readSysfsNode("/sys/block/zram0/compr_data_size")?.toLongOrNull() else null
      val zramAlg =
        if (zramExists) {
          rootEngine.readSysfsNode("/sys/block/zram0/comp_algorithm")?.let { raw ->
            Regex("\\[([^\\]]+)]").find(raw)?.groupValues?.getOrNull(1) ?: raw
          } ?: "N/A"
        } else {
          "Unsupported"
        }

      val swappiness = rootEngine.readSysfsNode("/proc/sys/vm/swappiness")?.toIntOrNull()
      val vfsPressure = rootEngine.readSysfsNode("/proc/sys/vm/vfs_cache_pressure")?.toIntOrNull()
      val lmkMinFree =
        rootEngine.readSysfsNode("/sys/module/lowmemorykiller/parameters/minfree")
          ?: "Userspace lmkd (sysfs unsupported)"

      val processes = readProcesses(am)

      MemoryManagerSnapshot(
        totalRamBytes = totalRam,
        usedRamBytes = usedRam,
        availableRamBytes = availRam,
        ramUsageFraction = fraction,
        cachedBytes = procMem["Cached"],
        swapTotalBytes = swapTotal,
        swapUsedBytes = swapUsed,
        zramDiskSizeBytes = zramDisk,
        zramOrigDataBytes = zramOrig,
        zramComprDataBytes = zramCompr,
        zramAlgorithm = zramAlg,
        swappiness = swappiness,
        vfsCachePressure = vfsPressure,
        lmkMinFree = lmkMinFree,
        topProcesses = processes,
      )
    }

  suspend fun setSwappiness(value: Int): RootCommandResult {
    val clamped = value.coerceIn(0, 100)
    val path = "/proc/sys/vm/swappiness"
    val original = rootEngine.readSysfsNode(path)
    val res = rootEngine.writeSysfsNode(path, clamped.toString(), "MEMORY_SWAPPINESS")
    if (res.succeeded && original != null) {
      controlRepository.recordBackupBeforeWrite(path, "MEMORY", original, clamped.toString())
    }
    return res
  }

  suspend fun trimPageCacheSafely(): RootCommandResult {
    // Only drops clean page cache (1) after sync; never kills user or system processes
    return rootEngine.executeAllowlistedCommand(
      category = "MEMORY_CACHE_DROP",
      shellCommand = "sync && printf '1' > /proc/sys/vm/drop_caches",
      timeoutMs = 3_500L,
    )
  }

  suspend fun boostGameProcessPriority(packageName: String, niceValue: Int = -5): RootCommandResult {
    if (!RootSecurityValidator.validatePackageName(packageName) || !RootSecurityValidator.validateNiceValue(niceValue)) {
      return RootCommandResult(
        command = "renice $packageName",
        exitCode = -1,
        stdout = "",
        stderr = "Invalid package name or nice value",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "INVALID_INPUT",
      )
    }
    val cmd =
      "PID=\$(pidof '$packageName' | awk '{print \$1}'); if [ -n \"\$PID\" ]; then renice -n $niceValue -p \"\$PID\" && echo \"PID=\$PID nice=$niceValue\"; else echo 'Process not running' >&2; exit 1; fi"
    return rootEngine.executeAllowlistedCommand("GAME_PROCESS_PRIORITY", cmd, 3_000L)
  }

  private suspend fun parseProcMemInfo(): Map<String, Long> {
    val raw = rootEngine.readSysfsNode("/proc/meminfo") ?: return emptyMap()
    val map = HashMap<String, Long>()
    for (line in raw.lineSequence()) {
      val idx = line.indexOf(':')
      if (idx <= 0) continue
      val key = line.substring(0, idx).trim()
      val kb = line.substring(idx + 1).trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: continue
      map[key] = kb * 1024L
    }
    return map
  }

  private suspend fun readProcesses(am: ActivityManager?): List<ProcessEntry> {
    if (rootEngine.environment.value.isRootGranted) {
      val res =
        rootEngine.executeAllowlistedCommand(
          category = "PROCESS_QUERY",
          shellCommand = "ps -eo pid,ni,rss,comm | sort -k3 -nr | head -n 15",
          timeoutMs = 3_000L,
        )
      if (res.succeeded && res.stdout.isNotBlank()) {
        val parsed =
          res.stdout
            .lineSequence()
            .drop(1)
            .mapNotNull { line ->
              val cols = line.trim().split(Regex("\\s+"), limit = 4)
              if (cols.size < 4) return@mapNotNull null
              val pid = cols[0].toIntOrNull() ?: return@mapNotNull null
              val ni = cols[1].toIntOrNull()
              val rss = cols[2].toLongOrNull()
              val name = cols[3]
              ProcessEntry(pid = pid, name = name, rssKb = rss, nice = ni)
            }
            .toList()
        if (parsed.isNotEmpty()) return parsed
      }
    }

    return am?.runningAppProcesses
      ?.take(12)
      ?.map { ProcessEntry(pid = it.pid, name = it.processName, rssKb = null, nice = null) }
      .orEmpty()
  }
}
