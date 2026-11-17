package com.dlssgamespace.core.system

import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.CpuProfilePreset
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.GpuProfilePreset
import com.dlssgamespace.core.hardware.MemoryManager
import com.dlssgamespace.core.root.RootCommandResult
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.repository.SystemControlRepository

data class ProfileApplyReport(
  val profileId: String,
  val isRootApplied: Boolean,
  val succeededCount: Int,
  val failedCount: Int,
  val message: String,
)

class PerformanceProfileOrchestrator(
  private val rootEngine: RootEngine,
  private val cpuController: CpuController,
  private val gpuController: GpuController,
  private val memoryManager: MemoryManager,
  private val controlRepository: SystemControlRepository,
  private val preferencesStore: AppPreferencesStore,
) {

  suspend fun applyProfile(profileId: String): ProfileApplyReport {
    val config = controlRepository.getProfileConfig(profileId)
    preferencesStore.setActivePerformanceProfile(profileId)

    if (!rootEngine.environment.value.isRootGranted) {
      return ProfileApplyReport(
        profileId = profileId,
        isRootApplied = false,
        succeededCount = 0,
        failedCount = 0,
        message =
          "Profile '$profileId' saved in Limited Mode. Grant Root access to apply kernel CPU/GPU/Memory settings.",
      )
    }

    val cpuPreset =
      CpuProfilePreset.entries.firstOrNull { it.id == profileId } ?: CpuProfilePreset.BALANCED
    val gpuPreset =
      GpuProfilePreset.entries.firstOrNull { it.id == profileId } ?: GpuProfilePreset.BALANCED

    val results = mutableListOf<RootCommandResult>()
    results.addAll(
      cpuController.applyProfile(
        preset = cpuPreset,
        governorOverride = config?.cpuGovernorPreference,
        minPercentOverride = config?.cpuMinFreqPercent,
        maxPercentOverride = config?.cpuMaxFreqPercent,
      )
    )
    results.addAll(
      gpuController.applyProfile(
        preset = gpuPreset,
        biasPercentOverride = config?.gpuBiasPercent,
      )
    )
    if (config != null) {
      results.add(memoryManager.setSwappiness(config.swappinessTarget))
    }

    val succeeded = results.count { it.succeeded }
    val failed = results.count { !it.succeeded }
    if (succeeded > 0) {
      preferencesStore.setTweakSessionDirty(true)
    }

    if (config != null) {
      preferencesStore.setThermalMode(config.thermalTargetMode)
    }

    return ProfileApplyReport(
      profileId = profileId,
      isRootApplied = true,
      succeededCount = succeeded,
      failedCount = failed,
      message =
        if (failed == 0) {
          "Applied '$profileId' ($succeeded kernel parameter(s) verified)"
        } else {
          "Applied '$profileId': $succeeded verified, $failed unsupported/rejected by kernel"
        },
    )
  }
}
