package com.widby.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WidbyDao {
    @Query("SELECT * FROM streaks ORDER BY createdAtEpochDay DESC, id DESC")
    fun observeStreaks(): Flow<List<StreakEntity>>

    @Query("SELECT * FROM streaks WHERE id = :id LIMIT 1")
    fun observeStreak(id: Long): Flow<StreakEntity?>

    @Query("SELECT * FROM streaks WHERE id = :id LIMIT 1")
    suspend fun getStreak(id: Long): StreakEntity?

    @Query("SELECT * FROM streaks WHERE type = 'AUTO'")
    suspend fun getAutoStreaks(): List<StreakEntity>

    @Query("SELECT * FROM streaks WHERE type = 'MANUAL' AND reminderEnabled = 1")
    suspend fun getReminderStreaks(): List<StreakEntity>

    @Insert
    suspend fun insertStreak(streak: StreakEntity): Long

    @Update
    suspend fun updateStreak(streak: StreakEntity)

    @Delete
    suspend fun deleteStreak(streak: StreakEntity)

    @Query("SELECT * FROM activity_days WHERE streakId = :streakId ORDER BY dayEpoch DESC")
    fun observeActivity(streakId: Long): Flow<List<ActivityDayEntity>>

    @Query("SELECT * FROM activity_days WHERE streakId = :streakId ORDER BY dayEpoch DESC")
    suspend fun getActivity(streakId: Long): List<ActivityDayEntity>

    @Query("SELECT * FROM activity_days WHERE streakId = :streakId AND dayEpoch = :day LIMIT 1")
    suspend fun getActivityDay(streakId: Long, day: Long): ActivityDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: ActivityDayEntity)

    @Delete
    suspend fun deleteActivity(activity: ActivityDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWidget(widget: WidgetInstanceEntity)

    @Query("SELECT * FROM widget_instances WHERE appWidgetId = :id LIMIT 1")
    suspend fun getWidget(id: Int): WidgetInstanceEntity?

    @Query("SELECT * FROM widget_instances WHERE streakId = :streakId")
    suspend fun getWidgetsForStreak(streakId: Long): List<WidgetInstanceEntity>

    @Query("DELETE FROM widget_instances WHERE appWidgetId = :id")
    suspend fun deleteWidget(id: Int)
}