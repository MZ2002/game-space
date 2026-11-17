package com.dlssgamespace.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
  @PrimaryKey val packageName: String,
  val displayName: String,
  val versionName: String,
  val versionCode: Long,
  val isSystemCategoryGame: Boolean,
  val isManuallyAdded: Boolean,
  val isHidden: Boolean = false,
  val isFavorite: Boolean = false,
  val lastPlayedAt: Long? = null,
  val playCount: Int = 0,
  val totalPlayTimeMs: Long = 0L,
  val addedAt: Long,
)

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val packageName: String,
  val startedAt: Long,
  val endedAt: Long,
  val durationMs: Long,
)
