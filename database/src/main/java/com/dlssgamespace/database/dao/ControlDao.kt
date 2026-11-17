package com.dlssgamespace.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dlssgamespace.database.entity.CustomPerformanceProfileEntity
import com.dlssgamespace.database.entity.GameProfileEntity
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.database.entity.SystemBackupEntity
import com.dlssgamespace.database.entity.ThermalHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ControlDao {
  // Per-game profiles
  @Query("SELECT * FROM game_profiles WHERE packageName = :packageName LIMIT 1")
  fun observeGameProfile(packageName: String): Flow<GameProfileEntity?>

  @Query("SELECT * FROM game_profiles WHERE packageName = :packageName LIMIT 1")
  suspend fun getGameProfileOnce(packageName: String): GameProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertGameProfile(profile: GameProfileEntity)

  // Custom Performance Profiles
  @Query("SELECT * FROM custom_performance_profiles")
  fun observeCustomProfiles(): Flow<List<CustomPerformanceProfileEntity>>

  @Query("SELECT * FROM custom_performance_profiles WHERE profileId = :profileId LIMIT 1")
  suspend fun getCustomProfileOnce(profileId: String): CustomPerformanceProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomProfile(profile: CustomPerformanceProfileEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertDefaultProfilesIgnore(profiles: List<CustomPerformanceProfileEntity>)

  // System Backups (Emergency Restore)
  @Query("SELECT * FROM system_backups ORDER BY capturedAt ASC")
  fun observeBackups(): Flow<List<SystemBackupEntity>>

  @Query("SELECT * FROM system_backups WHERE isRestored = 0 ORDER BY capturedAt DESC")
  fun observeActiveBackups(): Flow<List<SystemBackupEntity>>

  @Query("SELECT * FROM system_backups WHERE isRestored = 0")
  suspend fun getActiveBackupsOnce(): List<SystemBackupEntity>

  @Query("SELECT * FROM system_backups WHERE nodePath = :nodePath LIMIT 1")
  suspend fun getBackupByPath(nodePath: String): SystemBackupEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBackup(backup: SystemBackupEntity)

  @Query("UPDATE system_backups SET isRestored = 1 WHERE nodePath = :nodePath")
  suspend fun markBackupRestored(nodePath: String)

  @Query("DELETE FROM system_backups WHERE isRestored = 1")
  suspend fun purgeRestoredBackups()

  // Thermal History
  @Query("SELECT * FROM thermal_history ORDER BY timestampMs DESC LIMIT :limit")
  fun observeRecentThermalHistory(limit: Int = 60): Flow<List<ThermalHistoryEntity>>

  @Insert
  suspend fun insertThermalSample(sample: ThermalHistoryEntity)

  @Query(
    "DELETE FROM thermal_history WHERE id NOT IN (SELECT id FROM thermal_history ORDER BY timestampMs DESC LIMIT 200)"
  )
  suspend fun trimThermalHistory()

  // Root Command Logs
  @Query("SELECT * FROM root_command_logs ORDER BY timestampMs DESC LIMIT :limit")
  fun observeRecentRootLogs(limit: Int = 150): Flow<List<RootCommandLogEntity>>

  @Insert
  suspend fun insertRootLog(log: RootCommandLogEntity)

  @Query("DELETE FROM root_command_logs")
  suspend fun clearRootLogs()

  @Query(
    "DELETE FROM root_command_logs WHERE id NOT IN (SELECT id FROM root_command_logs ORDER BY timestampMs DESC LIMIT 300)"
  )
  suspend fun trimRootLogs()
}
