package com.dlssgamespace.database.model

data class LibrarySummary(
  val totalGames: Int = 0,
  val favoriteGames: Int = 0,
  val recentlyPlayedCount: Int = 0,
  val totalGamingTimeMs: Long = 0L,
)

data class ScannedGameMetadata(
  val packageName: String,
  val displayName: String,
  val versionName: String,
  val versionCode: Long,
  val isSystemCategoryGame: Boolean,
)
