package com.dlssgamespace.gamelibrary.ui

import com.dlssgamespace.gamelibrary.model.GameSortOption
import com.dlssgamespace.gamelibrary.model.GameViewMode
import com.dlssgamespace.gamelibrary.model.LaunchableAppCandidate
import com.dlssgamespace.gamelibrary.model.LibraryGameItem

data class GameLibraryUiState(
  val isScanning: Boolean = false,
  val scanError: String? = null,
  val games: List<LibraryGameItem> = emptyList(),
  val totalVisibleCount: Int = 0,
  val searchQuery: String = "",
  val sortOption: GameSortOption = GameSortOption.NAME,
  val favoritesOnly: Boolean = false,
  val viewMode: GameViewMode = GameViewMode.GRID,
  val isAddSheetOpen: Boolean = false,
  val manualSearchQuery: String = "",
  val addCandidates: List<LaunchableAppCandidate> = emptyList(),
  val pendingRemoveGame: LibraryGameItem? = null,
)
