package com.widby.data

import androidx.room.Entity
import androidx.room.Index

enum class StreakType { MANUAL, AUTO }

@Entity(tableName = "streaks")
data class StreakEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val type: String,
    val count: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val accentColor: Long,
    val dailyResetMinutes: Int = 240,
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 1200,
    val createdAtEpochDay: Long,
    val lastAutoIncrementEpochDay: Long? = null,
)

@Entity(
    tableName = "activity_days",
    primaryKeys = ["streakId", "dayEpoch"],
    indices = [Index("streakId")],
)
data class ActivityDayEntity(
    val streakId: Long,
    val dayEpoch: Long,
    val amount: Int = 1,
)

@Entity(tableName = "widget_instances")
data class WidgetInstanceEntity(
    @androidx.room.PrimaryKey val appWidgetId: Int,
    val streakId: Long,
    val streakIds: String = "",
    val label: String,
    val type: String,
    val accentColor: Long,
    val icon: String,
)