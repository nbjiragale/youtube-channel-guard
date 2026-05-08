package com.ycg.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ycg.app.data.AllowListMatcher
import com.ycg.app.data.AllowedChannel
import com.ycg.app.ui.HomeViewModel

private data class PermissionState(
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean
) {
    val allGranted: Boolean
        get() = accessibility && overlay && notifications
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val lastDetected by viewModel.lastDetected.collectAsState()
    val addState by viewModel.addState.collectAsState()

    var permissions by remember { mutableStateOf(readPermissionState(context)) }
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

    var pendingDelete by remember { mutableStateOf<AllowedChannel?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(addState) {
        when (val s = addState) {
            HomeViewModel.AddState.MissingApiKey -> {
                snackbar.showSnackbar(
                    "Channel saved. API key not set, so it won't be resolved or " +
                        "appear in the feed yet."
                )
                viewModel.ackAddState()
            }
            HomeViewModel.AddState.NotFound -> {
                snackbar.showSnackbar(
                    "Couldn't find that channel on YouTube. Try the @handle, " +
                        "e.g. @MrBeast."
                )
                viewModel.ackAddState()
            }
            is HomeViewModel.AddState.Failed -> {
                snackbar.showSnackbar("Lookup failed: ${s.message}")
                viewModel.ackAddState()
            }
            is HomeViewModel.AddState.Added -> {
                snackbar.showSnackbar("Added ${s.channel.label}")
                viewModel.ackAddState()
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Channels") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 12.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (permissions.allGranted) {
                    GuardStatusBanner(
                        channelCount = channels.size,
                        hasApiKey = viewModel.hasApiKey
                    )
                } else {
                    SetupCard(
                        permissions = permissions,
                        onOpenAccessibility = { openAccessibility(context) },
                        onOpenOverlay = { openOverlaySettings(context) },
                        onOpenAppDetails = { openAppDetails(context) }
                    )
                }
            }

            item {
                LastDetectedCard(
                    detectedName = lastDetected?.name,
                    alreadyAllowed = lastDetected?.let { ld ->
                        isOnAllowList(ld.name, channels)
                    } ?: false,
                    isResolving = addState is HomeViewModel.AddState.Resolving,
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
                AddChannelRow(
                    isResolving = addState is HomeViewModel.AddState.Resolving,
                    onAdd = { name -> viewModel.add(name) }
                )
            }

            if (channels.isEmpty()) {
                item { ChannelsEmptyState() }
            } else {
                items(channels, key = { it.originalInput.ifBlank { it.channelId } }) { ch ->
                    ChannelRow(
                        channel = ch,
                        onDelete = { pendingDelete = ch }
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Remove channel?") },
            text = {
                Text(
                    "“${target.label}” will no longer be on the allow-list. " +
                        "Videos from this channel will be blocked again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(target.originalInput)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun isOnAllowList(detectedName: String, channels: List<AllowedChannel>): Boolean {
    val needle = AllowListMatcher.normalize(detectedName) ?: return false
    return channels.any { ch ->
        AllowListMatcher.normalize(ch.originalInput) == needle ||
            AllowListMatcher.normalize(ch.displayName) == needle ||
            AllowListMatcher.normalize(ch.handle) == needle
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Setup needed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Grant the permissions below so Channel Guard can watch " +
                    "YouTube and block disallowed videos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionLine(
                icon = Icons.Outlined.Accessibility,
                label = "Accessibility access",
                description = "Required. Lets Channel Guard read YouTube's screen to identify channels.",
                granted = permissions.accessibility,
                ctaLabel = "Open settings",
                onCta = onOpenAccessibility,
                emphasis = !permissions.accessibility
            )
            PermissionLine(
                icon = Icons.Outlined.Layers,
                label = "Display over other apps",
                description = "Required for the in-place block dialog with OK / Allow buttons.",
                granted = permissions.overlay,
                ctaLabel = "Allow",
                onCta = onOpenOverlay,
                emphasis = !permissions.overlay
            )
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
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (granted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(granted = granted)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                if (emphasis) {
                    FilledTonalButton(
                        onClick = onCta,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(ctaLabel) }
                } else {
                    OutlinedButton(
                        onClick = onCta,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(ctaLabel) }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(granted: Boolean) {
    val tokens = if (granted) {
        StatusPillTokens(
            label = "Granted",
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Outlined.CheckCircle
        )
    } else {
        StatusPillTokens(
            label = "Action needed",
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Outlined.ErrorOutline
        )
    }
    Surface(
        color = tokens.container,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                tokens.icon,
                contentDescription = null,
                tint = tokens.onContainer,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                tokens.label,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.onContainer
            )
        }
    }
}

private data class StatusPillTokens(
    val label: String,
    val container: androidx.compose.ui.graphics.Color,
    val onContainer: androidx.compose.ui.graphics.Color,
    val icon: ImageVector
)

@Composable
private fun GuardStatusBanner(channelCount: Int, hasApiKey: Boolean) {
    val (title, body) = when {
        channelCount == 0 ->
            "Guard is inactive" to "Add at least one channel below to start enforcing."
        !hasApiKey ->
            "Guard active (limited)" to "Watching YouTube. " +
                "$channelCount channel${if (channelCount == 1) "" else "s"} on the " +
                "allow-list. Add an API key to populate the Feed tab."
        else ->
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
                Spacer(Modifier.height(2.dp))
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
    isResolving: Boolean,
    onApprove: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "LAST DETECTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (detectedName.isNullOrBlank()) {
                Text(
                    "No channel seen yet — open YouTube and play a video. " +
                        "Whatever the guard reads will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    detectedName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (alreadyAllowed) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Already allowed") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor =
                                MaterialTheme.colorScheme.primaryContainer,
                            disabledLabelColor =
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledLeadingIconContentColor =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                } else {
                    FilledTonalButton(
                        onClick = { onApprove(detectedName) },
                        enabled = !isResolving
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Allow this channel")
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (counter != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    counter,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
private fun AddChannelRow(
    isResolving: Boolean,
    onAdd: (String) -> Unit
) {
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
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Add channel") },
            placeholder = { Text("@MrBeast") },
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
        Spacer(Modifier.width(12.dp))
        FilledIconButton(
            onClick = { submit() },
            enabled = !isResolving,
            modifier = Modifier.size(56.dp)
        ) {
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Outlined.Add, contentDescription = "Add channel")
            }
        }
    }
}

// -------------------------------------------------------------------
// Channel list.
// -------------------------------------------------------------------

@Composable
private fun ChannelRow(channel: AllowedChannel, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelAvatar(channel = channel)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.handle.isNotBlank() &&
                    !channel.handle.equals(channel.label, ignoreCase = true)
                ) {
                    Text(
                        channel.handle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!channel.isResolved) {
                    Text(
                        "Not resolved yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove ${channel.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(channel: AllowedChannel) {
    val initial = channel.label.trim()
        .removePrefix("@")
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString().orEmpty()
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(40.dp)
    ) {
        if (channel.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ChannelsEmptyState() {
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
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No channels yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
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
