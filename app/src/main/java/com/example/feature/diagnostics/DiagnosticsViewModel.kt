package com.example.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.MemoryManager
import com.dlssgamespace.core.hardware.StorageIoManager
import com.dlssgamespace.core.monitoring.ThermalEngineController
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticProbeItem(
  val subsystem: String,
  val status: String,
  val detail: String,
)

class DiagnosticsViewModel(
  private val rootEngine: RootEngine,
  private val cpuController: CpuController,
  private val gpuController: GpuController,
  private val memoryManager: MemoryManager,
  private val storageIoManager: StorageIoManager,
  private val thermalController: ThermalEngineController,
  private val controlRepository: SystemControlRepository,
) : ViewModel() {

  private val _probes = MutableStateFlow<List<DiagnosticProbeItem>>(emptyList())
  val probes: StateFlow<List<DiagnosticProbeItem>> = _probes.asStateFlow()

  val logs: StateFlow<List<RootCommandLogEntity>> =
    controlRepository.observeRecentRootLogs(limit = 150).stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = emptyList(),
    )

  init {
    runCompatibilityProbe()
  }

  fun runCompatibilityProbe() {
    viewModelScope.launch {
      val root = rootEngine.environment.value
      val cpu = cpuController.readSnapshot()
      val gpu = gpuController.readSnapshot()
      val mem = memoryManager.readSnapshot()
      val storage = storageIoManager.readSnapshot()
      val thermal =
        thermalController.readAndEvaluate(
          engineEnabled = false,
          mode = ThermalMode.BALANCED,
          recordToHistory = false,
        )

      _probes.value =
        listOf(
          DiagnosticProbeItem(
            subsystem = "Root Engine (${root.managerType.displayName})",
            status = if (root.isRootGranted) "PASS" else "LIMITED",
            detail = "Access: ${root.accessState.label} · BusyBox: ${root.busyBoxPath}",
          ),
          DiagnosticProbeItem(
            subsystem = "CPU Sysfs Clusters",
            status = if (cpu.clusters.isNotEmpty()) "PASS" else "UNSUPPORTED",
            detail = "${cpu.clusters.size} cluster(s), ${cpu.coreCount} core(s)",
          ),
          DiagnosticProbeItem(
            subsystem = "GPU Controller",
            status = if (gpu.isSupported) "PASS" else "UNSUPPORTED",
            detail = "${gpu.gpuModel} (${gpu.sysfsRootPath})",
          ),
          DiagnosticProbeItem(
            subsystem = "Thermal Zones",
            status = if (thermal.zones.isNotEmpty()) "PASS" else "UNSUPPORTED",
            detail = "${thermal.zones.size} readable kernel zone(s)",
          ),
          DiagnosticProbeItem(
            subsystem = "zRAM & Memory",
            status = if (mem.zramDiskSizeBytes != null) "PASS" else "UNSUPPORTED",
            detail = "Alg: ${mem.zramAlgorithm} · Swappiness: ${mem.swappiness ?: "N/A"}",
          ),
          DiagnosticProbeItem(
            subsystem = "Storage I/O Queue",
            status = if (storage.primaryBlockIo != null) "PASS" else "UNSUPPORTED",
            detail =
              storage.primaryBlockIo?.let { "${it.deviceName} (${it.activeScheduler})" }
                ?: "No writable block queue exposed",
          ),
        )
    }
  }

  fun clearLogs() {
    viewModelScope.launch { controlRepository.clearRootLogs() }
  }

  class Factory(
    private val rootEngine: RootEngine,
    private val cpuController: CpuController,
    private val gpuController: GpuController,
    private val memoryManager: MemoryManager,
    private val storageIoManager: StorageIoManager,
    private val thermalController: ThermalEngineController,
    private val controlRepository: SystemControlRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DiagnosticsViewModel(
        rootEngine,
        cpuController,
        gpuController,
        memoryManager,
        storageIoManager,
        thermalController,
        controlRepository,
      )
        as T
    }
  }
}
