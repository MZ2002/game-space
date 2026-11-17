package com.dlssgamespace.gamelibrary.model

enum class GameSortOption(val label: String) {
  NAME("Name"),
  RECENT("Recent"),
  FAVORITE("Favorite"),
}

enum class GameViewMode {
  GRID,
  LIST,
}

data class LibraryGameItem(
  val packageName: String,
  val displayName: String,
  val versionName: String,
  val versionCode: Long,
  val isSystemCategoryGame: Boolean,
  val isManuallyAdded: Boolean,
  val isFavorite: Boolean,
  val lastPlayedAt: Long?,
  val playCount: Int,
  val totalPlayTimeMs: Long,
)

data class LaunchableAppCandidate(
  val packageName: String,
  val displayName: String,
  val versionName: String,
  val versionCode: Long,
  val isSystemCategoryGame: Boolean,
  val wasPreviouslyHidden: Boolean,
)
