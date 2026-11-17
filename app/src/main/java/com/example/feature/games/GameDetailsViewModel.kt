package com.example.feature.games

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.hardware.DisplayTouchManager
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.core.system.PerformanceProfileOrchestrator
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.database.entity.GameProfileEntity
import com.dlssgamespace.database.repository.GameLibraryRepository
import com.dlssgamespace.database.repository.SystemControlRepository
import com.dlssgamespace.gamelibrary.icons.AppIconCache
import com.dlssgamespace.gamelibrary.launcher.GameLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GameDetailsUiState(
  val game: GameEntity? = null,
  val profile: GameProfileEntity = GameProfileEntity(packageName = ""),
  val apkSizeBytes: Long? = null,
  val availableRefreshRates: List<Int> = emptyList(),
)

class GameDetailsViewModel(
  private val packageName: String,
  private val context: Context,
  private val gameRepository: GameLibraryRepository,
  private val controlRepository: SystemControlRepository,
  private val displayTouchManager: DisplayTouchManager,
  private val profileOrchestrator: PerformanceProfileOrchestrator,
  private val iconCache: AppIconCache,
  private val gameLauncher: GameLauncher,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private val apkSizeFlow = MutableStateFlow<Long?>(null)
  private val refreshRatesFlow = MutableStateFlow<List<Int>>(emptyList())

  val uiState: StateFlow<GameDetailsUiState> =
    combine(
        gameRepository.observeAllGames().map { list -> list.firstOrNull { it.packageName == packageName } },
        controlRepository.observeGameProfile(packageName),
        apkSizeFlow,
        refreshRatesFlow,
      ) { game, profile, apkSize, rates ->
        GameDetailsUiState(
          game = game,
          profile = profile ?: GameProfileEntity(packageName = packageName),
          apkSizeBytes = apkSize,
          availableRefreshRates = rates,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GameDetailsUiState(),
      )

  init {
    viewModelScope.launch {
      apkSizeFlow.value = readApkSizeBytes()
      refreshRatesFlow.value =
        displayTouchManager.readSnapshot().supportedModes.map { it.refreshRateHz }.distinct().sorted()
    }
  }

  fun updateProfile(
    performanceProfile: String? = null,
    thermalMode: String? = null,
    preferredRefreshRateHz: Int? = null,
    autoApplyOnLaunch: Boolean? = null,
    autoShowOverlay: Boolean? = null,
    boostPriority: Boolean? = null,
  ) {
    val current = uiState.value.profile
    val updated =
      current.copy(
        packageName = packageName,
        performanceProfile = performanceProfile ?: current.performanceProfile,
        thermalMode = thermalMode ?: current.thermalMode,
        preferredRefreshRateHz = preferredRefreshRateHz ?: current.preferredRefreshRateHz,
        autoApplyOnLaunch = autoApplyOnLaunch ?: current.autoApplyOnLaunch,
        autoShowOverlay = autoShowOverlay ?: current.autoShowOverlay,
        boostGameProcessPriority = boostPriority ?: current.boostGameProcessPriority,
        updatedAt = System.currentTimeMillis(),
      )
    viewModelScope.launch {
      controlRepository.saveGameProfile(updated)
    }
  }

  fun launchWithProfile() {
    val state = uiState.value
    viewModelScope.launch {
      if (state.profile.autoApplyOnLaunch) {
        val report = profileOrchestrator.applyProfile(state.profile.performanceProfile)
        _messages.tryEmit(report.message)
      }
      gameLauncher.launchGame(packageName).onFailure { err ->
        _messages.tryEmit(err.message ?: "Unable to launch $packageName")
      }
    }
  }

  fun toggleFavorite() {
    val game = uiState.value.game ?: return
    viewModelScope.launch { gameRepository.setFavorite(game.packageName, !game.isFavorite) }
  }

  suspend fun loadIcon(): Bitmap? = iconCache.loadIcon(packageName)

  private suspend fun readApkSizeBytes(): Long? =
    withContext(Dispatchers.IO) {
      runCatching {
          val pm = context.packageManager
          val appInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
              @Suppress("DEPRECATION") pm.getApplicationInfo(packageName, 0)
            }
          File(appInfo.sourceDir).length().takeIf { it > 0L }
        }
        .getOrNull()
    }

  class Factory(
    private val packageName: String,
    private val context: Context,
    private val gameRepository: GameLibraryRepository,
    private val controlRepository: SystemControlRepository,
    private val displayTouchManager: DisplayTouchManager,
    private val profileOrchestrator: PerformanceProfileOrchestrator,
    private val iconCache: AppIconCache,
    private val gameLauncher: GameLauncher,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return GameDetailsViewModel(
        packageName,
        context,
        gameRepository,
        controlRepository,
        displayTouchManager,
        profileOrchestrator,
        iconCache,
        gameLauncher,
      )
        as T
    }
  }
}
