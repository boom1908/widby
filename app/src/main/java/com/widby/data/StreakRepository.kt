package com.widby.data

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlinx.coroutines.flow.Flow

class StreakRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = WidbyDatabase.get(context)
    private val dao = database.dao()

    fun appContext(): Context = appContext

    fun observeStreaks(): Flow<List<StreakEntity>> = dao.observeStreaks()
    fun observeStreak(id: Long): Flow<StreakEntity?> = dao.observeStreak(id)
    fun observeActivity(id: Long): Flow<List<ActivityDayEntity>> = dao.observeActivity(id)

    suspend fun createStreak(
        name: String,
        type: StreakType,
        icon: String,
        accentColor: Long,
        dailyResetMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
    ): Long = dao.insertStreak(
        StreakEntity(
            name = name.trim(),
            icon = icon,
            type = type.name,
            accentColor = accentColor,
            dailyResetMinutes = dailyResetMinutes,
            reminderEnabled = reminderEnabled && type == StreakType.MANUAL,
            reminderMinutes = reminderMinutes,
            createdAtEpochDay = LocalDate.now().toEpochDay(),
        ),
    )

    suspend fun deleteStreak(streak: StreakEntity) {
        database.withTransaction {
            dao.deleteStreak(streak)
        }
    }

    suspend fun manualTap(streakId: Long) {
        database.withTransaction {
            val streak = dao.getStreak(streakId) ?: return@withTransaction
            val day = logicalDay(streak.dailyResetMinutes)
            val existing = dao.getActivityDay(streakId, day)
            dao.upsertActivity(
                ActivityDayEntity(streakId, day, (existing?.amount ?: 0) + 1),
            )
            val activity = dao.getActivity(streakId)
            val current = currentStreak(activity.map { it.dayEpoch }.toSet(), day)
            dao.updateStreak(
                streak.copy(
                    count = streak.count + 1,
                    currentStreak = current,
                    bestStreak = maxOf(streak.bestStreak, current),
                ),
            )
        }
    }

    suspend fun undoManualTap(streakId: Long) {
        database.withTransaction {
            val streak = dao.getStreak(streakId) ?: return@withTransaction
            if (streak.count == 0) return@withTransaction
            val activity = dao.getActivity(streakId)
            val latest = activity.maxByOrNull { it.dayEpoch }
            if (latest != null) {
                if (latest.amount > 1) {
                    dao.upsertActivity(latest.copy(amount = latest.amount - 1))
                } else {
                    dao.deleteActivity(latest)
                }
            }
            val remaining = dao.getActivity(streakId)
            val current = currentStreak(remaining.map { it.dayEpoch }.toSet(), logicalDay(streak.dailyResetMinutes))
            dao.updateStreak(streak.copy(count = (streak.count - 1).coerceAtLeast(0), currentStreak = current))
        }
    }

    suspend fun resetAuto(streakId: Long) {
        database.withTransaction {
            val streak = dao.getStreak(streakId) ?: return@withTransaction
            dao.updateStreak(streak.copy(count = 0, currentStreak = 0, lastAutoIncrementEpochDay = null))
            dao.getActivity(streakId).forEach { dao.deleteActivity(it) }
        }
    }

    suspend fun incrementDueAutoStreaks(): List<Long> {
        val updated = mutableListOf<Long>()
        database.withTransaction {
            dao.getAutoStreaks().forEach { streak ->
                val day = logicalDay(streak.dailyResetMinutes)
                if (streak.lastAutoIncrementEpochDay != day) {
                    dao.upsertActivity(ActivityDayEntity(streak.id, day))
                    val activity = dao.getActivity(streak.id)
                    val current = currentStreak(activity.map { it.dayEpoch }.toSet(), day)
                    dao.updateStreak(
                        streak.copy(
                            count = streak.count + 1,
                            currentStreak = current,
                            bestStreak = maxOf(streak.bestStreak, current),
                            lastAutoIncrementEpochDay = day,
                        ),
                    )
                    updated += streak.id
                }
            }
        }
        return updated
    }

    suspend fun getWidget(id: Int): WidgetInstanceEntity? = dao.getWidget(id)
    suspend fun saveWidget(widget: WidgetInstanceEntity) = dao.saveWidget(widget)
    suspend fun deleteWidget(id: Int) = dao.deleteWidget(id)
    suspend fun getStreak(id: Long): StreakEntity? = dao.getStreak(id)
    suspend fun getReminderStreaks(): List<StreakEntity> = dao.getReminderStreaks()

    suspend fun resetAutoWidget(widgetId: Int) {
        val widget = dao.getWidget(widgetId) ?: return
        val ids = widget.streakIds
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .ifEmpty { listOf(widget.streakId) }
        ids.forEach { resetAuto(it) }
    }

    private fun logicalDay(resetMinutes: Int): Long {
        val now = LocalDateTime.now()
        val date = if (now.hour * 60 + now.minute < resetMinutes) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
        return date.toEpochDay()
    }

    private fun currentStreak(activeDays: Set<Long>, today: Long): Int {
        if (activeDays.isEmpty()) return 0
        var cursor = today
        var total = 0
        val skippedWeeks = mutableSetOf<String>()
        while (true) {
            if (cursor in activeDays) {
                total++
            } else {
                val date = LocalDate.ofEpochDay(cursor)
                val week = "${date.get(WeekFields.ISO.weekBasedYear())}-${date.get(WeekFields.ISO.weekOfWeekBasedYear())}"
                if (!skippedWeeks.add(week)) break
            }
            cursor--
            if (total > 3660) break
        }
        return total
    }
}