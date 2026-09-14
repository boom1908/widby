package com.widby.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.widby.data.StreakEntity
import com.widby.data.StreakRepository
import com.widby.data.StreakType
import com.widby.data.WidgetInstanceEntity
import com.widby.scheduling.WidbyWorkScheduler
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {
    private val repository by lazy { StreakRepository(applicationContext) }
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetType: StreakType = StreakType.MANUAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidbyWorkScheduler.schedule(this)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        widgetType = if (provider?.className?.contains("AutoWidgetReceiver") == true) {
            StreakType.AUTO
        } else {
            StreakType.MANUAL
        }
        setResult(Activity.RESULT_CANCELED)
        setContent {
            val streaks by repository.observeStreaks().collectAsState(emptyList())
            WidgetConfigScreen(
                type = widgetType,
                streaks = streaks.filter { it.type == widgetType.name },
                onSave = { selectedStreaks, label, accent, icon ->
                    lifecycleScope.launch {
                        val first = selectedStreaks.first()
                        repository.saveWidget(
                            WidgetInstanceEntity(
                                appWidgetId = appWidgetId,
                                streakId = first.id,
                                streakIds = selectedStreaks.joinToString(",") { it.id.toString() },
                                label = label.trim().ifBlank { first.name },
                                type = widgetType.name,
                                accentColor = accent,
                                icon = icon,
                            ),
                        )
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    }
                },
                onCancel = { finish() },
            )
        }
    }
}

@Composable
private fun WidgetConfigScreen(
    type: StreakType,
    streaks: List<StreakEntity>,
    onSave: (List<StreakEntity>, String, Long, String) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<StreakEntity?>(streaks.firstOrNull()) }
    var selectedIds by remember { mutableStateOf(streaks.take(1).map { it.id }.toSet()) }
    var label by remember { mutableStateOf(streaks.firstOrNull()?.name.orEmpty()) }
    var widgetAccent by remember { mutableLongStateOf(streaks.firstOrNull()?.accentColor ?: 0xFFA6E86BL) }
    var widgetIcon by remember { mutableStateOf(streaks.firstOrNull()?.icon ?: "✦") }
    LaunchedEffect(streaks) {
        if (selected == null && streaks.isNotEmpty()) {
            selected = streaks.first()
            selectedIds = setOf(streaks.first().id)
            label = streaks.first().name
            widgetAccent = streaks.first().accentColor
            widgetIcon = streaks.first().icon
        }
    }

    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (type == StreakType.MANUAL) "Add a manual counter" else "Add an auto daily counter",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Choose a streak and give this widget its own label. The label can be different on every widget instance.",
                color = Color.Gray,
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Widget name") },
                placeholder = { Text("e.g. Morning pages") },
            )
            Text("Widget icon", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("✦", "✎", "◒", "♬", "⌁").forEach { option ->
                    FilterChip(
                        selected = widgetIcon == option,
                        onClick = { widgetIcon = option },
                        label = { Text(option) },
                    )
                }
            }
            Text("Widget accent", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFA6E86BL, 0xFF8DD8FFL, 0xFFFFB86BL, 0xFFFF8FA3L, 0xFFC4A7FFL).forEach { option ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(28.dp)
                            .clip(CircleShape)
                            .clickable { widgetAccent = option }
                            .background(Color(option.toInt())),
                    )
                }
            }
            Text("Track this streak", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select up to 6 streaks for medium and large widget layouts.",
                color = Color.Gray,
            )
            if (streaks.isEmpty()) {
                Text("Create a streak in WIDBY first, then add its widget.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(streaks, key = { it.id }) { streak ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = streak
                                selectedIds = if (streak.id in selectedIds) {
                                    selectedIds - streak.id
                                } else if (selectedIds.size < 6) {
                                    selectedIds + streak.id
                                } else {
                                    selectedIds
                                }
                                if (label.isBlank()) label = streak.name
                                widgetAccent = streak.accentColor
                                widgetIcon = streak.icon
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (streak.id in selectedIds) {
                                    Color(widgetAccent.toInt()).copy(alpha = .16f)
                                } else {
                                    Color(0xFFEEF2EE)
                                },
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.Vertical.CenterVertically,
                            ) {
                                Text(streak.icon, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(streak.name)
                                    Text(
                                        if (streak.type == StreakType.AUTO.name) "Auto daily" else "Manual tap",
                                        color = Color.Gray,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val chosen = streaks.filter { it.id in selectedIds }
                        if (chosen.isNotEmpty()) onSave(chosen, label, widgetAccent, widgetIcon)
                    },
                    enabled = selectedIds.isNotEmpty() && label.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Add widget") }
                Button(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}