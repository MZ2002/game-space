package com.dlssgamespace.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_profiles")
data class GameProfileEntity(
  @PrimaryKey val packageName: String,
  val performanceProfile: String = "BALANCED",
  val thermalMode: String = "BALANCED",
  val preferredRefreshRateHz: Int = 0,
  val autoApplyOnLaunch: Boolean = true,
  val autoShowOverlay: Boolean = false,
  val boostGameProcessPriority: Boolean = true,
  val ioSchedulerOverride: String? = null,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_performance_profiles")
data class CustomPerformanceProfileEntity(
  @PrimaryKey val profileId: String,
  val displayName: String,
  val cpuGovernorPreference: String,
  val cpuMinFreqPercent: Int,
  val cpuMaxFreqPercent: Int,
  val gpuBiasPercent: Int,
  val swappinessTarget: Int,
  val thermalTargetMode: String,
  val isCustomized: Boolean = false,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "system_backups")
data class SystemBackupEntity(
  @PrimaryKey val nodePath: String,
  val category: String,
  val originalValue: String,
  val lastAppliedValue: String,
  val capturedAt: Long,
  val isRestored: Boolean = false,
)

@Entity(tableName = "thermal_history")
data class ThermalHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMs: Long,
  val cpuTempCelsius: Float?,
  val gpuTempCelsius: Float?,
  val batteryTempCelsius: Float?,
  val isThrottling: Boolean,
  val activeMode: String,
)

@Entity(tableName = "root_command_logs")
data class RootCommandLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMs: Long,
  val category: String,
  val command: String,
  val exitCode: Int,
  val durationMs: Long,
  val succeeded: Boolean,
  val outputPreview: String,
)
