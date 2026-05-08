package com.ycg.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Tab(val label: String, val icon: ImageVector) {
    Feed("Feed", Icons.Outlined.PlayCircleOutline),
    Channels("Channels", Icons.Outlined.Tune)
}

@Composable
fun MainScreen() {
    var selected by rememberSaveable { mutableStateOf(Tab.Feed) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        // Each screen has its own Scaffold with its own top bar; the
        // bottomBar inset is forwarded so content above the nav bar.
        when (selected) {
            Tab.Feed -> Modifier.padding(padding).let {
                androidx.compose.foundation.layout.Box(modifier = it.fillMaxSize()) {
                    FeedScreen()
                }
            }
            Tab.Channels -> Modifier.padding(padding).let {
                androidx.compose.foundation.layout.Box(modifier = it.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}
