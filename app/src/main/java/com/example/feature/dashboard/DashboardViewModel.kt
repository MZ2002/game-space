package com.example.feature.dashboard

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.monitoring.SystemMonitorEngine
import com.dlssgamespace.core.monitoring.UnifiedMonitorSnapshot
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.EmergencyRestoreManager
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.core.system.PerformanceProfileOrchestrator
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.database.model.LibrarySummary
import com.dlssgamespace.database.repository.GameLibraryRepository
import com.dlssgamespace.gamelibrary.icons.AppIconCache
import com.dlssgamespace.gamelibrary.launcher.GameLauncher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardLibraryState(
  val summary: LibrarySummary = LibrarySummary(),
  val recentlyPlayed: List<GameEntity> = emptyList(),
  val favoriteGames: List<GameEntity> = emptyList(),
)

class DashboardViewModel(
  private val monitorEngine: SystemMonitorEngine,
  private val rootEngine: RootEngine,
  private val preferencesStore: AppPreferencesStore,
  private val profileOrchestrator: PerformanceProfileOrchestrator,
  private val emergencyRestoreManager: EmergencyRestoreManager,
  gameRepository: GameLibraryRepository,
  private val iconCache: AppIconCache,
  private val gameLauncher: GameLauncher,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  // Polls hardware telemetry ONLY while the Dashboard screen is visible
  val monitorSnapshot: StateFlow<UnifiedMonitorSnapshot?> =
    monitorEngine
      .observeMonitor(recordThermalHistory = false)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  val rootInfo: StateFlow<RootEnvironmentInfo> = rootEngine.environment

  val preferences: StateFlow<GameSpacePreferences> =
    preferencesStore.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = GameSpacePreferences(),
    )

  val libraryState: StateFlow<DashboardLibraryState> =
    combine(
        gameRepository.observeLibrarySummary(),
        gameRepository.observeRecentlyPlayed(limit = 6),
        gameRepository.observeFavoriteGames(),
      ) { summary, recent, favs ->
        DashboardLibraryState(summary = summary, recentlyPlayed = recent, favoriteGames = favs)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardLibraryState(),
      )

  init {
    viewModelScope.launch { rootEngine.detectEnvironment(requestRootPrompt = false) }
  }

  fun verifyRootAccess() {
    viewModelScope.launch {
      val info = rootEngine.detectEnvironment(requestRootPrompt = true)
      _messages.tryEmit(
        if (info.isRootGranted) "Root granted via ${info.managerType.displayName}"
        else "Root unavailable (${info.accessState.label}) — running in Limited Mode"
      )
    }
  }

  fun applyQuickProfile(profileId: String) {
    viewModelScope.launch {
      val report = profileOrchestrator.applyProfile(profileId)
      _messages.tryEmit(report.message)
    }
  }

  fun triggerEmergencyRestore() {
    viewModelScope.launch {
      val report = emergencyRestoreManager.restoreAllToOriginal()
      _messages.tryEmit(
        "Emergency Restore: ${report.restoredCount}/${report.totalBackups} node(s) restored"
      )
    }
  }

  fun launchGame(packageName: String, displayName: String) {
    gameLauncher.launchGame(packageName).onFailure { err ->
      _messages.tryEmit(err.message ?: "Unable to launch $displayName")
    }
  }

  suspend fun loadAppIcon(packageName: String): Bitmap? = iconCache.loadIcon(packageName)

  class Factory(
    private val monitorEngine: SystemMonitorEngine,
    private val rootEngine: RootEngine,
    private val preferencesStore: AppPreferencesStore,
    private val profileOrchestrator: PerformanceProfileOrchestrator,
    private val emergencyRestoreManager: EmergencyRestoreManager,
    private val gameRepository: GameLibraryRepository,
    private val iconCache: AppIconCache,
    private val gameLauncher: GameLauncher,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DashboardViewModel(
        monitorEngine,
        rootEngine,
        preferencesStore,
        profileOrchestrator,
        emergencyRestoreManager,
        gameRepository,
        iconCache,
        gameLauncher,
      )
        as T
    }
  }
}
