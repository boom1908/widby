package com.widby.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.widby.R
import com.widby.data.StreakRepository
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class AutoDailyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = StreakRepository(applicationContext)
        val updated = repository.incrementDueAutoStreaks()
        if (updated.isNotEmpty()) {
            com.widby.widget.AutoWidget().updateAll(applicationContext)
        }
        return Result.success()
    }
}

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = StreakRepository(applicationContext)
        val now = LocalDateTime.now()
        repository.getReminderStreaks().forEach { streak ->
            val currentMinute = now.hour * 60 + now.minute
            val today = now.toLocalDate().toEpochDay()
            val key = "reminded-${streak.id}-$today"
            val alreadyReminded = applicationContext
                .getSharedPreferences("widby-reminders", Context.MODE_PRIVATE)
                .getBoolean(key, false)
            if (currentMinute >= streak.reminderMinutes && !alreadyReminded) {
                NotificationHelper.showReminder(applicationContext, streak.id, streak.name)
                applicationContext
                    .getSharedPreferences("widby-reminders", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(key, true)
                    .apply()
            }
        }
        return Result.success()
    }
}

object WidbyWorkScheduler {
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val autoWork = PeriodicWorkRequestBuilder<AutoDailyWorker>(15, TimeUnit.MINUTES).build()
        val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            "widby-auto-daily",
            ExistingPeriodicWorkPolicy.UPDATE,
            autoWork,
        )
        workManager.enqueueUniquePeriodicWork(
            "widby-reminders",
            ExistingPeriodicWorkPolicy.UPDATE,
            reminderWork,
        )
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "streak-reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Streak reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders for manual WIDBY streaks"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun showReminder(context: Context, streakId: Long, name: String) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Keep $name going")
            .setContentText("A quick tap keeps today's streak active.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(("widby-$streakId").hashCode(), notification)
    }
}