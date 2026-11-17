package com.dlssgamespace.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dlssgamespace.core.root.RootCommandResult
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class GpuNodes(
  val rootPath: String,
  val devfreqPath: String?,
  val isKgsl: Boolean,
)

data class GpuSnapshot(
  val isSupported: Boolean,
  val gpuModel: String,
  val sysfsRootPath: String,
  val currentFreqMhz: Long?,
  val minFreqMhz: Long?,
  val maxFreqMhz: Long?,
  val availableFreqsMhz: List<Long>,
  val utilizationPercent: Float?,
  val currentGovernor: String,
  val availableGovernors: List<String>,
  val powerState: String,
  val gpuTempCelsius: Float?,
  val isThrottling: Boolean,
  val thermalPowerLevel: Int?,
  val vulkanVersion: String,
  val openGlEsVersion: String,
  val isWritable: Boolean,
)

enum class GpuProfilePreset(val id: String, val label: String) {
  BATTERY_SAVER("BATTERY_SAVER", "Battery Saver"),
  BALANCED("BALANCED", "Balanced"),
  PERFORMANCE("PERFORMANCE", "Performance"),
  EXTREME_GAMING("EXTREME_GAMING", "Extreme Gaming"),
}

class GpuController(
  private val context: Context,
  private val rootEngine: RootEngine,
  private val controlRepository: SystemControlRepository,
) {

  private var cachedNodes: GpuNodes? = null

  suspend fun readSnapshot(): GpuSnapshot =
    withContext(Dispatchers.IO) {
      val nodes = discoverGpuNodes()
      val (vulkanVer, glesVer) = readGraphicsApiSupport()

      if (nodes == null) {
        return@withContext GpuSnapshot(
          isSupported = false,
          gpuModel = detectGpuModelFallback(),
          sysfsRootPath = "Unsupported / Not exposed",
          currentFreqMhz = null,
          minFreqMhz = null,
          maxFreqMhz = null,
          availableFreqsMhz = emptyList(),
          utilizationPercent = null,
          currentGovernor = "Unsupported",
          availableGovernors = emptyList(),
          powerState = "N/A",
          gpuTempCelsius = readGpuTemperature(),
          isThrottling = false,
          thermalPowerLevel = null,
          vulkanVersion = vulkanVer,
          openGlEsVersion = glesVer,
          isWritable = false,
        )
      }

      val model =
        rootEngine.readSysfsNode("${nodes.rootPath}/gpu_model")
          ?: detectGpuModelFallback()

      // Frequencies: KGSL clock_mhz / gpuclk (Hz) or devfreq cur_freq (Hz)
      val curMhz =
        rootEngine.readSysfsNode("${nodes.rootPath}/clock_mhz")?.toLongOrNull()
          ?: rootEngine.readSysfsNode("${nodes.rootPath}/gpuclk")?.toLongOrNull()?.let { it / 1_000_000L }
          ?: nodes.devfreqPath?.let { df ->
            rootEngine.readSysfsNode("$df/cur_freq")?.toLongOrNull()?.let { hzToMhz(it) }
          }

      val availMhz =
        rootEngine
          .readSysfsNode("${nodes.rootPath}/gpu_available_rates")
          ?.split(Regex("\\s+"))
          ?.mapNotNull { it.toLongOrNull()?.let { v -> hzToMhz(v) } }
          ?.distinct()
          ?.sorted()
          ?: nodes.devfreqPath
            ?.let { df ->
              rootEngine
                .readSysfsNode("$df/available_frequencies")
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toLongOrNull()?.let { v -> hzToMhz(v) } }
                ?.distinct()
                ?.sorted()
            }
            .orEmpty()

      val minMhz =
        rootEngine.readSysfsNode("${nodes.rootPath}/min_clock_mhz")?.toLongOrNull()
          ?: nodes.devfreqPath?.let { df ->
            rootEngine.readSysfsNode("$df/min_freq")?.toLongOrNull()?.let { hzToMhz(it) }
          }
          ?: availMhz.firstOrNull()

      val maxMhz =
        rootEngine.readSysfsNode("${nodes.rootPath}/max_clock_mhz")?.toLongOrNull()
          ?: nodes.devfreqPath?.let { df ->
            rootEngine.readSysfsNode("$df/max_freq")?.toLongOrNull()?.let { hzToMhz(it) }
          }
          ?: availMhz.lastOrNull()

      val busyPct =
        rootEngine.readSysfsNode("${nodes.rootPath}/gpu_busy_percentage")?.let { raw ->
          raw.replace("%", "").trim().toFloatOrNull()?.coerceIn(0f, 100f)
        }
          ?: rootEngine.readSysfsNode("${nodes.rootPath}/gpubusy")?.let { raw ->
            val parts = raw.trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
            if (parts.size >= 2 && parts[1] > 0.0) {
              ((parts[0] / parts[1]) * 100.0).toFloat().coerceIn(0f, 100f)
            } else {
              null
            }
          }

      val curGov =
        nodes.devfreqPath?.let { df -> rootEngine.readSysfsNode("$df/governor") }
          ?: "N/A"
      val availGovs =
        nodes.devfreqPath
          ?.let { df ->
            rootEngine
              .readSysfsNode("$df/available_governors")
              ?.split(Regex("\\s+"))
              ?.filter { it.isNotBlank() }
              ?.distinct()
          }
          .orEmpty()

      val thermalPwrLevel =
        rootEngine.readSysfsNode("${nodes.rootPath}/thermal_pwrlevel")?.toIntOrNull()
      val throttlingFlag =
        rootEngine.readSysfsNode("${nodes.rootPath}/throttling")?.toIntOrNull() ?: 0
      val isThrottling = throttlingFlag > 0 || (thermalPwrLevel != null && thermalPwrLevel > 0)

      val powerLevel = rootEngine.readSysfsNode("${nodes.rootPath}/default_pwrlevel")
      val busSplit = rootEngine.readSysfsNode("${nodes.rootPath}/force_clk_on")
      val powerState =
        when {
          busSplit == "1" -> "Active (Clk Forced)"
          curMhz != null && curMhz > 0L -> "Active (${curMhz} MHz${powerLevel?.let { " · PL $it" }.orEmpty()})"
          else -> "Idle / Sleep"
        }

      val isWritable =
        rootEngine.environment.value.isRootGranted &&
          (File("${nodes.rootPath}/max_clock_mhz").exists() ||
            File("${nodes.rootPath}/min_clock_mhz").exists() ||
            (nodes.devfreqPath != null && File("${nodes.devfreqPath}/governor").exists()))

      GpuSnapshot(
        isSupported = true,
        gpuModel = model,
        sysfsRootPath = nodes.rootPath,
        currentFreqMhz = curMhz,
        minFreqMhz = minMhz,
        maxFreqMhz = maxMhz,
        availableFreqsMhz = availMhz,
        utilizationPercent = busyPct,
        currentGovernor = curGov,
        availableGovernors = availGovs,
        powerState = powerState,
        gpuTempCelsius = readGpuTemperature(),
        isThrottling = isThrottling,
        thermalPowerLevel = thermalPwrLevel,
        vulkanVersion = vulkanVer,
        openGlEsVersion = glesVer,
        isWritable = isWritable,
      )
    }

  suspend fun discoverGpuNodes(): GpuNodes? {
    cachedNodes?.let { return it }

    val kgslPrimary = File("/sys/class/kgsl/kgsl-3d0")
    if (kgslPrimary.exists()) {
      val devfreq = File("${kgslPrimary.absolutePath}/devfreq").takeIf { it.exists() }?.absolutePath
      return GpuNodes(kgslPrimary.absolutePath, devfreq, isKgsl = true).also { cachedNodes = it }
    }

    val devfreqRoot = File("/sys/class/devfreq")
    val devfreqMatch =
      devfreqRoot
        .listFiles()
        ?.firstOrNull { dir ->
          val n = dir.name.lowercase()
          n.contains("kgsl") || n.contains("gpu") || n.contains("mali")
        }
    if (devfreqMatch != null) {
      return GpuNodes(devfreqMatch.absolutePath, devfreqMatch.absolutePath, isKgsl = devfreqMatch.name.contains("kgsl"))
        .also { cachedNodes = it }
    }
    return null
  }

  suspend fun setGpuGovernor(governor: String): RootCommandResult {
    val nodes = discoverGpuNodes()
    val dfPath =
      nodes?.devfreqPath
        ?: return unsupportedResult("GPU devfreq governor node not available on this kernel")
    val govPath = "$dfPath/governor"
    val avail =
      rootEngine.readSysfsNode("$dfPath/available_governors")?.split(Regex("\\s+")).orEmpty()
    if (avail.isNotEmpty() && !avail.contains(governor)) {
      return unsupportedResult("GPU governor '$governor' not supported")
    }
    val original = rootEngine.readSysfsNode(govPath)
    val res = rootEngine.writeSysfsNode(govPath, governor, "GPU_GOVERNOR")
    if (res.succeeded && original != null) {
      controlRepository.recordBackupBeforeWrite(govPath, "GPU", original, governor)
    }
    return res
  }

  suspend fun setGpuMaxClockMhz(freqMhz: Long): RootCommandResult {
    val nodes = discoverGpuNodes() ?: return unsupportedResult("GPU sysfs node not found")
    val kgslMax = "${nodes.rootPath}/max_clock_mhz"
    if (File(kgslMax).exists()) {
      val original = rootEngine.readSysfsNode(kgslMax)
      val res = rootEngine.writeSysfsNode(kgslMax, freqMhz.toString(), "GPU_MAX_FREQ")
      if (res.succeeded && original != null) {
        controlRepository.recordBackupBeforeWrite(kgslMax, "GPU", original, freqMhz.toString())
      }
      return res
    }
    val dfMax = nodes.devfreqPath?.let { "$it/max_freq" }
    if (dfMax != null && File(dfMax).exists()) {
      val hz = freqMhz * 1_000_000L
      val original = rootEngine.readSysfsNode(dfMax)
      val res = rootEngine.writeSysfsNode(dfMax, hz.toString(), "GPU_MAX_FREQ")
      if (res.succeeded && original != null) {
        controlRepository.recordBackupBeforeWrite(dfMax, "GPU", original, hz.toString())
      }
      return res
    }
    return unsupportedResult("Kernel does not expose writable GPU max clock node")
  }

  suspend fun setGpuMinClockMhz(freqMhz: Long): RootCommandResult {
    val nodes = discoverGpuNodes() ?: return unsupportedResult("GPU sysfs node not found")
    val kgslMin = "${nodes.rootPath}/min_clock_mhz"
    if (File(kgslMin).exists()) {
      val original = rootEngine.readSysfsNode(kgslMin)
      val res = rootEngine.writeSysfsNode(kgslMin, freqMhz.toString(), "GPU_MIN_FREQ")
      if (res.succeeded && original != null) {
        controlRepository.recordBackupBeforeWrite(kgslMin, "GPU", original, freqMhz.toString())
      }
      return res
    }
    val dfMin = nodes.devfreqPath?.let { "$it/min_freq" }
    if (dfMin != null && File(dfMin).exists()) {
      val hz = freqMhz * 1_000_000L
      val original = rootEngine.readSysfsNode(dfMin)
      val res = rootEngine.writeSysfsNode(dfMin, hz.toString(), "GPU_MIN_FREQ")
      if (res.succeeded && original != null) {
        controlRepository.recordBackupBeforeWrite(dfMin, "GPU", original, hz.toString())
      }
      return res
    }
    return unsupportedResult("Kernel does not expose writable GPU min clock node")
  }

  suspend fun applyProfile(
    preset: GpuProfilePreset,
    biasPercentOverride: Int? = null,
  ): List<RootCommandResult> {
    val snapshot = readSnapshot()
    if (!snapshot.isSupported) {
      return listOf(unsupportedResult("GPU sysfs nodes unsupported on this device/kernel"))
    }
    val results = mutableListOf<RootCommandResult>()

    val targetGov =
      when (preset) {
        GpuProfilePreset.BATTERY_SAVER ->
          snapshot.availableGovernors.firstOrNull { it == "powersave" || it == "msm-adreno-tz" }
        GpuProfilePreset.BALANCED ->
          snapshot.availableGovernors.firstOrNull { it == "msm-adreno-tz" || it == "simple_ondemand" }
        GpuProfilePreset.PERFORMANCE ->
          snapshot.availableGovernors.firstOrNull { it == "msm-adreno-tz" || it == "performance" }
        GpuProfilePreset.EXTREME_GAMING ->
          snapshot.availableGovernors.firstOrNull { it == "performance" || it == "msm-adreno-tz" }
      }
    if (targetGov != null) {
      results.add(setGpuGovernor(targetGov))
    }

    val rates = snapshot.availableFreqsMhz
    if (rates.isNotEmpty()) {
      val bias =
        biasPercentOverride
          ?: when (preset) {
            GpuProfilePreset.BATTERY_SAVER -> 45
            GpuProfilePreset.BALANCED -> 80
            GpuProfilePreset.PERFORMANCE -> 100
            GpuProfilePreset.EXTREME_GAMING -> 100
          }
      val minIdx =
        when (preset) {
          GpuProfilePreset.BATTERY_SAVER -> 0
          GpuProfilePreset.BALANCED -> 0
          GpuProfilePreset.PERFORMANCE -> (rates.lastIndex * 0.25f).roundToInt()
          GpuProfilePreset.EXTREME_GAMING -> (rates.lastIndex * 0.50f).roundToInt()
        }.coerceIn(0, rates.lastIndex)
      val maxIdx = (rates.lastIndex * (bias.coerceIn(20, 100) / 100f)).roundToInt().coerceIn(minIdx, rates.lastIndex)

      results.add(setGpuMaxClockMhz(rates[maxIdx]))
      results.add(setGpuMinClockMhz(rates[minIdx]))
    }

    return results
  }

  private fun hzToMhz(value: Long): Long = if (value > 10_000L) value / 1_000_000L else value

  private suspend fun readGpuTemperature(): Float? {
    val thermalDir = File("/sys/class/thermal")
    val zones =
      thermalDir.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") } ?: return null
    val temps = mutableListOf<Float>()
    for (zone in zones) {
      val type = rootEngine.readSysfsNode("${zone.absolutePath}/type")?.lowercase() ?: continue
      if (type.contains("gpu") || type.contains("gpuss") || type.contains("kgsl")) {
        val raw = rootEngine.readSysfsNode("${zone.absolutePath}/temp")?.toFloatOrNull() ?: continue
        val celsius = if (raw > 1000f) raw / 1000f else if (raw > 200f) raw / 10f else raw
        if (celsius in 5f..125f) temps.add(celsius)
      }
    }
    return temps.maxOrNull()
  }

  private fun readGraphicsApiSupport(): Pair<String, String> {
    val pm = context.packageManager
    val vulkanFeature =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching {
            pm.systemAvailableFeatures
              ?.firstOrNull { it?.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
              ?.version
              ?.let { v ->
                val major = (v shr 22) and 0x3FF
                val minor = (v shr 12) and 0x3FF
                "Vulkan $major.$minor"
              }
          }
          .getOrNull() ?: "Unsupported"
      } else {
        "N/A"
      }
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val gles =
      runCatching { am?.deviceConfigurationInfo?.glEsVersion }
        .getOrNull()
        ?.let { "OpenGL ES $it" } ?: "N/A"
    return vulkanFeature to gles
  }

  private fun detectGpuModelFallback(): String {
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
    return when {
      soc.contains("SM8350", ignoreCase = true) || soc.contains("888") -> "Qualcomm Adreno 660"
      else -> "GPU (${Build.HARDWARE})"
    }
  }

  private fun unsupportedResult(reason: String) =
    RootCommandResult(
      command = "gpu_control",
      exitCode = -1,
      stdout = "",
      stderr = reason,
      durationMs = 0L,
      succeeded = false,
      blockedReason = "UNSUPPORTED",
    )
}
