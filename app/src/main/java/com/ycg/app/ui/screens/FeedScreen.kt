package com.ycg.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WifiTetheringError
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ycg.app.data.FeedVideo
import com.ycg.app.data.Format
import com.ycg.app.ui.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: FeedViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state == FeedViewModel.State.Idle) viewModel.refresh()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Feed") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LockdownStatusBanner()
            FeedBody(
                state = state,
                onOpen = { openInYouTube(context, it.videoId, it.watchUrl) },
                onRetry = { viewModel.refresh() }
            )
        }
    }
}

@Composable
private fun ColumnScope.FeedBody(
    state: FeedViewModel.State,
    onOpen: (FeedVideo) -> Unit,
    onRetry: () -> Unit
) {
    when (val s = state) {
        FeedViewModel.State.Loading,
        FeedViewModel.State.Idle -> CenteredSpinner()

        is FeedViewModel.State.Loaded -> {
            if (s.videos.isEmpty()) {
                EmptyMessage(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Outlined.Shield,
                    title = "No videos yet",
                    body = "Your allowed channels haven't uploaded anything " +
                        "we can see. Pull to refresh."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 12.dp, bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(s.videos, key = { it.videoId }) { video ->
                        VideoCard(video = video, onClick = { onOpen(video) })
                    }
                }
            }
        }

        is FeedViewModel.State.Empty -> when (s.reason) {
            FeedViewModel.State.Reason.NoApiKey -> EmptyMessage(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.WifiTetheringError,
                title = "API key not configured",
                body = "Add your YouTube Data API key to local.properties as " +
                    "youtube.api.key=YOUR_KEY and rebuild. The feed needs the " +
                    "API to fetch uploads from your allowed channels.",
                cta = null
            )
            FeedViewModel.State.Reason.NoChannels -> EmptyMessage(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.Shield,
                title = "No channels yet",
                body = "Add at least one channel on the Channels tab and the " +
                    "feed will populate with their latest uploads.",
                cta = null
            )
            FeedViewModel.State.Reason.AllUnresolved -> EmptyMessage(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Outlined.WifiTetheringError,
                title = "Couldn't look up your channels",
                body = "We have channels in your list, but resolving them via " +
                    "the YouTube API hasn't worked. Check your API key and " +
                    "internet connection, then refresh.",
                cta = "Retry" to onRetry
            )
        }

        is FeedViewModel.State.Error -> EmptyMessage(
            modifier = Modifier.fillMaxSize(),
            icon = Icons.Outlined.WifiTetheringError,
            title = "Couldn't refresh feed",
            body = s.message,
            cta = "Retry" to onRetry
        )
    }
}

@Composable
private fun CenteredSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyMessage(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    cta: Pair<String, () -> Unit>? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (cta != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = cta.second) { Text(cta.first) }
        }
    }
}

@Composable
private fun VideoCard(video: FeedVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
            val duration = Format.duration(video.durationIso)
            if (duration.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        duration,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            ChannelAvatarSmall(
                avatarUrl = video.channelAvatarUrl,
                fallbackInitial = video.channelTitle.firstOrNull()?.toString().orEmpty()
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(video.channelTitle)
                        val rel = Format.relativeTime(video.publishedAt)
                        if (rel.isNotBlank()) {
                            append("  •  ")
                            append(rel)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatarSmall(avatarUrl: String, fallbackInitial: String) {
    val size = 36.dp
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(size)
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    fallbackInitial.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun openInYouTube(
    context: android.content.Context,
    videoId: String,
    fallbackUrl: String
) {
    val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
        .setPackage("com.google.android.youtube")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(ytIntent)
        return
    } catch (_: Throwable) { /* fall through */ }

    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) { /* swallow */ }
}
