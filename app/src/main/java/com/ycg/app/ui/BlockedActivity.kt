package com.ycg.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown over YouTube whenever the active video is from a channel that is not
 * on the allow-list.
 *
 * This is a real Activity, not a system overlay, on purpose — having it become
 * the foreground task is what backgrounds YouTube and stops playback. The
 * Accessibility Service additionally dispatches a media-pause key event right
 * before launching us so audio cuts immediately.
 *
 * On dismissal we send the user to the home launcher rather than back to the
 * previous task (which was YouTube). If they re-open YouTube and try the same
 * disallowed video, the service will simply launch us again.
 */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()

        // Force any back-press to land on the home launcher, never back into
        // the YouTube task — the whole point of the screen is to stop the
        // user from returning to the disallowed video.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            }
        )

        setContent {
            MaterialTheme {
                BlockedScreen(
                    channelName = channel,
                    onDismiss = { goHome() }
                )
            }
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(home)
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_CHANNEL = "channel"
    }
}

@Composable
private fun BlockedScreen(channelName: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Channel not allowed",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This video is from a channel that isn't on your allow-list.",
            color = Color(0xFFDDDDDD),
            fontSize = 16.sp
        )
        if (channelName.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                channelName,
                color = Color(0xFFFFC107),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDismiss) { Text("Got it") }
    }
}
