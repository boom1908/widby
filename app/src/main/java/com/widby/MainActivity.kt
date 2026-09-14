package com.widby

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.glance.appwidget.updateAll
import com.widby.data.ActivityDayEntity
import com.widby.data.StreakEntity
import com.widby.data.StreakRepository
import com.widby.data.StreakType
import com.widby.scheduling.NotificationHelper
import com.widby.scheduling.WidbyWorkScheduler
import com.widby.widget.AutoWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONFIRM_RESET_WIDGET = "confirm_reset_widget"
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        WidbyWorkScheduler.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WidbyTheme {
                WidbyApp(
                    initialResetWidgetId = intent.getIntExtra(EXTRA_CONFIRM_RESET_WIDGET, -1)
                        .takeIf { it > 0 },
                )
            }
        }
    }
}

class MainViewModel(private val repository: StreakRepository) : ViewModel() {
    val streaks: StateFlow<List<StreakEntity>> = repository.observeStreaks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun createStreak(
        name: String,
        type: StreakType,
        icon: String,
        accent: Long,
        resetMinutes: Int,
        reminderEnabled: Boolean,
        reminderMinutes: Int,
    ) = viewModelScope.launch {
        repository.createStreak(
            name,
            type,
            icon,
            accent,
            resetMinutes,
            reminderEnabled,
            reminderMinutes,
        )
    }

    fun delete(streak: StreakEntity) = viewModelScope.launch { repository.deleteStreak(streak) }

    fun resetWidget(widgetId: Int) = viewModelScope.launch {
        repository.resetAutoWidget(widgetId)
        AutoWidget().updateAll(repository.appContext())
    }

    fun activity(streakId: Long) = repository.observeActivity(streakId)
}

