package com.dlssgamespace.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dlssgamespace.database.dao.ControlDao
import com.dlssgamespace.database.dao.GameDao
import com.dlssgamespace.database.entity.CustomPerformanceProfileEntity
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.database.entity.GameProfileEntity
import com.dlssgamespace.database.entity.GameSessionEntity
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.database.entity.SystemBackupEntity
import com.dlssgamespace.database.entity.ThermalHistoryEntity

@Database(
  entities =
    [
      GameEntity::class,
      GameSessionEntity::class,
      GameProfileEntity::class,
      CustomPerformanceProfileEntity::class,
      SystemBackupEntity::class,
      ThermalHistoryEntity::class,
      RootCommandLogEntity::class,
    ],
  version = 2,
  exportSchema = false,
)
abstract class DlssDatabase : RoomDatabase() {
  abstract fun gameDao(): GameDao

  abstract fun controlDao(): ControlDao

  companion object {
    @Volatile private var instance: DlssDatabase? = null

    fun getInstance(context: Context): DlssDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                DlssDatabase::class.java,
                "dlss_game_space.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
