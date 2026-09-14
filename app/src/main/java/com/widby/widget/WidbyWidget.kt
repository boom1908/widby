package com.widby.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import com.widby.data.StreakEntity
import com.widby.data.StreakRepository
import com.widby.data.StreakType

abstract class WidbyWidget(private val expectedType: StreakType) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val repository = StreakRepository(context)
        val widget = repository.getWidget(appWidgetId)
        val streakIds = widget?.streakIds
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.ifEmpty { widget?.let { listOf(it.streakId) } }
            ?: emptyList()
        val streaks = streakIds.mapNotNull { repository.getStreak(it) }

        provideContent {
            val size = LocalSize.current
            val accent = ColorProvider(
                day = Color((widget?.accentColor ?: 0xFFA6E86BL).toInt()),
                night = Color((widget?.accentColor ?: 0xFFA6E86BL).toInt()),
            )
            if (widget == null || widget.type != expectedType.name || streaks.isEmpty()) {
                EmptyWidget()
            } else if (streaks.size > 1) {
                GridWidget(
                    label = widget.label,
                    icon = widget.icon,
                    streaks = streaks,
                    accent = accent,
                    type = expectedType,
                    appWidgetId = appWidgetId,
                    large = size.width > 300.dp || size.height > 300.dp,
                )
            } else if (size.width > 220.dp) {
                WideWidget(
                    label = widget.label,
                    icon = widget.icon,
                    streak = streaks.first(),
                    accent = accent,
                    type = expectedType,
                    appWidgetId = appWidgetId,
                )
            } else {
                CompactWidget(
                    label = widget.label,
                    icon = widget.icon,
                    streak = streaks.first(),
                    accent = accent,
                    type = expectedType,
                    appWidgetId = appWidgetId,
                )
            }
        }
    }
}

class ManualWidget : WidbyWidget(StreakType.MANUAL)
class AutoWidget : WidbyWidget(StreakType.AUTO)

class ManualWidgetReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ManualWidget()
}

class AutoWidgetReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AutoWidget()
}

@androidx.compose.runtime.Composable
private fun EmptyWidget() {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(darkSurface).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Open WIDBY to configure", style = TextStyle(color = lightText, fontSize = 12.sp))
    }
}

@androidx.compose.runtime.Composable
private fun CompactWidget(
    label: String,
    icon: String,
    streak: StreakEntity,
    accent: ColorProvider,
    type: StreakType,
    appWidgetId: Int,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(darkSurface).padding(14.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text("$icon  $label", style = TextStyle(color = mutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.size(5.dp))
        Text(streak.count.toString(), style = TextStyle(color = accent, fontSize = 38.sp, fontWeight = FontWeight.Bold))
        Text("${streak.currentStreak} day streak", style = TextStyle(color = mutedText, fontSize = 11.sp))
        Spacer(GlanceModifier.size(6.dp))
        WidgetActionButtons(type, appWidgetId, streak.id)
    }
}

@androidx.compose.runtime.Composable
private fun WideWidget(
    label: String,
    icon: String,
    streak: StreakEntity,
    accent: ColorProvider,
    type: StreakType,
    appWidgetId: Int,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().background(darkSurface).padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text("$icon  $label", style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Text(streak.name, style = TextStyle(color = lightText, fontSize = 14.sp))
            Spacer(GlanceModifier.size(8.dp))
            Text("${streak.currentStreak} day streak", style = TextStyle(color = mutedText, fontSize = 12.sp))
        }
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(streak.count.toString(), style = TextStyle(color = accent, fontSize = 38.sp, fontWeight = FontWeight.Bold))
            WidgetActionButtons(type, appWidgetId, streak.id)
        }
    }
}

@androidx.compose.runtime.Composable
private fun GridWidget(
    label: String,
    icon: String,
    streaks: List<StreakEntity>,
    accent: ColorProvider,
    type: StreakType,
    appWidgetId: Int,
    large: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(darkSurface).padding(12.dp),
    ) {
        Text("$icon  $label", style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.size(6.dp))
        streaks.chunked(if (large) 2 else 1).forEach { rowStreaks ->
            Row {
                rowStreaks.forEach { streak ->
                    Column(
                        modifier = GlanceModifier.defaultWeight().padding(5.dp),
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    ) {
                        Text(streak.icon, style = TextStyle(color = accent, fontSize = 14.sp))
                        Text(streak.count.toString(), style = TextStyle(color = lightText, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        Text(
                            "${streak.currentStreak}d",
                            style = TextStyle(color = mutedText, fontSize = 10.sp),
                        )
                        WidgetActionButtons(type, appWidgetId, streak.id)
                    }
                }
            }
            Spacer(GlanceModifier.size(4.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetActionButtons(type: StreakType, appWidgetId: Int, streakId: Long) {
    if (type == StreakType.MANUAL) {
        Row {
            androidx.glance.Button(
                text = "+1",
                onClick = actionRunCallback<ManualTapAction>(
                    actionParametersOf(widgetIdParameter to appWidgetId, streakIdParameter to streakId),
                ),
            )
            Spacer(GlanceModifier.width(4.dp))
            androidx.glance.Button(
                text = "Undo",
                onClick = actionRunCallback<ManualUndoAction>(
                    actionParametersOf(widgetIdParameter to appWidgetId, streakIdParameter to streakId),
                ),
            )
        }
    } else {
        androidx.glance.Button(
            text = "Reset",
            onClick = actionRunCallback<AutoResetRequestAction>(
                actionParametersOf(widgetIdParameter to appWidgetId),
            ),
        )
    }
}

private val darkSurface = ColorProvider(day = Color(0xFF17201D), night = Color(0xFF17201D))
private val lightText = ColorProvider(day = Color(0xFFF4F8F2), night = Color(0xFFF4F8F2))
private val mutedText = ColorProvider(day = Color(0xFF9BAAA2), night = Color(0xFF9BAAA2))