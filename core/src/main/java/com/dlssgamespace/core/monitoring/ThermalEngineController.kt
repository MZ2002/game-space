package com.dlssgamespace.core.monitoring

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.CpuProfilePreset
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.GpuProfilePreset
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.entity.ThermalHistoryEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ThermalMode(val id: String, val label: String, val description: String) {
  COOL("COOL", "Cool Mode", "Prioritizes low skin & battery temperature by capping clocks early."),
  BALANCED("BALANCED", "Balanced Mode", "Standard thermal headroom aligned with vendor safety."),
  PERFORMANCE("PERFORMANCE", "Performance Mode", "Allows sustained boost clocks up to safe thermal thresholds."),
  GAMING("GAMING", "Gaming Mode", "Maximizes frame stability while stepping down before thermal throttling."),
  INTELLIGENT_AUTO(
    "INTELLIGENT_AUTO",
    "Intelligent Auto",
    "Rule + history adaptive controller reacting to temperature trends in real time.",
  );

  companion object {
    fun fromId(id: String): ThermalMode = entries.firstOrNull { it.id == id } ?: BALANCED
  }
}

enum class ThermalAlertLevel(val label: String) {
  NORMAL("Normal"),
  WARM("Warm"),
  HOT("Hot"),
  CRITICAL("Critical"),
}

data class ThermalZoneReading(
  val zoneName: String,
  val type: String,
  val tempCelsius: Float,
)

data class ThermalSnapshot(
  val cpuTempCelsius: Float?,
  val gpuTempCelsius: Float?,
  val batteryTempCelsius: Float?,
  val skinTempCelsius: Float?,
  val maxZoneTempCelsius: Float?,
  val isThrottling: Boolean,
  val alertLevel: ThermalAlertLevel,
  val alertMessage: String,
  val zones: List<ThermalZoneReading>,
  val adaptiveActionTaken: String? = null,
)

