package com.dlssgamespace.database.repository

import com.dlssgamespace.database.DlssDatabase
import com.dlssgamespace.database.entity.CustomPerformanceProfileEntity
import com.dlssgamespace.database.entity.GameProfileEntity
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.database.entity.SystemBackupEntity
import com.dlssgamespace.database.entity.ThermalHistoryEntity
import kotlinx.coroutines.flow.Flow

class SystemControlRepository(private val database: DlssDatabase) {
  private val controlDao = database.controlDao()

  fun observeGameProfile(packageName: String): Flow<GameProfileEntity?> =
    controlDao.observeGameProfile(packageName)

  suspend fun getGameProfileOnce(packageName: String): GameProfileEntity? =
    controlDao.getGameProfileOnce(packageName)

  suspend fun saveGameProfile(profile: GameProfileEntity) {
    controlDao.upsertGameProfile(profile)
  }

  fun observeCustomProfiles(): Flow<List<CustomPerformanceProfileEntity>> =
    controlDao.observeCustomProfiles()

  suspend fun ensureDefaultProfilesSeeded() {
    val now = System.currentTimeMillis()
    val defaults =
      listOf(
        CustomPerformanceProfileEntity(
          profileId = "BATTERY_SAVER",
          displayName = "Battery Saver",
          cpuGovernorPreference = "powersave",
          cpuMinFreqPercent = 0,
          cpuMaxFreqPercent = 55,
          gpuBiasPercent = 40,
          swappinessTarget = 80,
          thermalTargetMode = "COOL",
          isCustomized = false,
          updatedAt = now,
        ),
        CustomPerformanceProfileEntity(
          profileId = "BALANCED",
          displayName = "Balanced",
          cpuGovernorPreference = "schedutil",
          cpuMinFreqPercent = 0,
          cpuMaxFreqPercent = 85,
          gpuBiasPercent = 70,
          swappinessTarget = 60,
          thermalTargetMode = "BALANCED",
          isCustomized = false,
          updatedAt = now,
        ),
        CustomPerformanceProfileEntity(
          profileId = "PERFORMANCE",
          displayName = "Performance",
          cpuGovernorPreference = "schedutil",
          cpuMinFreqPercent = 25,
          cpuMaxFreqPercent = 100,
          gpuBiasPercent = 90,
          swappinessTarget = 40,
          thermalTargetMode = "PERFORMANCE",
          isCustomized = false,
          updatedAt = now,
        ),
        CustomPerformanceProfileEntity(
          profileId = "EXTREME_GAMING",
          displayName = "Extreme Gaming",
          cpuGovernorPreference = "performance",
          cpuMinFreqPercent = 45,
          cpuMaxFreqPercent = 100,
          gpuBiasPercent = 100,
          swappinessTarget = 25,
          thermalTargetMode = "GAMING",
          isCustomized = false,
          updatedAt = now,
        ),
      )
    controlDao.insertDefaultProfilesIgnore(defaults)
  }

  suspend fun getProfileConfig(profileId: String): CustomPerformanceProfileEntity? {
    ensureDefaultProfilesSeeded()
    return controlDao.getCustomProfileOnce(profileId)
  }

  suspend fun saveCustomProfile(profile: CustomPerformanceProfileEntity) {
    controlDao.upsertCustomProfile(profile)
  }

  fun observeAllBackups(): Flow<List<SystemBackupEntity>> = controlDao.observeBackups()

  fun observeActiveBackups(): Flow<List<SystemBackupEntity>> = controlDao.observeActiveBackups()

  suspend fun getActiveBackupsOnce(): List<SystemBackupEntity> = controlDao.getActiveBackupsOnce()

  /**
   * Captures the original hardware value the very first time a node is touched (until restored),
   * ensuring Emergency Restore always reverts to the true pre-tweak value.
   */
  suspend fun recordBackupBeforeWrite(
    nodePath: String,
    category: String,
    originalValue: String,
    appliedValue: String,
  ) {
    val existing = controlDao.getBackupByPath(nodePath)
    val preservedOriginal =
      if (existing != null && !existing.isRestored) existing.originalValue else originalValue
    controlDao.upsertBackup(
      SystemBackupEntity(
        nodePath = nodePath,
        category = category,
        originalValue = preservedOriginal,
        lastAppliedValue = appliedValue,
        capturedAt = existing?.takeIf { !it.isRestored }?.capturedAt ?: System.currentTimeMillis(),
        isRestored = false,
      )
    )
  }

  suspend fun markBackupRestored(nodePath: String) {
    controlDao.markBackupRestored(nodePath)
  }

  fun observeRecentThermalHistory(limit: Int = 60): Flow<List<ThermalHistoryEntity>> =
    controlDao.observeRecentThermalHistory(limit)

  suspend fun recordThermalSample(sample: ThermalHistoryEntity) {
    controlDao.insertThermalSample(sample)
    controlDao.trimThermalHistory()
  }

  fun observeRecentRootLogs(limit: Int = 150): Flow<List<RootCommandLogEntity>> =
    controlDao.observeRecentRootLogs(limit)

  suspend fun recordRootLog(log: RootCommandLogEntity) {
    controlDao.insertRootLog(log)
    controlDao.trimRootLogs()
  }

  suspend fun clearRootLogs() {
    controlDao.clearRootLogs()
  }
}
