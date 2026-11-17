package com.dlssgamespace.core.hardware

import com.dlssgamespace.core.root.RootCommandResult
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class CpuClusterInfo(
  val clusterId: Int,
  val name: String,
  val coreIds: List<Int>,
  val policyPath: String,
  val currentFreqKhz: Long?,
  val hardwareMinKhz: Long?,
  val hardwareMaxKhz: Long?,
  val scalingMinKhz: Long?,
  val scalingMaxKhz: Long?,
  val availableFreqsKhz: List<Long>,
  val currentGovernor: String,
  val availableGovernors: List<String>,
  val isWritable: Boolean,
)

data class CpuSnapshot(
  val coreCount: Int,
  val usagePercent: Float?,
  val loadAvg: String,
  val cpuTempCelsius: Float?,
  val isThrottling: Boolean,
  val throttlingReason: String,
  val clusters: List<CpuClusterInfo>,
  val hardwareModel: String,
)

enum class CpuProfilePreset(val id: String, val label: String) {
  BATTERY_SAVER("BATTERY_SAVER", "Battery Saver"),
  BALANCED("BALANCED", "Balanced"),
  PERFORMANCE("PERFORMANCE", "Performance"),
  EXTREME_GAMING("EXTREME_GAMING", "Extreme Gaming"),
}

