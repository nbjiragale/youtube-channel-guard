package com.ycg.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ycg.app.data.AllowListMatcher
import com.ycg.app.ui.HomeViewModel

private data class PermissionState(
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean
) {
    val allGranted: Boolean get() = accessibility && overlay && notifications
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val lastDetected by viewModel.lastDetected.collectAsState()

    var permissions by remember {
        mutableStateOf(readPermissionState(context))
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = readPermissionState(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Channel Guard",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = !permissions.allGranted,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SetupCard(
                        permissions = permissions,
                        onOpenAccessibility = { openAccessibility(context) },
                        onOpenOverlay = { openOverlaySettings(context) },
                        onOpenAppDetails = { openAppDetails(context) }
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = permissions.allGranted,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    GuardActiveBanner(channelCount = channels.size)
                }
            }

            item {
                LastDetectedCard(
                    detectedName = lastDetected?.name,
                    alreadyAllowed = lastDetected?.let { ld ->
                        channels.any {
                            AllowListMatcher.normalize(it) ==
                                AllowListMatcher.normalize(ld.name)
                        }
                    } ?: false,
                    onApprove = { name -> viewModel.add(name) }
                )
            }

            item {
                SectionHeader(
                    title = "Allowed channels",
                    counter = if (channels.isEmpty()) null else channels.size.toString()
                )
            }

            item {
                AddChannelRow(onAdd = { name -> viewModel.add(name) })
            }

            if (channels.isEmpty()) {
                item { ChannelsEmptyState() }
            } else {
                items(channels.toList(), key = { it }) { name ->
                    ChannelRow(
                        name = name,
                        onDelete = { pendingDelete = name }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Remove channel?") },
            text = {
                Text(
                    "“$target” will no longer be on the allow-list. " +
                        "Videos from this channel will be blocked again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(target); pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------------
// Setup / permission card.
// -------------------------------------------------------------------

@Composable
private fun SetupCard(
    permissions: PermissionState,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAppDetails: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Setup needed",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Grant the permissions below so Channel Guard can " +
                    "watch YouTube and block disallowed videos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            PermissionLine(
                icon = Icons.Outlined.Accessibility,
                label = "Accessibility access",
                description = "Required. Lets Channel Guard read YouTube's screen to identify channels.",
                granted = permissions.accessibility,
                ctaLabel = "Open settings",
                onCta = onOpenAccessibility,
                emphasis = !permissions.accessibility
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            PermissionLine(
                icon = Icons.Outlined.Layers,
                label = "Display over other apps",
                description = "Required for the in-place block dialog with OK / Allow buttons.",
                granted = permissions.overlay,
                ctaLabel = "Allow",
                onCta = onOpenOverlay,
                emphasis = !permissions.overlay
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            PermissionLine(
                icon = Icons.Outlined.Notifications,
                label = "Notifications",
                description = "Lets the persistent “guard active” notification show.",
                granted = permissions.notifications,
                ctaLabel = "App settings",
                onCta = onOpenAppDetails,
                emphasis = false
            )
        }
    }
}

@Composable
private fun PermissionLine(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    ctaLabel: String,
    onCta: () -> Unit,
    emphasis: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(granted = granted)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            // No CTA — already done.
        } else if (emphasis) {
            FilledTonalButton(onClick = onCta) { Text(ctaLabel) }
        } else {
            OutlinedButton(onClick = onCta) { Text(ctaLabel) }
        }
    }
}

@Composable
private fun StatusPill(granted: Boolean) {
    if (granted) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Granted") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    } else {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Action needed") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        )
    }
}

@Composable
private fun GuardActiveBanner(channelCount: Int) {
    val (title, body) = if (channelCount == 0) {
        "Guard is inactive" to "Add at least one channel below to start enforcing."
    } else {
        "Guard active" to "Watching YouTube. " +
            "$channelCount channel${if (channelCount == 1) "" else "s"} on the allow-list."
    }
    val container = if (channelCount == 0)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (channelCount == 0)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
        }
    }
}

// -------------------------------------------------------------------
// Last detected card.
// -------------------------------------------------------------------

@Composable
private fun LastDetectedCard(
    detectedName: String?,
    alreadyAllowed: Boolean,
    onApprove: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "LAST DETECTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            if (detectedName.isNullOrBlank()) {
                Text(
                    "No channel seen yet — open YouTube and play a video. " +
                        "Whatever the guard reads will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        detectedName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (alreadyAllowed) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Allowed") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    } else {
                        FilledTonalButton(onClick = { onApprove(detectedName) }) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Allow")
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------
// Section header.
// -------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, counter: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (counter != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    counter,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// -------------------------------------------------------------------
// Add channel row.
// -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelRow(onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        val name = input.trim()
        if (name.isNotEmpty()) {
            onAdd(name)
            input = ""
            keyboard?.hide()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Add channel") },
            placeholder = { Text("e.g. MrBeast or @MrBeast") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { input = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear")
                    }
                }
            }
        )
        FilledIconButton(
            onClick = { submit() },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add channel")
        }
    }
}

// -------------------------------------------------------------------
// Channel list.
// -------------------------------------------------------------------

@Composable
private fun ChannelRow(name: String, onDelete: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelAvatar(name = name)
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(name: String) {
    val initial = name.trim()
        .removePrefix("@")
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {}
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChannelsEmptyState() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Guard is inactive",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "While the allow-list is empty, every video is allowed. " +
                    "Add at least one channel above to start enforcing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------------
// Permission probes & deep-links.
// -------------------------------------------------------------------

private fun readPermissionState(context: Context): PermissionState =
    PermissionState(
        accessibility = isAccessibilityEnabled(context),
        overlay = Settings.canDrawOverlays(context),
        notifications = areNotificationsEnabled(context)
    )

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

private fun openAccessibility(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