@Composable
private fun WidbyApp(initialResetWidgetId: Int?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: MainViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(StreakRepository(context.applicationContext)) as T
        },
    )
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var selectedId by remember { mutableLongStateOf(-1L) }
    var resetWidgetId by remember { mutableStateOf(initialResetWidgetId) }
    val selected = streaks.firstOrNull { it.id == selectedId }
    val activity by if (selected != null) {
        vm.activity(selected.id).collectAsStateWithLifecycle(emptyList())
    } else {
        remember { mutableStateOf<List<ActivityDayEntity>>(emptyList()) }
    }

    if (selected != null) {
        HeatmapScreen(
            streak = selected,
            activity = activity,
            onBack = { selectedId = -1L },
        )
    } else {
        HomeScreen(
            streaks = streaks,
            onAdd = { showAdd = true },
            onHistory = { selectedId = it },
            onDelete = vm::delete,
        )
    }

    if (showAdd) {
        AddStreakDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, type, icon, accent, reset, reminder, reminderTime ->
                vm.createStreak(name, type, icon, accent, reset, reminder, reminderTime)
                showAdd = false
            },
        )
    }

    resetWidgetId?.let { widgetId ->
        AlertDialog(
            onDismissRequest = { resetWidgetId = null },
            title = { Text("Reset auto streak?") },
            text = { Text("This clears the count and its daily history. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.resetWidget(widgetId)
                        resetWidgetId = null
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { resetWidgetId = null }) { Text("Keep it") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    streaks: List<StreakEntity>,
    onAdd: () -> Unit,
    onHistory: (Long) -> Unit,
    onDelete: (StreakEntity) -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Column {
                        Text("WIDBY", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Streaks that stay visible", fontSize = 12.sp, color = muted)
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add streak")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = lime,
                contentColor = ink,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add streak")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroSummary(streaks)
            }
            if (streaks.isEmpty()) {
                item {
                    EmptyState(onAdd)
                }
            } else {
                item {
                    Text(
                        "Your streaks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(streaks, key = { it.id }) { streak ->
                    StreakCard(
                        streak = streak,
                        onHistory = { onHistory(streak.id) },
                        onDelete = { onDelete(streak) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSummary(streaks: List<StreakEntity>) {
    val active = streaks.count { it.currentStreak > 0 }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF19221F)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep the chain going", color = lime, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (streaks.isEmpty()) "Add your first widget-led streak."
                    else "$active of ${streaks.size} streaks active today.",
                    color = Color(0xFFD7E1DB),
                )
                Text(
                    "Your homescreen is the habit loop.",
                    color = muted,
                    fontSize = 12.sp,
                )
            }
            Icon(
                Icons.Outlined.Whatshot,
                contentDescription = null,
                tint = lime,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = lime, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text("Nothing tracking yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Create a streak, then place its counter on your homescreen.",
            color = muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Button(onClick = onAdd) { Text("Create a streak") }
    }
}

@Composable
private fun StreakCard(streak: StreakEntity, onHistory: () -> Unit, onDelete: () -> Unit) {
    val accent = Color(streak.accentColor.toInt())
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17201D)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = .17f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(streak.icon, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(streak.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (streak.type == StreakType.AUTO.name) "Auto daily counter" else "Manual tap counter",
                        color = muted,
                        fontSize = 12.sp,
                    )
                }
                CircularProgressIndicator(
                    progress = { (streak.currentStreak / 30f).coerceIn(0f, 1f) },
                    color = accent,
                    trackColor = Color(0xFF2A3530),
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(streak.count.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = accent)
                    Text("total count", color = muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${streak.currentStreak} days", fontWeight = FontWeight.Bold)
                    Text("best ${streak.bestStreak}", color = muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { (streak.currentStreak / 30f).coerceIn(0f, 1f) },
                color = accent,
                trackColor = Color(0xFF2A3530),
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("History")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${streak.name}", tint = muted)
                }
            }
        }
    }
}

@Composable
private fun AddStreakDialog(
    onDismiss: () -> Unit,
    onCreate: (String, StreakType, String, Long, Int, Boolean, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(StreakType.MANUAL) }
    var icon by remember { mutableStateOf("✦") }
    var accent by remember { mutableLongStateOf(0xFFA6E86BL) }
    var resetTime by remember { mutableStateOf("04:00") }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderTime by remember { mutableStateOf("20:00") }
    val resetMinutes = parseTime(resetTime, 240)
    val reminderMinutes = parseTime(reminderTime, 1200)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New streak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Streak name") },
                    placeholder = { Text("Morning pages") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == StreakType.MANUAL,
                        onClick = { type = StreakType.MANUAL },
                        label = { Text("Manual tap") },
                    )
                    FilterChip(
                        selected = type == StreakType.AUTO,
                        onClick = { type = StreakType.AUTO },
                        label = { Text("Auto daily") },
                    )
                }
                Text("Icon", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("✦", "✎", "◒", "♬", "⌁").forEach { option ->
                        FilterChip(
                            selected = icon == option,
                            onClick = { icon = option },
                            label = { Text(option) },
                        )
                    }
                }
                Text("Accent", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accentOptions.forEach { option ->
                        Box(
                            Modifier.size(28.dp)
                                .clip(CircleShape)
                                .background(Color(option.toInt()))
                                .clickable { accent = option },
                        ) {
                            if (accent == option) {
                                Box(
                                    Modifier.padding(6.dp).fillMaxSize().clip(CircleShape)
                                        .background(Color(0xCC0D1110)),
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = resetTime,
                    onValueChange = { resetTime = it },
                    singleLine = true,
                    label = { Text("Daily reset time") },
                    supportingText = { Text("24-hour time, e.g. 04:00") },
                )
                AnimatedVisibility(visible = type == StreakType.MANUAL) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = muted)
                            Spacer(Modifier.width(8.dp))
                            Text("Remind me if I haven't tapped")
                            Spacer(Modifier.weight(1f))
                            Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                        }
                        if (reminderEnabled) {
                            OutlinedTextField(
                                value = reminderTime,
                                onValueChange = { reminderTime = it },
                                singleLine = true,
                                label = { Text("Reminder time") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name, type, icon, accent, resetMinutes, reminderEnabled, reminderMinutes)
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeatmapScreen(streak: StreakEntity, activity: List<ActivityDayEntity>, onBack: () -> Unit) {
    val accent = Color(streak.accentColor.toInt())
    val active = activity.associateBy { it.dayEpoch }
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("${streak.icon}  ${streak.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Consistency map", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Your last 12 weeks", color = muted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                (0 until 12).forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        (6 downTo 0).forEach { dayOffset ->
                            val epoch = today.minusDays((11 - week) * 7L + dayOffset).toEpochDay()
                            val amount = active[epoch]?.amount ?: 0
                            Box(
                                Modifier.size(17.dp).clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (amount > 0) accent.copy(alpha = (0.25f + (amount.coerceAtMost(4) / 4f) * .75f))
                                        else Color(0xFF27322D),
                                    ),
                            )
                        }
                    }
                }
            }
            Divider(color = Color(0xFF2A3530))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                HistoryStat("Current", "${streak.currentStreak} days", accent)
                HistoryStat("Best", "${streak.bestStreak} days", accent)
                HistoryStat("Total", streak.count.toString(), accent)
            }
            Text(
                "A freeze can cover one missed day per week on manual streaks.",
                color = muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String, accent: Color) {
    Column {
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun WidbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = lime,
            onPrimary = ink,
            background = ink,
            surface = Color(0xFF111815),
            onBackground = Color(0xFFF4F8F2),
            onSurface = Color(0xFFF4F8F2),
        ),
        content = content,
    )
}

private fun parseTime(value: String, fallback: Int): Int {
    val parts = value.split(":")
    if (parts.size != 2) return fallback
    val hour = parts[0].toIntOrNull() ?: return fallback
    val minute = parts[1].toIntOrNull() ?: return fallback
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else fallback
}

private val accentOptions = listOf(
    0xFFA6E86BL,
    0xFF8DD8FFL,
    0xFFFFB86BL,
    0xFFFF8FA3L,
    0xFFC4A7FFL,
)
private val lime = Color(0xFFA6E86B)
private val ink = Color(0xFF0D1110)
private val muted = Color(0xFF9BAAA2)