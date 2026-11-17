package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dlssgamespace.gamelibrary.ui.GameLibraryScreen
import com.dlssgamespace.gamelibrary.ui.GameLibraryViewModel
import com.example.BuildConfig
import com.example.di.AppContainer
import com.example.feature.cpu.CpuControllerScreen
import com.example.feature.cpu.CpuViewModel
import com.example.feature.dashboard.ControlHubScreen
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.dashboard.DashboardViewModel
import com.example.feature.diagnostics.DiagnosticsScreen
import com.example.feature.diagnostics.DiagnosticsViewModel
import com.example.feature.diagnostics.EmergencyRestoreScreen
import com.example.feature.diagnostics.EmergencyRestoreViewModel
import com.example.feature.games.GameDetailsScreen
import com.example.feature.games.GameDetailsViewModel
import com.example.feature.gpu.GpuControllerScreen
import com.example.feature.gpu.GpuViewModel
import com.example.feature.memory.MemoryManagerScreen
import com.example.feature.memory.MemoryViewModel
import com.example.feature.monitoring.SystemMonitorScreen
import com.example.feature.monitoring.SystemMonitorViewModel
import com.example.feature.overlay.OverlaySettingsScreen
import com.example.feature.overlay.OverlayViewModel
import com.example.feature.profiles.ProfilesScreen
import com.example.feature.profiles.ProfilesViewModel
import com.example.feature.root.RootManagerScreen
import com.example.feature.root.RootViewModel
import com.example.feature.settings.AboutScreen
import com.example.feature.settings.AdvancedSettingsScreen
import com.example.feature.settings.AdvancedSettingsViewModel
import com.example.feature.thermal.ThermalEngineScreen
import com.example.feature.thermal.ThermalViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface GameSpaceRoute {
  @Serializable data object Home : GameSpaceRoute

  @Serializable data object Games : GameSpaceRoute

  @Serializable data class GameDetails(val packageName: String) : GameSpaceRoute

  @Serializable data object ControlHub : GameSpaceRoute

  @Serializable data object Cpu : GameSpaceRoute

  @Serializable data object Gpu : GameSpaceRoute

  @Serializable data object Thermal : GameSpaceRoute

  @Serializable data object Memory : GameSpaceRoute

  @Serializable data object Monitor : GameSpaceRoute

  @Serializable data object Profiles : GameSpaceRoute

  @Serializable data object Overlay : GameSpaceRoute

  @Serializable data object Root : GameSpaceRoute

  @Serializable data object Diagnostics : GameSpaceRoute

  @Serializable data object EmergencyRestore : GameSpaceRoute

  @Serializable data object Settings : GameSpaceRoute

  @Serializable data object About : GameSpaceRoute
}

