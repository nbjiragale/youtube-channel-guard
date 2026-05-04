package com.ycg.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-starts the foreground status notification after a reboot. The
 * Accessibility Service itself is started by the system once the user has
 * granted the permission, so we don't need to bind it here.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val svc = Intent(context, GuardForegroundService::class.java)
        context.startForegroundService(svc)
    }
}
