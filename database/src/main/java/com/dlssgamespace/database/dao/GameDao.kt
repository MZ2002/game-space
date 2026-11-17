package com.dlssgamespace.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.database.entity.GameSessionEntity
import com.dlssgamespace.database.model.LibrarySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
  @Query("SELECT * FROM games WHERE isHidden = 0 ORDER BY displayName COLLATE NOCASE ASC")
  fun observeVisibleGames(): Flow<List<GameEntity>>

  @Query("SELECT * FROM games")
  fun observeAllGames(): Flow<List<GameEntity>>

  @Query("SELECT * FROM games")
  suspend fun getAllGamesOnce(): List<GameEntity>

  @Query("SELECT * FROM games WHERE packageName = :packageName LIMIT 1")
  suspend fun getGameByPackage(packageName: String): GameEntity?

  @Query(
    "SELECT * FROM games WHERE isHidden = 0 AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit"
  )
  fun observeRecentlyPlayed(limit: Int = 6): Flow<List<GameEntity>>

  @Query(
    "SELECT * FROM games WHERE isHidden = 0 AND isFavorite = 1 ORDER BY displayName COLLATE NOCASE ASC"
  )
  fun observeFavoriteGames(): Flow<List<GameEntity>>

  @Query(
    """
    SELECT
      COUNT(CASE WHEN isHidden = 0 THEN 1 END) AS totalGames,
      COUNT(CASE WHEN isHidden = 0 AND isFavorite = 1 THEN 1 END) AS favoriteGames,
      COUNT(CASE WHEN isHidden = 0 AND lastPlayedAt IS NOT NULL THEN 1 END) AS recentlyPlayedCount,
      COALESCE(SUM(totalPlayTimeMs), 0) AS totalGamingTimeMs
    FROM games
    """
  )
  fun observeLibrarySummary(): Flow<LibrarySummary>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnore(games: List<GameEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(game: GameEntity)

  @Query(
    """
    UPDATE games
    SET displayName = :displayName,
        versionName = :versionName,
        versionCode = :versionCode,
        isSystemCategoryGame = :isSystemCategoryGame
    WHERE packageName = :packageName
    """
  )
  suspend fun updateScannedMetadata(
    packageName: String,
    displayName: String,
    versionName: String,
    versionCode: Long,
    isSystemCategoryGame: Boolean,
  )

  @Query("UPDATE games SET isFavorite = :isFavorite WHERE packageName = :packageName")
  suspend fun setFavorite(packageName: String, isFavorite: Boolean)

  @Query("UPDATE games SET isHidden = 1 WHERE packageName = :packageName")
  suspend fun hideGame(packageName: String)

  @Query("UPDATE games SET isHidden = 0, isManuallyAdded = 1 WHERE packageName = :packageName")
  suspend fun unhideManualGame(packageName: String)

  @Query(
    """
    UPDATE games
    SET lastPlayedAt = :timestampMs,
        playCount = playCount + 1
    WHERE packageName = :packageName
    """
  )
  suspend fun recordLaunch(packageName: String, timestampMs: Long)

  @Query(
    """
    UPDATE games
    SET totalPlayTimeMs = totalPlayTimeMs + :durationMs
    WHERE packageName = :packageName
    """
  )
  suspend fun addPlayDuration(packageName: String, durationMs: Long)

  @Query("DELETE FROM games WHERE packageName IN (:packageNames)")
  suspend fun deleteByPackageNames(packageNames: List<String>)

  @Insert
  suspend fun insertSession(session: GameSessionEntity)

  @Transaction
  suspend fun recordCompletedSession(
    packageName: String,
    startedAt: Long,
    endedAt: Long,
    durationMs: Long,
  ) {
    if (durationMs > 0L) {
      addPlayDuration(packageName, durationMs)
      insertSession(
        GameSessionEntity(
          packageName = packageName,
          startedAt = startedAt,
          endedAt = endedAt,
          durationMs = durationMs,
        )
      )
    }
  }
}
