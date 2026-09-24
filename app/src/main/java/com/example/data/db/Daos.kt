package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AllowedAppEntity
import com.example.data.model.KioskSettingsEntity
import com.example.data.model.RoadObstacleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps ORDER BY orderIndex ASC, appName ASC")
    fun getAllApps(): Flow<List<AllowedAppEntity>>

    @Query("SELECT * FROM allowed_apps WHERE isAllowed = 1 ORDER BY orderIndex ASC, appName ASC")
    fun getAllowedApps(): Flow<List<AllowedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateApps(apps: List<AllowedAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AllowedAppEntity)

    @Update
    suspend fun updateApp(app: AllowedAppEntity)

    @Query("UPDATE allowed_apps SET isAllowed = :isAllowed WHERE packageName = :packageName")
    suspend fun setAppAllowed(packageName: String, isAllowed: Boolean)

    @Query("DELETE FROM allowed_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)
}

@Dao
interface RoadObstacleDao {
    @Query("SELECT * FROM road_obstacles ORDER BY createdAt DESC")
    fun getAllObstacles(): Flow<List<RoadObstacleEntity>>

    @Query("SELECT * FROM road_obstacles WHERE isSuppressedFake = 0")
    fun getActiveObstacles(): Flow<List<RoadObstacleEntity>>

    @Query("SELECT * FROM road_obstacles WHERE isSuppressedFake = 0")
    suspend fun getActiveObstaclesList(): List<RoadObstacleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateObstacles(obstacles: List<RoadObstacleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObstacle(obstacle: RoadObstacleEntity)

    @Query("UPDATE road_obstacles SET strikeCount = :strikes, isSuppressedFake = :suppressed WHERE id = :id")
    suspend fun updateStrike(id: String, strikes: Int, suppressed: Boolean)

    @Query("DELETE FROM road_obstacles WHERE id = :id")
    suspend fun deleteObstacle(id: String)

    @Query("DELETE FROM road_obstacles")
    suspend fun clearAll()
}

@Dao
interface KioskSettingsDao {
    @Query("SELECT * FROM kiosk_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<KioskSettingsEntity?>

    @Query("SELECT * FROM kiosk_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSnapshot(): KioskSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: KioskSettingsEntity)
}
