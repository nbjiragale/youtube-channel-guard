package com.ycg.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ycg.app.ui.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val lastDetected by viewModel.lastDetected.collectAsState()
    var newChannel by remember { mutableStateOf("") }

    var accessibilityEnabled by remember {
        mutableStateOf(isAccessibilityEnabled(context))
    }
    var notificationsEnabled by remember {
        mutableStateOf(areNotificationsEnabled(context))
    }
    var overlayEnabled by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityEnabled(context)
                notificationsEnabled = areNotificationsEnabled(context)
                overlayEnabled = Settings.canDrawOverlays(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Channel Guard") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                accessibilityEnabled = accessibilityEnabled,
                overlayEnabled = overlayEnabled,
                notificationsEnabled = notificationsEnabled,
                onOpenAccessibility = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onOpenOverlay = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onOpenAppDetails = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            LastDetectedCard(
                lastDetected = lastDetected?.name,
                alreadyAllowed = lastDetected?.let { ld ->
                    channels.any {
                        com.ycg.app.data.AllowListMatcher.normalize(it) ==
                            com.ycg.app.data.AllowListMatcher.normalize(ld.name)
                    }
                } ?: false,
                onApprove = { name -> viewModel.add(name) }
            )

            Text("Allowed channels", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newChannel,
                    onValueChange = { newChannel = it },
                    label = { Text("Channel name (e.g. MrBeast)") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    singleLine = true
                )
                SmallFloatingActionButton(onClick = {
                    val name = newChannel.trim()
                    if (name.isNotEmpty()) {
                        viewModel.add(name)
                        newChannel = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add channel")
                }
            }

            if (channels.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No channels yet. While the list is empty the guard is " +
                        "inactive — every video is allowed. Add at least one " +
                        "channel to start enforcing.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels.toList()) { ch ->
                        ChannelRow(name = ch, onDelete = { viewModel.remove(ch) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    notificationsEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAppDetails: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Status", style = MaterialTheme.typography.titleMedium)
            StatusLine("Accessibility access", accessibilityEnabled)
            StatusLine("Display over other apps", overlayEnabled)
            StatusLine("Notifications", notificationsEnabled)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAccessibility) { Text("Accessibility") }
                OutlinedButton(onClick = onOpenOverlay) { Text("Overlay") }
                OutlinedButton(onClick = onOpenAppDetails) { Text("App") }
            }
            if (!accessibilityEnabled) {
                Text(
                    "Accessibility access is required for the guard to work.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!overlayEnabled) {
                Text(
                    "Without 'Display over other apps' the OK/Allow modal " +
                        "can't be shown. The guard will still close " +
                        "disallowed videos silently (with a Toast) but you " +
                        "won't get the in-place dialog.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LastDetectedCard(
    lastDetected: String?,
    alreadyAllowed: Boolean,
    onApprove: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Last seen channel", style = MaterialTheme.typography.titleMedium)
            if (lastDetected.isNullOrBlank()) {
                Text(
                    "Open a YouTube video and come back — the channel name " +
                        "the guard sees will appear here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lastDetected,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (alreadyAllowed) {
                        Text(
                            "Already allowed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        TextButton(onClick = { onApprove(lastDetected) }) {
                            Text("Allow")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (ok) "● " else "○ ",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(label)
        Spacer(Modifier.weight(1f))
        Text(if (ok) "Granted" else "Not granted")
    }
}

@Composable
private fun ChannelRow(name: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove $name")
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = context.packageName +
        "/com.ycg.app.service.YouTubeAccessibilityService"
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager ?: return false
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    if (!am.isEnabled) return false
    val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
    while (splitter.hasNext()) {
        if (splitter.next().equals(expected, ignoreCase = true)) return true
    }
    return false
}

private fun areNotificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}
