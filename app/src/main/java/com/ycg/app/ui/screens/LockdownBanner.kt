package com.ycg.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ycg.app.data.LockdownWindow
import com.ycg.app.ui.LockdownViewModel
import java.time.LocalTime

/**
 * A thin error-coloured banner shown above the content of Feed and
 * Channels tabs whenever lockdown is currently active. Hides itself
 * (with a fade-out) when no window is active.
 */
@Composable
fun LockdownStatusBanner(
    modifier: Modifier = Modifier,
    viewModel: LockdownViewModel = viewModel()
) {
    val active by viewModel.activeWindow.collectAsState()
    AnimatedVisibility(
        visible = active != null,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        active?.let { Banner(it, modifier) }
    }
}

@Composable
private fun Banner(window: LockdownWindow, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.Top) {
                Text(
                    "Lockdown active",
                    style = MaterialTheme.typography.titleSmall
                )
                val labelText = window.label.ifBlank {
                    if (window.crossesMidnight) "Sleep" else "Lockdown"
                }
                val end = window.endAt(java.time.LocalDateTime.now()).toLocalTime()
                Text(
                    "$labelText until ${formatTime(end)} — all videos blocked",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Suppress("unused")
private fun previewKeep(): LocalTime = LocalTime.NOON
