package com.example.di

import android.content.Context
import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.DisplayTouchManager
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.MemoryManager
import com.dlssgamespace.core.hardware.StorageIoManager
import com.dlssgamespace.core.monitoring.FpsMonitor
import com.dlssgamespace.core.monitoring.SystemMonitorEngine
import com.dlssgamespace.core.monitoring.ThermalEngineController
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.EmergencyRestoreManager
import com.dlssgamespace.core.system.PerformanceProfileOrchestrator
import com.dlssgamespace.core.telemetry.DeviceTelemetryProvider
import com.dlssgamespace.database.DlssDatabase
import com.dlssgamespace.database.repository.GameLibraryRepository
import com.dlssgamespace.database.repository.SystemControlRepository
import com.dlssgamespace.gamelibrary.icons.AppIconCache
import com.dlssgamespace.gamelibrary.launcher.GameLauncher
import com.dlssgamespace.gamelibrary.launcher.GameSessionTracker
import com.dlssgamespace.gamelibrary.scanner.InstalledAppsScanner

class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val database: DlssDatabase by lazy { DlssDatabase.getInstance(appContext) }

  val gameLibraryRepository: GameLibraryRepository by lazy { GameLibraryRepository(database) }

  val systemControlRepository: SystemControlRepository by lazy {
    SystemControlRepository(database)
  }

  val preferencesStore: AppPreferencesStore by lazy { AppPreferencesStore(appContext) }

  val rootEngine: RootEngine by lazy { RootEngine(systemControlRepository) }

  val telemetryProvider: DeviceTelemetryProvider by lazy { DeviceTelemetryProvider(appContext) }

  val cpuController: CpuController by lazy { CpuController(rootEngine, systemControlRepository) }

  val gpuController: GpuController by lazy {
    GpuController(appContext, rootEngine, systemControlRepository)
  }

  val memoryManager: MemoryManager by lazy {
    MemoryManager(appContext, rootEngine, systemControlRepository)
  }

  val storageIoManager: StorageIoManager by lazy {
    StorageIoManager(rootEngine, systemControlRepository)
  }

  val displayTouchManager: DisplayTouchManager by lazy {
    DisplayTouchManager(appContext, rootEngine)
  }

  val thermalEngineController: ThermalEngineController by lazy {
    ThermalEngineController(
      context = appContext,
      rootEngine = rootEngine,
      controlRepository = systemControlRepository,
      cpuController = cpuController,
      gpuController = gpuController,
    )
  }

  val fpsMonitor: FpsMonitor by lazy { FpsMonitor(rootEngine) }

  val systemMonitorEngine: SystemMonitorEngine by lazy {
    SystemMonitorEngine(
      deviceTelemetryProvider = telemetryProvider,
      cpuController = cpuController,
      gpuController = gpuController,
      memoryManager = memoryManager,
      thermalEngineController = thermalEngineController,
      preferencesStore = preferencesStore,
    )
  }

  val profileOrchestrator: PerformanceProfileOrchestrator by lazy {
    PerformanceProfileOrchestrator(
      rootEngine = rootEngine,
      cpuController = cpuController,
      gpuController = gpuController,
      memoryManager = memoryManager,
      controlRepository = systemControlRepository,
      preferencesStore = preferencesStore,
    )
  }

  val emergencyRestoreManager: EmergencyRestoreManager by lazy {
    EmergencyRestoreManager(
      rootEngine = rootEngine,
      controlRepository = systemControlRepository,
      preferencesStore = preferencesStore,
    )
  }

  val installedAppsScanner: InstalledAppsScanner by lazy { InstalledAppsScanner(appContext) }

  val appIconCache: AppIconCache by lazy { AppIconCache(appContext) }

  val sessionTracker: GameSessionTracker by lazy { GameSessionTracker(gameLibraryRepository) }

  val gameLauncher: GameLauncher by lazy { GameLauncher(appContext, sessionTracker) }
}