private data class TopLevelDestination(
  val route: GameSpaceRoute,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val topLevelDestinations =
  listOf(
    TopLevelDestination(
      route = GameSpaceRoute.Home,
      label = "Home",
      selectedIcon = Icons.Rounded.Home,
      unselectedIcon = Icons.Outlined.Home,
      testTag = "nav_home",
    ),
    TopLevelDestination(
      route = GameSpaceRoute.Games,
      label = "Games",
      selectedIcon = Icons.Rounded.SportsEsports,
      unselectedIcon = Icons.Outlined.SportsEsports,
      testTag = "nav_games",
    ),
    TopLevelDestination(
      route = GameSpaceRoute.ControlHub,
      label = "Control",
      selectedIcon = Icons.Rounded.Tune,
      unselectedIcon = Icons.Outlined.Tune,
      testTag = "nav_control",
    ),
    TopLevelDestination(
      route = GameSpaceRoute.Root,
      label = "Root",
      selectedIcon = Icons.Rounded.Security,
      unselectedIcon = Icons.Outlined.Security,
      testTag = "nav_root",
    ),
    TopLevelDestination(
      route = GameSpaceRoute.Settings,
      label = "Settings",
      selectedIcon = Icons.Rounded.Settings,
      unselectedIcon = Icons.Outlined.Settings,
      testTag = "nav_settings",
    ),
  )

@Composable
fun DlssGameSpaceApp(container: AppContainer) {
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val appContext = LocalContext.current.applicationContext

  val gameLibraryViewModel: GameLibraryViewModel =
    viewModel(
      factory =
        GameLibraryViewModel.Factory(
          repository = container.gameLibraryRepository,
          scanner = container.installedAppsScanner,
          iconCache = container.appIconCache,
          gameLauncher = container.gameLauncher,
        )
    )

  LaunchedEffect(gameLibraryViewModel) {
    gameLibraryViewModel.snackbarEvents.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  fun navigateToTab(route: GameSpaceRoute) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.navigationBarsPadding(),
          ) {
            topLevelDestinations.forEach { dest ->
              val selected =
                currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label,
                  )
                },
                label = {
                  Text(text = dest.label, style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.testTag(dest.testTag),
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ) {
            topLevelDestinations.forEach { dest ->
              val selected =
                currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(dest.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = dest.label,
                  )
                },
                label = {
                  Text(text = dest.label, style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.testTag(dest.testTag),
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = GameSpaceRoute.Home,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          // 1. Home Dashboard
          composable<GameSpaceRoute.Home> {
            val vm: DashboardViewModel =
              viewModel(
                factory =
                  DashboardViewModel.Factory(
                    monitorEngine = container.systemMonitorEngine,
                    rootEngine = container.rootEngine,
                    preferencesStore = container.preferencesStore,
                    profileOrchestrator = container.profileOrchestrator,
                    emergencyRestoreManager = container.emergencyRestoreManager,
                    gameRepository = container.gameLibraryRepository,
                    iconCache = container.appIconCache,
                    gameLauncher = container.gameLauncher,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val snapshot by
              vm.monitorSnapshot.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val rootInfo by vm.rootInfo.collectAsStateWithLifecycle()
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            val libraryState by vm.libraryState.collectAsStateWithLifecycle()

            DashboardScreen(
              snapshot = snapshot,
              rootInfo = rootInfo,
              preferences = prefs,
              libraryState = libraryState,
              appVersionName = BuildConfig.VERSION_NAME,
              onVerifyRoot = vm::verifyRootAccess,
              onApplyProfile = vm::applyQuickProfile,
              onEmergencyRestore = vm::triggerEmergencyRestore,
              onOpenGames = { navigateToTab(GameSpaceRoute.Games) },
              onOpenGameDetails = { pkg -> navController.navigate(GameSpaceRoute.GameDetails(pkg)) },
              onLaunchGame = { game -> vm.launchGame(game.packageName, game.displayName) },
              onOpenCpu = { navController.navigate(GameSpaceRoute.Cpu) },
              onOpenGpu = { navController.navigate(GameSpaceRoute.Gpu) },
              onOpenThermal = { navController.navigate(GameSpaceRoute.Thermal) },
              onOpenMemory = { navController.navigate(GameSpaceRoute.Memory) },
              onOpenRoot = { navigateToTab(GameSpaceRoute.Root) },
              loadIcon = { pkg -> vm.loadAppIcon(pkg) },
            )
          }

          // 2. Game Library
          composable<GameSpaceRoute.Games> {
            val libraryUiState by gameLibraryViewModel.uiState.collectAsStateWithLifecycle()

            GameLibraryScreen(
              uiState = libraryUiState,
              onSearchQueryChanged = gameLibraryViewModel::onSearchQueryChanged,
              onSortOptionSelected = gameLibraryViewModel::onSortOptionSelected,
              onFavoritesOnlyToggled = gameLibraryViewModel::onFavoritesOnlyToggled,
              onViewModeToggled = gameLibraryViewModel::onViewModeToggled,
              onRescanClicked = { gameLibraryViewModel.rescanInstalledApps(showFeedback = true) },
              onAddGameClicked = gameLibraryViewModel::openAddGameSheet,
              onOpenGameDetails = { game ->
                navController.navigate(GameSpaceRoute.GameDetails(game.packageName))
              },
              onLaunchGame = { game ->
                gameLibraryViewModel.launchGame(game.packageName, game.displayName)
              },
              onToggleFavorite = gameLibraryViewModel::toggleFavorite,
              onRequestRemove = gameLibraryViewModel::requestRemoveFromLibrary,
              onConfirmRemove = gameLibraryViewModel::confirmRemoveFromLibrary,
              onCancelRemove = gameLibraryViewModel::cancelRemoveFromLibrary,
              onManualSearchChanged = gameLibraryViewModel::onManualSearchQueryChanged,
              onAddManualCandidate = gameLibraryViewModel::addGameManually,
              onDismissAddSheet = gameLibraryViewModel::dismissAddGameSheet,
              loadIcon = { pkg -> gameLibraryViewModel.loadAppIcon(pkg) },
            )
          }

          // 3. Game Details & Per-Game Profile
          composable<GameSpaceRoute.GameDetails> { entry ->
            val args = entry.toRoute<GameSpaceRoute.GameDetails>()
            val vm: GameDetailsViewModel =
              viewModel(
                factory =
                  GameDetailsViewModel.Factory(
                    packageName = args.packageName,
                    context = appContext,
                    gameRepository = container.gameLibraryRepository,
                    controlRepository = container.systemControlRepository,
                    displayTouchManager = container.displayTouchManager,
                    profileOrchestrator = container.profileOrchestrator,
                    iconCache = container.appIconCache,
                    gameLauncher = container.gameLauncher,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val uiState by vm.uiState.collectAsStateWithLifecycle()

            GameDetailsScreen(
              uiState = uiState,
              onBack = { navController.popBackStack() },
              onLaunch = vm::launchWithProfile,
              onToggleFavorite = vm::toggleFavorite,
              onSelectPerformanceProfile = { vm.updateProfile(performanceProfile = it) },
              onSelectThermalMode = { vm.updateProfile(thermalMode = it) },
              onSelectRefreshRate = { vm.updateProfile(preferredRefreshRateHz = it) },
              onToggleAutoApply = { vm.updateProfile(autoApplyOnLaunch = it) },
              onToggleAutoOverlay = { vm.updateProfile(autoShowOverlay = it) },
              onToggleBoostPriority = { vm.updateProfile(boostPriority = it) },
              loadIcon = { vm.loadIcon() },
            )
          }

          // 4. Control Hub
          composable<GameSpaceRoute.ControlHub> {
            val rootInfo by container.rootEngine.environment.collectAsStateWithLifecycle()

            ControlHubScreen(
              rootInfo = rootInfo,
              onOpenCpu = { navController.navigate(GameSpaceRoute.Cpu) },
              onOpenGpu = { navController.navigate(GameSpaceRoute.Gpu) },
              onOpenThermal = { navController.navigate(GameSpaceRoute.Thermal) },
              onOpenMemory = { navController.navigate(GameSpaceRoute.Memory) },
              onOpenMonitor = { navController.navigate(GameSpaceRoute.Monitor) },
              onOpenProfiles = { navController.navigate(GameSpaceRoute.Profiles) },
              onOpenOverlay = { navController.navigate(GameSpaceRoute.Overlay) },
            )
          }

          // 5. CPU Controller
          composable<GameSpaceRoute.Cpu> {
            val vm: CpuViewModel =
              viewModel(
                factory =
                  CpuViewModel.Factory(
                    cpuController = container.cpuController,
                    rootEngine = container.rootEngine,
                    preferencesStore = container.preferencesStore,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val snapshot by vm.snapshot.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val rootInfo by vm.rootInfo.collectAsStateWithLifecycle()

            CpuControllerScreen(
              snapshot = snapshot,
              rootInfo = rootInfo,
              onBack = { navController.popBackStack() },
              onApplyPreset = vm::applyPreset,
              onSelectGovernor = vm::setGovernor,
              onSelectMinFreq = vm::setMinFreq,
              onSelectMaxFreq = vm::setMaxFreq,
            )
          }

          // 6. GPU Controller
          composable<GameSpaceRoute.Gpu> {
            val vm: GpuViewModel =
              viewModel(
                factory =
                  GpuViewModel.Factory(
                    gpuController = container.gpuController,
                    rootEngine = container.rootEngine,
                    preferencesStore = container.preferencesStore,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val snapshot by vm.snapshot.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val rootInfo by vm.rootInfo.collectAsStateWithLifecycle()

            GpuControllerScreen(
              snapshot = snapshot,
              rootInfo = rootInfo,
              onBack = { navController.popBackStack() },
              onApplyPreset = vm::applyPreset,
              onSelectGovernor = vm::setGovernor,
              onSelectMinClock = vm::setMinClock,
              onSelectMaxClock = vm::setMaxClock,
            )
          }

          // 7. AI Thermal Engine
          composable<GameSpaceRoute.Thermal> {
            val vm: ThermalViewModel =
              viewModel(
                factory =
                  ThermalViewModel.Factory(
                    thermalController = container.thermalEngineController,
                    controlRepository = container.systemControlRepository,
                    preferencesStore = container.preferencesStore,
                    emergencyRestoreManager = container.emergencyRestoreManager,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val snapshot by vm.snapshot.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val history by vm.thermalHistory.collectAsStateWithLifecycle()
            val prefs by vm.preferences.collectAsStateWithLifecycle()

            ThermalEngineScreen(
              snapshot = snapshot,
              history = history,
              preferences = prefs,
              onBack = { navController.popBackStack() },
              onToggleThermalEngine = vm::setThermalEngineEnabled,
              onSelectThermalMode = vm::selectThermalMode,
            )
          }

          // 8. RAM & Memory Manager
          composable<GameSpaceRoute.Memory> {
            val vm: MemoryViewModel =
              viewModel(
                factory =
                  MemoryViewModel.Factory(
                    memoryManager = container.memoryManager,
                    rootEngine = container.rootEngine,
                    preferencesStore = container.preferencesStore,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val snapshot by vm.snapshot.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val rootInfo by vm.rootInfo.collectAsStateWithLifecycle()

            MemoryManagerScreen(
              snapshot = snapshot,
              rootInfo = rootInfo,
              onBack = { navController.popBackStack() },
              onSetSwappiness = vm::setSwappiness,
              onTrimPageCache = vm::trimPageCache,
              onBoostProcess = vm::boostProcessPriority,
            )
          }

          // 9. System Monitor & FPS
          composable<GameSpaceRoute.Monitor> {
            val vm: SystemMonitorViewModel =
              viewModel(
                factory =
                  SystemMonitorViewModel.Factory(
                    monitorEngine = container.systemMonitorEngine,
                    fpsMonitor = container.fpsMonitor,
                    preferencesStore = container.preferencesStore,
                  )
              )

            val snapshot by vm.snapshot.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val localFps by vm.localFps.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED)
            val buffers by vm.buffers.collectAsStateWithLifecycle()
            val prefs by vm.preferences.collectAsStateWithLifecycle()

            SystemMonitorScreen(
              snapshot = snapshot,
              localFps = localFps,
              buffers = buffers,
              preferences = prefs,
              onBack = { navController.popBackStack() },
              onSelectInterval = vm::setPollingInterval,
            )
          }

          // 10. Performance Profiles
          composable<GameSpaceRoute.Profiles> {
            val vm: ProfilesViewModel =
              viewModel(
                factory =
                  ProfilesViewModel.Factory(
                    controlRepository = container.systemControlRepository,
                    preferencesStore = container.preferencesStore,
                    orchestrator = container.profileOrchestrator,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val profiles by vm.profiles.collectAsStateWithLifecycle()
            val prefs by vm.preferences.collectAsStateWithLifecycle()

            ProfilesScreen(
              profiles = profiles,
              preferences = prefs,
              onBack = { navController.popBackStack() },
              onUpdateProfile = vm::updateProfile,
              onApplyProfile = vm::applyProfile,
            )
          }

          // 11. Floating Overlay Settings
          composable<GameSpaceRoute.Overlay> {
            val vm: OverlayViewModel =
              viewModel(
                factory =
                  OverlayViewModel.Factory(
                    context = appContext,
                    preferencesStore = container.preferencesStore,
                  )
              )

            val prefs by vm.preferences.collectAsStateWithLifecycle()
            val running by vm.isOverlayRunning.collectAsStateWithLifecycle()

            OverlaySettingsScreen(
              preferences = prefs,
              isOverlayRunning = running,
              onBack = { navController.popBackStack() },
              onToggleOverlay = vm::toggleOverlayService,
              onUpdatePrefs = vm::updatePrefs,
            )
          }

          // 12. Root Manager
          composable<GameSpaceRoute.Root> {
            val vm: RootViewModel =
              viewModel(
                factory =
                  RootViewModel.Factory(
                    rootEngine = container.rootEngine,
                    controlRepository = container.systemControlRepository,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val rootInfo by vm.rootInfo.collectAsStateWithLifecycle()
            val recentLogs by vm.recentLogs.collectAsStateWithLifecycle()

            RootManagerScreen(
              rootInfo = rootInfo,
              recentLogs = recentLogs,
              onRequestRoot = { vm.refreshDetection(requestPrompt = true) },
              onRescanNonInteractive = { vm.refreshDetection(requestPrompt = false) },
              onOpenEmergencyRestore = { navController.navigate(GameSpaceRoute.EmergencyRestore) },
              onOpenDiagnostics = { navController.navigate(GameSpaceRoute.Diagnostics) },
            )
          }

          // 13. Logs & Diagnostics
          composable<GameSpaceRoute.Diagnostics> {
            val vm: DiagnosticsViewModel =
              viewModel(
                factory =
                  DiagnosticsViewModel.Factory(
                    rootEngine = container.rootEngine,
                    cpuController = container.cpuController,
                    gpuController = container.gpuController,
                    memoryManager = container.memoryManager,
                    storageIoManager = container.storageIoManager,
                    thermalController = container.thermalEngineController,
                    controlRepository = container.systemControlRepository,
                  )
              )

            val probes by vm.probes.collectAsStateWithLifecycle()
            val logs by vm.logs.collectAsStateWithLifecycle()

            DiagnosticsScreen(
              probes = probes,
              logs = logs,
              onBack = { navController.popBackStack() },
              onRunProbe = vm::runCompatibilityProbe,
              onClearLogs = vm::clearLogs,
            )
          }

          // 14. Emergency Restore
          composable<GameSpaceRoute.EmergencyRestore> {
            val vm: EmergencyRestoreViewModel =
              viewModel(
                factory =
                  EmergencyRestoreViewModel.Factory(
                    controlRepository = container.systemControlRepository,
                    preferencesStore = container.preferencesStore,
                    restoreManager = container.emergencyRestoreManager,
                  )
              )

            val backups by vm.activeBackups.collectAsStateWithLifecycle()
            val prefs by vm.preferences.collectAsStateWithLifecycle()
            val report by vm.lastReport.collectAsStateWithLifecycle()

            EmergencyRestoreScreen(
              activeBackups = backups,
              preferences = prefs,
              lastReport = report,
              onBack = { navController.popBackStack() },
              onExecuteRestore = vm::executeEmergencyRestore,
            )
          }

          // 15. Advanced Settings
          composable<GameSpaceRoute.Settings> {
            val vm: AdvancedSettingsViewModel =
              viewModel(
                factory =
                  AdvancedSettingsViewModel.Factory(
                    context = appContext,
                    preferencesStore = container.preferencesStore,
                    storageIoManager = container.storageIoManager,
                    displayTouchManager = container.displayTouchManager,
                    rootEngine = container.rootEngine,
                  )
              )

            LaunchedEffect(vm) {
              vm.messages.collect { snackbarHostState.showSnackbar(it) }
            }

            val prefs by vm.preferences.collectAsStateWithLifecycle()
            val storageIo by vm.storageIo.collectAsStateWithLifecycle()
            val displayTouch by vm.displayTouch.collectAsStateWithLifecycle()
            val rootInfo by vm.rootInfo.collectAsStateWithLifecycle()
            val autoRunning by vm.isAutoMonitorRunning.collectAsStateWithLifecycle()

            AdvancedSettingsScreen(
              preferences = prefs,
              storageIo = storageIo,
              displayTouch = displayTouch,
              rootInfo = rootInfo,
              isAutoMonitorRunning = autoRunning,
              onSelectPollingInterval = vm::setPollingInterval,
              onSelectPreferredRefreshRate = vm::setPreferredRefreshRate,
              onToggleAutoGameDetection = vm::setAutoGameDetection,
              onSelectIoScheduler = vm::setIoScheduler,
              onSelectReadAhead = vm::setReadAheadKb,
              onOpenAbout = { navController.navigate(GameSpaceRoute.About) },
            )
          }

          // 16. About Game Space
          composable<GameSpaceRoute.About> {
            val rootInfo by container.rootEngine.environment.collectAsStateWithLifecycle()

            AboutScreen(
              appVersionName = BuildConfig.VERSION_NAME,
              rootInfo = rootInfo,
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}
