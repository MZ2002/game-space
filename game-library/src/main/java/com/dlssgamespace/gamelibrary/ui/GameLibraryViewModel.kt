package com.dlssgamespace.gamelibrary.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.database.model.ScannedGameMetadata
import com.dlssgamespace.database.repository.GameLibraryRepository
import com.dlssgamespace.gamelibrary.icons.AppIconCache
import com.dlssgamespace.gamelibrary.launcher.GameLauncher
import com.dlssgamespace.gamelibrary.model.GameSortOption
import com.dlssgamespace.gamelibrary.model.GameViewMode
import com.dlssgamespace.gamelibrary.model.LaunchableAppCandidate
import com.dlssgamespace.gamelibrary.model.LibraryGameItem
import com.dlssgamespace.gamelibrary.scanner.InstalledAppsScanner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LibraryControlsState(
  val searchQuery: String = "",
  val sortOption: GameSortOption = GameSortOption.NAME,
  val favoritesOnly: Boolean = false,
  val viewMode: GameViewMode = GameViewMode.GRID,
  val isScanning: Boolean = false,
  val scanError: String? = null,
  val isAddSheetOpen: Boolean = false,
  val manualSearchQuery: String = "",
  val pendingRemoveGame: LibraryGameItem? = null,
)

class GameLibraryViewModel(
  private val repository: GameLibraryRepository,
  private val scanner: InstalledAppsScanner,
  private val iconCache: AppIconCache,
  private val gameLauncher: GameLauncher,
) : ViewModel() {

  private val controls = MutableStateFlow(LibraryControlsState())
  private val scannedLaunchableApps = MutableStateFlow<List<ScannedGameMetadata>>(emptyList())

  private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

  val uiState: StateFlow<GameLibraryUiState> =
    combine(
        repository.observeAllGames(),
        scannedLaunchableApps,
        controls,
      ) { allRoomGames, installedApps, ctrl ->
        val visibleEntities = allRoomGames.filter { !it.isHidden }
        val hiddenPackages = allRoomGames.filter { it.isHidden }.map { it.packageName }.toSet()
        val visiblePackages = visibleEntities.map { it.packageName }.toSet()

        val query = ctrl.searchQuery.trim()
        val filtered =
          visibleEntities
            .asSequence()
            .filter { entity ->
              if (ctrl.favoritesOnly && !entity.isFavorite) return@filter false
              if (query.isEmpty()) return@filter true
              entity.displayName.contains(query, ignoreCase = true) ||
                entity.packageName.contains(query, ignoreCase = true)
            }
            .map { it.toItem() }
            .toList()

        val sorted =
          when (ctrl.sortOption) {
            GameSortOption.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            GameSortOption.RECENT ->
              filtered.sortedWith(
                compareByDescending<LibraryGameItem> { it.lastPlayedAt ?: 0L }
                  .thenBy { it.displayName.lowercase() }
              )
            GameSortOption.FAVORITE ->
              filtered.sortedWith(
                compareByDescending<LibraryGameItem> { it.isFavorite }
                  .thenBy { it.displayName.lowercase() }
              )
          }

        val manualQuery = ctrl.manualSearchQuery.trim()
        val candidates =
          installedApps
            .asSequence()
            .filter { !visiblePackages.contains(it.packageName) }
            .filter { app ->
              if (manualQuery.isEmpty()) return@filter true
              app.displayName.contains(manualQuery, ignoreCase = true) ||
                app.packageName.contains(manualQuery, ignoreCase = true)
            }
            .map { app ->
              LaunchableAppCandidate(
                packageName = app.packageName,
                displayName = app.displayName,
                versionName = app.versionName,
                versionCode = app.versionCode,
                isSystemCategoryGame = app.isSystemCategoryGame,
                wasPreviouslyHidden = hiddenPackages.contains(app.packageName),
              )
            }
            .sortedWith(
              compareByDescending<LaunchableAppCandidate> { it.isSystemCategoryGame }
                .thenBy { it.displayName.lowercase() }
            )
            .toList()

        GameLibraryUiState(
          isScanning = ctrl.isScanning,
          scanError = ctrl.scanError,
          games = sorted,
          totalVisibleCount = visibleEntities.size,
          searchQuery = ctrl.searchQuery,
          sortOption = ctrl.sortOption,
          favoritesOnly = ctrl.favoritesOnly,
          viewMode = ctrl.viewMode,
          isAddSheetOpen = ctrl.isAddSheetOpen,
          manualSearchQuery = ctrl.manualSearchQuery,
          addCandidates = candidates,
          pendingRemoveGame = ctrl.pendingRemoveGame,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = GameLibraryUiState(isScanning = true),
      )

  init {
    rescanInstalledApps(showFeedback = false)
  }

  fun rescanInstalledApps(showFeedback: Boolean = true) {
    if (controls.value.isScanning) return
    viewModelScope.launch {
      controls.update { it.copy(isScanning = true, scanError = null) }
      scanner
        .scanLaunchableApps()
        .onSuccess { apps ->
          scannedLaunchableApps.value = apps
          runCatching { repository.syncInstalledApps(apps) }
            .onSuccess {
              val detectedCount = apps.count { it.isSystemCategoryGame }
              controls.update { it.copy(isScanning = false, scanError = null) }
              if (showFeedback) {
                _snackbarEvents.tryEmit(
                  "Scan complete: $detectedCount CATEGORY_GAME app(s) detected across ${apps.size} launchable apps"
                )
              }
            }
            .onFailure { err ->
              val msg = err.message ?: "Failed to save scan results"
              controls.update { it.copy(isScanning = false, scanError = msg) }
              _snackbarEvents.tryEmit(msg)
            }
        }
        .onFailure { err ->
          val msg = err.message ?: "Unable to scan installed applications"
          controls.update { it.copy(isScanning = false, scanError = msg) }
          if (showFeedback) _snackbarEvents.tryEmit(msg)
        }
    }
  }

  fun onSearchQueryChanged(query: String) {
    controls.update { it.copy(searchQuery = query) }
  }

  fun onSortOptionSelected(option: GameSortOption) {
    controls.update { it.copy(sortOption = option) }
  }

  fun onFavoritesOnlyToggled() {
    controls.update { it.copy(favoritesOnly = !it.favoritesOnly) }
  }

  fun onViewModeToggled() {
    controls.update {
      it.copy(viewMode = if (it.viewMode == GameViewMode.GRID) GameViewMode.LIST else GameViewMode.GRID)
    }
  }

  fun toggleFavorite(game: LibraryGameItem) {
    viewModelScope.launch {
      runCatching { repository.setFavorite(game.packageName, !game.isFavorite) }
        .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Failed to update favorite") }
    }
  }

  fun requestRemoveFromLibrary(game: LibraryGameItem) {
    controls.update { it.copy(pendingRemoveGame = game) }
  }

  fun cancelRemoveFromLibrary() {
    controls.update { it.copy(pendingRemoveGame = null) }
  }

  fun confirmRemoveFromLibrary() {
    val target = controls.value.pendingRemoveGame ?: return
    controls.update { it.copy(pendingRemoveGame = null) }
    viewModelScope.launch {
      runCatching { repository.hideGame(target.packageName) }
        .onSuccess {
          _snackbarEvents.tryEmit(
            "\"${target.displayName}\" removed from library. App remains installed on your device."
          )
        }
        .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Failed to remove entry") }
    }
  }

  fun openAddGameSheet() {
    controls.update { it.copy(isAddSheetOpen = true, manualSearchQuery = "") }
    if (scannedLaunchableApps.value.isEmpty()) {
      rescanInstalledApps(showFeedback = false)
    }
  }

  fun dismissAddGameSheet() {
    controls.update { it.copy(isAddSheetOpen = false, manualSearchQuery = "") }
  }

  fun onManualSearchQueryChanged(query: String) {
    controls.update { it.copy(manualSearchQuery = query) }
  }

  fun addGameManually(candidate: LaunchableAppCandidate) {
    viewModelScope.launch {
      runCatching {
          repository.addManualGame(
            ScannedGameMetadata(
              packageName = candidate.packageName,
              displayName = candidate.displayName,
              versionName = candidate.versionName,
              versionCode = candidate.versionCode,
              isSystemCategoryGame = candidate.isSystemCategoryGame,
            )
          )
        }
        .onSuccess {
          _snackbarEvents.tryEmit("Added \"${candidate.displayName}\" to DLSS Game Space")
        }
        .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Failed to add game") }
    }
  }

  fun launchGame(packageName: String, displayName: String) {
    gameLauncher
      .launchGame(packageName)
      .onFailure { err ->
        _snackbarEvents.tryEmit(err.message ?: "Unable to launch $displayName")
      }
  }

  suspend fun loadAppIcon(packageName: String): Bitmap? = iconCache.loadIcon(packageName)

  private fun GameEntity.toItem(): LibraryGameItem =
    LibraryGameItem(
      packageName = packageName,
      displayName = displayName,
      versionName = versionName,
      versionCode = versionCode,
      isSystemCategoryGame = isSystemCategoryGame,
      isManuallyAdded = isManuallyAdded,
      isFavorite = isFavorite,
      lastPlayedAt = lastPlayedAt,
      playCount = playCount,
      totalPlayTimeMs = totalPlayTimeMs,
    )

  class Factory(
    private val repository: GameLibraryRepository,
    private val scanner: InstalledAppsScanner,
    private val iconCache: AppIconCache,
    private val gameLauncher: GameLauncher,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return GameLibraryViewModel(repository, scanner, iconCache, gameLauncher) as T
    }
  }
}