class ThermalEngineController(
  private val context: Context,
  private val rootEngine: RootEngine,
  private val controlRepository: SystemControlRepository,
  private val cpuController: CpuController,
  private val gpuController: GpuController,
) {

  suspend fun readAndEvaluate(
    engineEnabled: Boolean,
    mode: ThermalMode,
    recordToHistory: Boolean = false,
  ): ThermalSnapshot =
    withContext(Dispatchers.IO) {
      val zones = readAllThermalZones()
      val cpuTemp =
        zones
          .filter { z ->
            val t = z.type.lowercase()
            t.contains("cpu") || t.contains("tsens") || t.contains("kryo") || t.contains("soc")
          }
          .maxOfOrNull { it.tempCelsius }

      val gpuTemp =
        zones
          .filter { z ->
            val t = z.type.lowercase()
            t.contains("gpu") || t.contains("gpuss") || t.contains("kgsl")
          }
          .maxOfOrNull { it.tempCelsius }

      val skinTemp =
        zones
          .filter { z ->
            val t = z.type.lowercase()
            t.contains("skin") || t.contains("xo-therm") || t.contains("quiet-therm")
          }
          .maxOfOrNull { it.tempCelsius }

      val batteryTemp = readBatteryTempCelsius()
      val maxZone = zones.maxOfOrNull { it.tempCelsius }

      val cpuSnap = cpuController.readSnapshot()
      val gpuSnap = gpuController.readSnapshot()
      val isThrottling = cpuSnap.isThrottling || gpuSnap.isThrottling

      val alertLevel =
        when {
          (batteryTemp != null && batteryTemp >= 45f) || (cpuTemp != null && cpuTemp >= 88f) || isThrottling ->
            ThermalAlertLevel.CRITICAL
          (batteryTemp != null && batteryTemp >= 41f) || (cpuTemp != null && cpuTemp >= 76f) ->
            ThermalAlertLevel.HOT
          (batteryTemp != null && batteryTemp >= 37f) || (cpuTemp != null && cpuTemp >= 62f) ->
            ThermalAlertLevel.WARM
          else -> ThermalAlertLevel.NORMAL
        }

      var adaptiveAction: String? = null
      if (engineEnabled && rootEngine.environment.value.isRootGranted) {
        adaptiveAction = applyRuleAndHistoryAdaptation(mode, alertLevel, cpuTemp, batteryTemp)
      }

      val alertMsg =
        when (alertLevel) {
          ThermalAlertLevel.CRITICAL ->
            "Thermal safety alert: High temperature or hardware throttling active. Stepping down clocks."
          ThermalAlertLevel.HOT -> "Device running hot. Monitor sustained load closely."
          ThermalAlertLevel.WARM -> "Normal gaming warmth."
          ThermalAlertLevel.NORMAL -> "Thermals nominal."
        }

      if (recordToHistory) {
        runCatching {
          controlRepository.recordThermalSample(
            ThermalHistoryEntity(
              timestampMs = System.currentTimeMillis(),
              cpuTempCelsius = cpuTemp,
              gpuTempCelsius = gpuTemp,
              batteryTempCelsius = batteryTemp,
              isThrottling = isThrottling,
              activeMode = if (engineEnabled) mode.id else "OFF",
            )
          )
        }
      }

      ThermalSnapshot(
        cpuTempCelsius = cpuTemp,
        gpuTempCelsius = gpuTemp,
        batteryTempCelsius = batteryTemp,
        skinTempCelsius = skinTemp,
        maxZoneTempCelsius = maxZone,
        isThrottling = isThrottling,
        alertLevel = alertLevel,
        alertMessage = alertMsg,
        zones = zones,
        adaptiveActionTaken = adaptiveAction,
      )
    }

  private suspend fun applyRuleAndHistoryAdaptation(
    mode: ThermalMode,
    alertLevel: ThermalAlertLevel,
    cpuTemp: Float?,
    batteryTemp: Float?,
  ): String? {
    // Safety-first rule: never override hardware thermal safety; only step DOWN app optimization
    if (alertLevel == ThermalAlertLevel.CRITICAL || (batteryTemp != null && batteryTemp >= 44f)) {
      cpuController.applyProfile(CpuProfilePreset.BATTERY_SAVER)
      gpuController.applyProfile(GpuProfilePreset.BATTERY_SAVER)
      return "Safety step-down applied (Battery Saver) due to high temperature"
    }

    if (mode == ThermalMode.COOL && (alertLevel == ThermalAlertLevel.WARM || alertLevel == ThermalAlertLevel.HOT)) {
      cpuController.applyProfile(CpuProfilePreset.BATTERY_SAVER)
      return "Cool Mode active: capped CPU bias for lower thermals"
    }

    if (mode == ThermalMode.INTELLIGENT_AUTO) {
      if (alertLevel == ThermalAlertLevel.HOT || (cpuTemp != null && cpuTemp >= 78f)) {
        cpuController.applyProfile(CpuProfilePreset.BALANCED)
        gpuController.applyProfile(GpuProfilePreset.BALANCED)
        return "Intelligent Auto stepped down to Balanced to prevent throttling"
      }
    }
    return null
  }

  private suspend fun readAllThermalZones(): List<ThermalZoneReading> {
    val thermalDir = File("/sys/class/thermal")
    val dirs =
      thermalDir
        .listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
        ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: Int.MAX_VALUE }
        .orEmpty()

    val readings = mutableListOf<ThermalZoneReading>()
    for (dir in dirs.take(32)) {
      val type = rootEngine.readSysfsNode("${dir.absolutePath}/type") ?: continue
      val raw = rootEngine.readSysfsNode("${dir.absolutePath}/temp")?.toFloatOrNull() ?: continue
      val celsius =
        when {
          raw > 1000f -> raw / 1000f
          raw > 200f -> raw / 10f
          else -> raw
        }
      if (celsius in -10f..135f) {
        readings.add(ThermalZoneReading(zoneName = dir.name, type = type, tempCelsius = celsius))
      }
    }
    return readings
  }

  private fun readBatteryTempCelsius(): Float? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    return if (tenths != Int.MIN_VALUE && tenths in -200..1200) tenths / 10f else null
  }
}
