package com.dlssgamespace.core.system

import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.repository.SystemControlRepository

data class EmergencyRestoreReport(
  val totalBackups: Int,
  val restoredCount: Int,
  val failedCount: Int,
  val details: List<String>,
)

class EmergencyRestoreManager(
  private val rootEngine: RootEngine,
  private val controlRepository: SystemControlRepository,
  private val preferencesStore: AppPreferencesStore,
) {

  suspend fun restoreAllToOriginal(): EmergencyRestoreReport {
    val activeBackups = controlRepository.getActiveBackupsOnce()
    if (activeBackups.isEmpty()) {
      preferencesStore.setTweakSessionDirty(false)
      preferencesStore.setThermalEngineEnabled(false)
      preferencesStore.setActivePerformanceProfile("BALANCED")
      return EmergencyRestoreReport(
        totalBackups = 0,
        restoredCount = 0,
        failedCount = 0,
        details = listOf("No modified kernel nodes pending restoration."),
      )
    }

    var restored = 0
    var failed = 0
    val details = mutableListOf<String>()

    // Restore CPU max frequencies before min frequencies if multiple nodes exist,
    // by sorting max_freq first then min_freq/governors/others.
    val ordered =
      activeBackups.sortedBy {
        when {
          it.nodePath.contains("max_freq") || it.nodePath.contains("max_clock") -> 0
          it.nodePath.contains("min_freq") || it.nodePath.contains("min_clock") -> 1
          else -> 2
        }
      }

    for (backup in ordered) {
      val res =
        rootEngine.writeSysfsNode(
          path = backup.nodePath,
          value = backup.originalValue,
          category = "EMERGENCY_RESTORE_${backup.category}",
        )
      if (res.succeeded) {
        controlRepository.markBackupRestored(backup.nodePath)
        restored++
        details.add("RESTORED ${backup.nodePath} -> ${backup.originalValue}")
      } else {
        failed++
        details.add("FAILED ${backup.nodePath}: ${res.stderr}")
      }
    }

    preferencesStore.setThermalEngineEnabled(false)
    preferencesStore.setActivePerformanceProfile("BALANCED")
    if (failed == 0) {
      preferencesStore.setTweakSessionDirty(false)
    }

    return EmergencyRestoreReport(
      totalBackups = activeBackups.size,
      restoredCount = restored,
      failedCount = failed,
      details = details,
    )
  }
}
