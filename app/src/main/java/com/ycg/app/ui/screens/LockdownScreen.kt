package com.ycg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ycg.app.data.LockdownWindow
import com.ycg.app.data.MAX_SCROLL_THRESHOLD
import com.ycg.app.data.MIN_SCROLL_THRESHOLD
import com.ycg.app.ui.LockdownViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownScreen(viewModel: LockdownViewModel = viewModel()) {
    val windows by viewModel.windows.collectAsState()
    val active by viewModel.activeWindow.collectAsState()
    val blockShorts by viewModel.blockShorts.collectAsState()
    val scrollEnabled by viewModel.scrollCounterEnabled.collectAsState()
    val scrollThreshold by viewModel.scrollCounterThreshold.collectAsState()

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LockdownWindow?>(null) }
    var pendingDelete by remember { mutableStateOf<LockdownWindow?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lockdown") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New window") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 12.dp, bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ActiveBanner(active)
            }
            item {
                BlockShortsCard(
                    enabled = blockShorts,
                    onToggle = { viewModel.setBlockShorts(it) }
                )
            }
            item {
                ScrollCounterCard(
                    enabled = scrollEnabled,
                    threshold = scrollThreshold,
                    onToggle = { viewModel.setScrollCounterEnabled(it) },
                    onThresholdChange = { viewModel.setScrollCounterThreshold(it) }
                )
            }
            item {
                Text(
                    "Time windows",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            if (windows.isEmpty()) {
                item { EmptyWindowsCard() }
            } else {
                items(windows, key = { it.id }) { w ->
                    WindowCard(
                        window = w,
                        isActive = active?.id == w.id,
                        onToggle = { viewModel.setEnabled(w.id, it) },
                        onEdit = { editing = w; editorOpen = true },
                        onDelete = { pendingDelete = w }
                    )
                }
            }
        }
    }

    if (editorOpen) {
        WindowEditorDialog(
            initial = editing,
            onDismiss = { editorOpen = false; editing = null },
            onSave = {
                viewModel.upsert(it)
                editorOpen = false
                editing = null
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Delete window?") },
            text = {
                Text(
                    "“${target.displayLabel()}” " +
                        "(${formatTime(target.startTime)}–${formatTime(target.endTime)}) " +
                        "will be removed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(target.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActiveBanner(active: LockdownWindow?) {
    val container = if (active != null)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (active != null)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (active != null) Icons.Outlined.Lock else Icons.Outlined.Shield,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (active != null) "Lockdown active" else "Not in lockdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (active != null)
                        "${active.displayLabel()} until ${formatTime(active.endTime)}. " +
                            "All YouTube videos will be blocked."
                    else
                        "When inside a window below, every YouTube video is blocked " +
                            "— even ones from allowed channels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
        }
    }
}

@Composable
private fun BlockShortsCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Block Shorts entirely",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Every YouTube Short is blocked, regardless of channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun ScrollCounterCard(
    enabled: Boolean,
    threshold: Int,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Scroll counter",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "A floating pill counts your scrolls each time you " +
                            "open YouTube. A nudge fires when you cross the " +
                            "threshold.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nudge after $threshold scrolls",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { onThresholdChange(it.toInt()) },
                    valueRange = MIN_SCROLL_THRESHOLD.toFloat()..
                        MAX_SCROLL_THRESHOLD.toFloat(),
                    steps = (MAX_SCROLL_THRESHOLD - MIN_SCROLL_THRESHOLD) - 1
                )
                Text(
                    "Range: $MIN_SCROLL_THRESHOLD–$MAX_SCROLL_THRESHOLD. " +
                        "Repeat nudges fire at every multiple of $threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyWindowsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No windows yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap “New window” to schedule a period during which all YouTube " +
                    "videos are blocked. Useful for sleep hours or work hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WindowCard(
    window: LockdownWindow,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        window.displayLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatTime(window.startTime)} – ${formatTime(window.endTime)}" +
                            if (window.crossesMidnight) " (next day)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = window.enabled,
                    onCheckedChange = onToggle
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (iso in 1..7) {
                    val on = iso in window.daysOfWeek
                    val container = if (on)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                    val onContainer = if (on)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        color = container,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            LockdownWindow.dayName(iso).first().toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainer
                        )
                    }
                }
            }

            if (isActive) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Active now") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledLeadingIconContentColor =
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) { Text("Edit") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowEditorDialog(
    initial: LockdownWindow?,
    onDismiss: () -> Unit,
    onSave: (LockdownWindow) -> Unit
) {
    val isEdit = initial != null
    val baseStart = initial?.startMinuteOfDay ?: (23 * 60)
    val baseEnd = initial?.endMinuteOfDay ?: (7 * 60)

    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var days by remember { mutableStateOf(initial?.daysOfWeek ?: LockdownWindow.ALL_DAYS) }

    val startState = rememberTimePickerState(
        initialHour = baseStart / 60,
        initialMinute = baseStart % 60,
        is24Hour = false
    )
    val endState = rememberTimePickerState(
        initialHour = baseEnd / 60,
        initialMinute = baseEnd % 60,
        is24Hour = false
    )
    var pickingStart by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = { Text(if (isEdit) "Edit window" else "New lockdown window") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("Sleep") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimeFieldChip(
                        label = "Start",
                        time = LocalTime.of(startState.hour, startState.minute),
                        selected = pickingStart,
                        onClick = { pickingStart = true },
                        modifier = Modifier.weight(1f)
                    )
                    TimeFieldChip(
                        label = "End",
                        time = LocalTime.of(endState.hour, endState.minute),
                        selected = !pickingStart,
                        onClick = { pickingStart = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (pickingStart) TimePicker(state = startState)
                    else TimePicker(state = endState)
                }

                Text(
                    "Repeats on",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (iso in 1..7) {
                        FilterChip(
                            selected = iso in days,
                            onClick = {
                                days = if (iso in days) days - iso else days + iso
                            },
                            label = {
                                Text(LockdownWindow.dayName(iso).take(2))
                            },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }

                val sM = startState.hour * 60 + startState.minute
                val eM = endState.hour * 60 + endState.minute
                if (eM <= sM) {
                    Text(
                        "Crosses midnight — ends the next morning at " +
                            "${formatTime(LocalTime.of(endState.hour, endState.minute))}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = startState.hour * 60 + startState.minute
                val end = endState.hour * 60 + endState.minute
                val saved = (initial ?: LockdownWindow(
                    startMinuteOfDay = start,
                    endMinuteOfDay = end
                )).copy(
                    label = label.trim(),
                    startMinuteOfDay = start,
                    endMinuteOfDay = end,
                    daysOfWeek = days.ifEmpty { LockdownWindow.ALL_DAYS }
                )
                onSave(saved)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TimeFieldChip(
    label: String,
    time: LocalTime,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = onContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatTime(time),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun LockdownWindow.displayLabel(): String =
    label.ifBlank {
        if (crossesMidnight) "Sleep" else "Lockdown"
    }

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

internal fun formatTime(time: LocalTime): String = time.format(TIME_FORMATTER)
