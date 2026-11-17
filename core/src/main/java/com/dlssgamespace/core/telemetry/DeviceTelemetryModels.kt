package com.dlssgamespace.core.telemetry

data class DeviceHardwareInfo(
  val deviceName: String,
  val androidVersion: String,
  val apiLevel: Int,
  val securityPatch: String,
  val chipset: String,
  val abi: String,
)

data class DisplayInfo(
  val resolutionLabel: String,
  val refreshRateHz: Float?,
  val refreshRateLabel: String,
  val supportedRefreshRates: List<Int>,
)

data class MemoryInfoSnapshot(
  val totalBytes: Long?,
  val usedBytes: Long?,
  val availableBytes: Long?,
  val usageFraction: Float?,
)

data class StorageInfoSnapshot(
  val totalBytes: Long?,
  val usedBytes: Long?,
  val availableBytes: Long?,
  val usageFraction: Float?,
)

data class BatteryInfoSnapshot(
  val levelPercent: Int?,
  val temperatureCelsius: Float?,
  val statusLabel: String,
  val pluggedLabel: String,
  val isCharging: Boolean,
)

data class DeviceTelemetry(
  val hardware: DeviceHardwareInfo,
  val display: DisplayInfo,
  val memory: MemoryInfoSnapshot,
  val storage: StorageInfoSnapshot,
  val battery: BatteryInfoSnapshot,
  val lastUpdatedAtMillis: Long,
)