class CpuController(
  private val rootEngine: RootEngine,
  private val controlRepository: SystemControlRepository,
) {
  private var prevTotalTicks: Long? = null
  private var prevIdleTicks: Long? = null

  suspend fun readSnapshot(): CpuSnapshot =
    withContext(Dispatchers.IO) {
      val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
      val clusters = discoverClusters()
      val usagePercent = readCpuUsagePercent()
      val loadAvg = readLoadAvg()
      val cpuTemp = readCpuTemperature()

      val throttledClusters =
        clusters.filter { c ->
          val hwMax = c.hardwareMaxKhz ?: return@filter false
          val scMax = c.scalingMaxKhz ?: return@filter false
          scMax < (hwMax * 0.90).toLong()
        }

      val isThrottling = throttledClusters.isNotEmpty() || (cpuTemp != null && cpuTemp >= 82f)
      val throttlingReason =
        when {
          throttledClusters.isNotEmpty() ->
            "Freq capped on ${throttledClusters.joinToString { it.name }}"
          cpuTemp != null && cpuTemp >= 82f -> "High SoC thermal (${cpuTemp.roundToInt()} °C)"
          else -> "None detected"
        }

      val hardwareModel = readCpuHardwareName()

      CpuSnapshot(
        coreCount = coreCount,
        usagePercent = usagePercent,
        loadAvg = loadAvg,
        cpuTempCelsius = cpuTemp,
        isThrottling = isThrottling,
        throttlingReason = throttlingReason,
        clusters = clusters,
        hardwareModel = hardwareModel,
      )
    }

  private suspend fun discoverClusters(): List<CpuClusterInfo> {
    val cpufreqDir = File("/sys/devices/system/cpu/cpufreq")
    val policyDirs =
      cpufreqDir
        .listFiles { file -> file.isDirectory && file.name.startsWith("policy") }
        ?.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
        .orEmpty()

    if (policyDirs.isNotEmpty()) {
      return policyDirs.mapIndexed { idx, dir -> readClusterFromPolicyDir(idx, dir.absolutePath) }
    }

    // Fallback: group by cpuX/cpufreq/related_cpus
    val cpuRoot = File("/sys/devices/system/cpu")
    val cpuDirs =
      cpuRoot
        .listFiles { f -> f.isDirectory && Regex("^cpu[0-9]+$").matches(f.name) }
        ?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
        .orEmpty()

    val seenCoreGroups = LinkedHashSet<String>()
    val result = mutableListOf<CpuClusterInfo>()
    for (cpuDir in cpuDirs) {
      val cpufreq = File(cpuDir, "cpufreq")
      if (!cpufreq.exists()) continue
      val related = rootEngine.readSysfsNode("${cpufreq.absolutePath}/related_cpus") ?: cpuDir.name.removePrefix("cpu")
      if (seenCoreGroups.add(related)) {
        result.add(readClusterFromPolicyDir(result.size, cpufreq.absolutePath))
      }
    }
    return result
  }

  private suspend fun readClusterFromPolicyDir(index: Int, policyPath: String): CpuClusterInfo {
    val relatedRaw = rootEngine.readSysfsNode("$policyPath/related_cpus")
    val coreIds =
      relatedRaw
        ?.split(Regex("\\s+"))
        ?.mapNotNull { it.toIntOrNull() }
        ?.sorted()
        ?: listOf(index)

    val curFreq = rootEngine.readSysfsNode("$policyPath/scaling_cur_freq")?.toLongOrNull()
    val hwMin = rootEngine.readSysfsNode("$policyPath/cpuinfo_min_freq")?.toLongOrNull()
    val hwMax = rootEngine.readSysfsNode("$policyPath/cpuinfo_max_freq")?.toLongOrNull()
    val scMin = rootEngine.readSysfsNode("$policyPath/scaling_min_freq")?.toLongOrNull()
    val scMax = rootEngine.readSysfsNode("$policyPath/scaling_max_freq")?.toLongOrNull()
    val availFreqs =
      rootEngine
        .readSysfsNode("$policyPath/scaling_available_frequencies")
        ?.split(Regex("\\s+"))
        ?.mapNotNull { it.toLongOrNull() }
        ?.distinct()
        ?.sorted()
        .orEmpty()

    val curGov = rootEngine.readSysfsNode("$policyPath/scaling_governor") ?: "N/A"
    val availGovs =
      rootEngine
        .readSysfsNode("$policyPath/scaling_available_governors")
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotBlank() }
        ?.distinct()
        .orEmpty()

    val name =
      when {
        coreIds.size == 1 -> "Prime Cluster (CPU${coreIds.first()})"
        index == 0 -> "Efficiency Cluster (CPU${coreIds.first()}–${coreIds.last()})"
        else -> "Performance Cluster (CPU${coreIds.first()}–${coreIds.last()})"
      }

    val writable = rootEngine.environment.value.isRootGranted && File("$policyPath/scaling_governor").exists()

    return CpuClusterInfo(
      clusterId = index,
      name = name,
      coreIds = coreIds,
      policyPath = policyPath,
      currentFreqKhz = curFreq,
      hardwareMinKhz = hwMin,
      hardwareMaxKhz = hwMax,
      scalingMinKhz = scMin,
      scalingMaxKhz = scMax,
      availableFreqsKhz = availFreqs,
      currentGovernor = curGov,
      availableGovernors = availGovs,
      isWritable = writable,
    )
  }

  suspend fun setClusterGovernor(cluster: CpuClusterInfo, governor: String): RootCommandResult {
    if (cluster.availableGovernors.isNotEmpty() && !cluster.availableGovernors.contains(governor)) {
      return RootCommandResult(
        command = "set governor $governor",
        exitCode = -1,
        stdout = "",
        stderr = "Governor '$governor' not supported by ${cluster.name}",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "UNSUPPORTED_GOVERNOR",
      )
    }

    val path = "${cluster.policyPath}/scaling_governor"
    val original = rootEngine.readSysfsNode(path) ?: cluster.currentGovernor
    val res = rootEngine.writeSysfsNode(path, governor, "CPU_GOVERNOR")
    if (res.succeeded && original != "N/A") {
      controlRepository.recordBackupBeforeWrite(path, "CPU", original, governor)
    }
    return res
  }

  suspend fun setClusterMinFreq(cluster: CpuClusterInfo, freqKhz: Long): RootCommandResult {
    val validFreq = validateFrequencyAgainstCluster(cluster, freqKhz)
    if (validFreq == null) {
      return RootCommandResult(
        command = "set min freq $freqKhz",
        exitCode = -1,
        stdout = "",
        stderr = "Frequency ${freqKhz / 1000} MHz not supported by ${cluster.name}",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "UNSUPPORTED_FREQ",
      )
    }
    val path = "${cluster.policyPath}/scaling_min_freq"
    val original = rootEngine.readSysfsNode(path) ?: cluster.scalingMinKhz?.toString()
    val res = rootEngine.writeSysfsNode(path, validFreq.toString(), "CPU_MIN_FREQ")
    if (res.succeeded && original != null) {
      controlRepository.recordBackupBeforeWrite(path, "CPU", original, validFreq.toString())
    }
    return res
  }

  suspend fun setClusterMaxFreq(cluster: CpuClusterInfo, freqKhz: Long): RootCommandResult {
    val validFreq = validateFrequencyAgainstCluster(cluster, freqKhz)
    if (validFreq == null) {
      return RootCommandResult(
        command = "set max freq $freqKhz",
        exitCode = -1,
        stdout = "",
        stderr = "Frequency ${freqKhz / 1000} MHz not supported by ${cluster.name}",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "UNSUPPORTED_FREQ",
      )
    }
    val path = "${cluster.policyPath}/scaling_max_freq"
    val original = rootEngine.readSysfsNode(path) ?: cluster.scalingMaxKhz?.toString()
    val res = rootEngine.writeSysfsNode(path, validFreq.toString(), "CPU_MAX_FREQ")
    if (res.succeeded && original != null) {
      controlRepository.recordBackupBeforeWrite(path, "CPU", original, validFreq.toString())
    }
    return res
  }

  suspend fun applyProfile(
    preset: CpuProfilePreset,
    governorOverride: String? = null,
    minPercentOverride: Int? = null,
    maxPercentOverride: Int? = null,
  ): List<RootCommandResult> {
    val clusters = discoverClusters()
    if (clusters.isEmpty()) return emptyList()

    val results = mutableListOf<RootCommandResult>()
    for (cluster in clusters) {
      val targetGov =
        when {
          !governorOverride.isNullOrBlank() && cluster.availableGovernors.contains(governorOverride) ->
            governorOverride
          preset == CpuProfilePreset.BATTERY_SAVER ->
            cluster.availableGovernors.firstOrNull { it == "powersave" || it == "conservative" || it == "schedutil" }
          preset == CpuProfilePreset.BALANCED ->
            cluster.availableGovernors.firstOrNull { it == "schedutil" || it == "walt" || it == "interactive" }
          preset == CpuProfilePreset.PERFORMANCE ->
            cluster.availableGovernors.firstOrNull { it == "walt" || it == "schedutil" || it == "performance" }
          preset == CpuProfilePreset.EXTREME_GAMING ->
            cluster.availableGovernors.firstOrNull { it == "performance" || it == "walt" || it == "schedutil" }
          else -> null
        }

      if (targetGov != null) {
        results.add(setClusterGovernor(cluster, targetGov))
      }

      val freqs = cluster.availableFreqsKhz
      if (freqs.isNotEmpty()) {
        val minPct =
          minPercentOverride
            ?: when (preset) {
              CpuProfilePreset.BATTERY_SAVER -> 0
              CpuProfilePreset.BALANCED -> 0
              CpuProfilePreset.PERFORMANCE -> 25
              CpuProfilePreset.EXTREME_GAMING -> 45
            }
        val maxPct =
          maxPercentOverride
            ?: when (preset) {
              CpuProfilePreset.BATTERY_SAVER -> 55
              CpuProfilePreset.BALANCED -> 85
              CpuProfilePreset.PERFORMANCE -> 100
              CpuProfilePreset.EXTREME_GAMING -> 100
            }
        val minIdx = ((freqs.lastIndex) * (minPct.coerceIn(0, 100) / 100f)).roundToInt().coerceIn(0, freqs.lastIndex)
        val maxIdx = ((freqs.lastIndex) * (maxPct.coerceIn(0, 100) / 100f)).roundToInt().coerceIn(minIdx, freqs.lastIndex)
        val targetMin = freqs[minIdx]
        val targetMax = freqs[maxIdx]

        // Apply max first if raising, or min first if lowering, to avoid invalid kernel min>max rejection
        val currentMin = cluster.scalingMinKhz ?: targetMin
        if (targetMax >= currentMin) {
          results.add(setClusterMaxFreq(cluster, targetMax))
          results.add(setClusterMinFreq(cluster, targetMin))
        } else {
          results.add(setClusterMinFreq(cluster, targetMin))
          results.add(setClusterMaxFreq(cluster, targetMax))
        }
      }
    }
    return results
  }

  private fun validateFrequencyAgainstCluster(cluster: CpuClusterInfo, freqKhz: Long): Long? {
    if (cluster.availableFreqsKhz.isNotEmpty()) {
      return if (cluster.availableFreqsKhz.contains(freqKhz)) {
        freqKhz
      } else {
        cluster.availableFreqsKhz.minByOrNull { kotlin.math.abs(it - freqKhz) }
      }
    }
    val min = cluster.hardwareMinKhz ?: return null
    val max = cluster.hardwareMaxKhz ?: return null
    return freqKhz.coerceIn(min, max)
  }

  private suspend fun readCpuUsagePercent(): Float? {
    val statLine =
      rootEngine.readSysfsNode("/proc/stat")?.lineSequence()?.firstOrNull { it.startsWith("cpu ") }
        ?: return null
    val parts = statLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
    if (parts.size < 4) return null

    val idle = parts[3] + parts.getOrElse(4) { 0L }
    val total = parts.sum()

    val oldTotal = prevTotalTicks
    val oldIdle = prevIdleTicks
    prevTotalTicks = total
    prevIdleTicks = idle

    if (oldTotal == null || oldIdle == null) return null
    val totalDelta = (total - oldTotal).coerceAtLeast(0L)
    val idleDelta = (idle - oldIdle).coerceAtLeast(0L)
    if (totalDelta <= 0L) return null
    return (((totalDelta - idleDelta).toFloat() / totalDelta.toFloat()) * 100f).coerceIn(0f, 100f)
  }

  private suspend fun readLoadAvg(): String {
    val raw = rootEngine.readSysfsNode("/proc/loadavg") ?: return "N/A"
    val parts = raw.split(Regex("\\s+"))
    return if (parts.size >= 3) "${parts[0]} · ${parts[1]} · ${parts[2]}" else raw
  }

  private suspend fun readCpuTemperature(): Float? {
    val thermalDir = File("/sys/class/thermal")
    val zones =
      thermalDir.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") } ?: return null
    val temps = mutableListOf<Float>()
    for (zone in zones) {
      val type = rootEngine.readSysfsNode("${zone.absolutePath}/type")?.lowercase() ?: continue
      if (
        type.contains("cpu") ||
          type.contains("tsens") ||
          type.contains("kryo") ||
          type.contains("soc") ||
          type.contains("aoss")
      ) {
        val rawTemp = rootEngine.readSysfsNode("${zone.absolutePath}/temp")?.toFloatOrNull() ?: continue
        val celsius = if (rawTemp > 1000f) rawTemp / 1000f else if (rawTemp > 200f) rawTemp / 10f else rawTemp
        if (celsius in 5f..125f) temps.add(celsius)
      }
    }
    return temps.maxOrNull()
  }

  private suspend fun readCpuHardwareName(): String {
    val cpuInfo = rootEngine.readSysfsNode("/proc/cpuinfo") ?: return "N/A"
    val hardwareLine =
      cpuInfo
        .lineSequence()
        .firstOrNull { it.startsWith("Hardware", ignoreCase = true) || it.startsWith("model name", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
    return hardwareLine?.takeIf { it.isNotBlank() } ?: "ARM64 SoC"
  }
}
