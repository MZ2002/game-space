package com.dlssgamespace.core.monitoring

import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.CpuSnapshot
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.GpuSnapshot
import com.dlssgamespace.core.hardware.MemoryManager
import com.dlssgamespace.core.hardware.MemoryManagerSnapshot
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.telemetry.DeviceTelemetry
import com.dlssgamespace.core.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

data class UnifiedMonitorSnapshot(
  val deviceTelemetry: DeviceTelemetry,
  val cpu: CpuSnapshot,
  val gpu: GpuSnapshot,
  val memory: MemoryManagerSnapshot,
  val thermal: ThermalSnapshot,
  val timestampMs: Long,
)

class SystemMonitorEngine(
  private val deviceTelemetryProvider: DeviceTelemetryProvider,
  private val cpuController: CpuController,
  private val gpuController: GpuController,
  private val memoryManager: MemoryManager,
  private val thermalEngineController: ThermalEngineController,
  private val preferencesStore: AppPreferencesStore,
) {

  fun observeMonitor(recordThermalHistory: Boolean = false): Flow<UnifiedMonitorSnapshot> =
    flow {
        while (currentCoroutineContext().isActive) {
          val prefs = preferencesStore.preferences.first()
          val thermalMode = ThermalMode.fromId(prefs.thermalMode)

          val snapshot =
            UnifiedMonitorSnapshot(
              deviceTelemetry = deviceTelemetryProvider.readSnapshot(),
              cpu = cpuController.readSnapshot(),
              gpu = gpuController.readSnapshot(),
              memory = memoryManager.readSnapshot(),
              thermal =
                thermalEngineController.readAndEvaluate(
                  engineEnabled = prefs.thermalEngineEnabled,
                  mode = thermalMode,
                  recordToHistory = recordThermalHistory,
                ),
              timestampMs = System.currentTimeMillis(),
            )
          emit(snapshot)
          delay(prefs.pollingIntervalMs.coerceIn(1_000L, 10_000L))
        }
      }
      .flowOn(Dispatchers.IO)
}
