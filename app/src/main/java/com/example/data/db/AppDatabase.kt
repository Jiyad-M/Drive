package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AllowedAppEntity
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.RoadObstacleEntity

@Database(
    entities = [
        AllowedAppEntity::class,
        RoadObstacleEntity::class,
        KioskSettingsEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun roadObstacleDao(): RoadObstacleDao
    abstract fun kioskSettingsDao(): KioskSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kiosk_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
