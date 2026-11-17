package com.dlssgamespace.database.repository

import androidx.room.withTransaction
import com.dlssgamespace.database.DlssDatabase
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.database.model.LibrarySummary
import com.dlssgamespace.database.model.ScannedGameMetadata
import kotlinx.coroutines.flow.Flow

class GameLibraryRepository(private val database: DlssDatabase) {
  private val gameDao = database.gameDao()

  fun observeVisibleGames(): Flow<List<GameEntity>> = gameDao.observeVisibleGames()

  fun observeAllGames(): Flow<List<GameEntity>> = gameDao.observeAllGames()

  fun observeRecentlyPlayed(limit: Int = 6): Flow<List<GameEntity>> =
    gameDao.observeRecentlyPlayed(limit)

  fun observeFavoriteGames(): Flow<List<GameEntity>> = gameDao.observeFavoriteGames()

  fun observeLibrarySummary(): Flow<LibrarySummary> = gameDao.observeLibrarySummary()

  suspend fun syncInstalledApps(
    installedApps: List<ScannedGameMetadata>,
    nowMs: Long = System.currentTimeMillis(),
  ) {
    val installedPackageSet = installedApps.map { it.packageName }.toSet()

    database.withTransaction {
      val existingByPackage = gameDao.getAllGamesOnce().associateBy { it.packageName }

      // Remove entries for apps that have been uninstalled from Android itself
      if (installedPackageSet.isNotEmpty()) {
        val uninstalledPackages =
          existingByPackage.keys.filter { pkg -> !installedPackageSet.contains(pkg) }
        if (uninstalledPackages.isNotEmpty()) {
          gameDao.deleteByPackageNames(uninstalledPackages)
        }
      }

      val newDetectedGames = mutableListOf<GameEntity>()
      for (app in installedApps) {
        val existing = existingByPackage[app.packageName]
        if (existing != null) {
          // Preserve isHidden, isFavorite, lastPlayedAt, playCount, totalPlayTimeMs, isManuallyAdded
          gameDao.updateScannedMetadata(
            packageName = app.packageName,
            displayName = app.displayName,
            versionName = app.versionName,
            versionCode = app.versionCode,
            isSystemCategoryGame = app.isSystemCategoryGame,
          )
        } else if (app.isSystemCategoryGame) {
          newDetectedGames.add(
            GameEntity(
              packageName = app.packageName,
              displayName = app.displayName,
              versionName = app.versionName,
              versionCode = app.versionCode,
              isSystemCategoryGame = true,
              isManuallyAdded = false,
              isHidden = false,
              isFavorite = false,
              addedAt = nowMs,
            )
          )
        }
      }

      if (newDetectedGames.isNotEmpty()) {
        gameDao.insertIgnore(newDetectedGames)
      }
    }
  }

  suspend fun addManualGame(
    metadata: ScannedGameMetadata,
    nowMs: Long = System.currentTimeMillis(),
  ) {
    database.withTransaction {
      val existing = gameDao.getGameByPackage(metadata.packageName)
      if (existing != null) {
        gameDao.updateScannedMetadata(
          packageName = metadata.packageName,
          displayName = metadata.displayName,
          versionName = metadata.versionName,
          versionCode = metadata.versionCode,
          isSystemCategoryGame = metadata.isSystemCategoryGame,
        )
        gameDao.unhideManualGame(metadata.packageName)
      } else {
        gameDao.upsert(
          GameEntity(
            packageName = metadata.packageName,
            displayName = metadata.displayName,
            versionName = metadata.versionName,
            versionCode = metadata.versionCode,
            isSystemCategoryGame = metadata.isSystemCategoryGame,
            isManuallyAdded = true,
            isHidden = false,
            isFavorite = false,
            addedAt = nowMs,
          )
        )
      }
    }
  }

  suspend fun setFavorite(packageName: String, isFavorite: Boolean) {
    gameDao.setFavorite(packageName, isFavorite)
  }

  suspend fun hideGame(packageName: String) {
    gameDao.hideGame(packageName)
  }

  suspend fun recordGameLaunch(packageName: String, timestampMs: Long = System.currentTimeMillis()) {
    gameDao.recordLaunch(packageName, timestampMs)
  }

  suspend fun recordCompletedSession(
    packageName: String,
    startedAt: Long,
    endedAt: Long,
    durationMs: Long,
  ) {
    gameDao.recordCompletedSession(packageName, startedAt, endedAt, durationMs)
  }
}
