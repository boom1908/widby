package com.widby.widget

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.glance.GlanceId
import androidx.glance.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.updateAll
import com.widby.MainActivity
import com.widby.data.StreakRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val widgetIdParameter = ActionParameters.Key<Int>("widby_widget_id")
val streakIdParameter = ActionParameters.Key<Long>("widby_streak_id")

class ManualTapAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[widgetIdParameter] ?: return
        withContext(Dispatchers.IO) {
            val widget = StreakRepository(context).getWidget(id) ?: return@withContext
            val streakId = parameters[streakIdParameter] ?: widget.streakId
            StreakRepository(context).manualTap(streakId)
        }
        vibrate(context)
        ManualWidget().updateAll(context)
    }
}

class ManualUndoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[widgetIdParameter] ?: return
        withContext(Dispatchers.IO) {
            val widget = StreakRepository(context).getWidget(id) ?: return@withContext
            val streakId = parameters[streakIdParameter] ?: widget.streakId
            StreakRepository(context).undoManualTap(streakId)
        }
        ManualWidget().updateAll(context)
    }
}

class AutoResetRequestAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[widgetIdParameter] ?: return
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_CONFIRM_RESET_WIDGET, id),
        )
    }
}

private fun vibrate(context: Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
}