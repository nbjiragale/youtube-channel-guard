package com.ycg.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ycg.app.R

/**
 * Owns the full-screen "this channel isn't on your allow-list" overlay.
 *
 * Implementation lives outside the AccessibilityService so it can be unit
 * tested with a fake Context, and so the service file stays focused on
 * detection logic.
 */
class BlockOverlayManager(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile
    private var overlay: View? = null

    fun isShowing(): Boolean = overlay != null

    fun show(channelName: String) {
        main.post {
            if (overlay != null) {
                updateChannelText(channelName)
                return@post
            }
            val view = buildOverlay(channelName)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            try {
                wm.addView(view, params)
                overlay = view
            } catch (e: Exception) {
                // Most likely SYSTEM_ALERT_WINDOW not granted. Silently ignore;
                // we still fall back to performing GLOBAL_ACTION_BACK in the
                // service.
            }
        }
    }

    fun hide() {
        main.post {
            val v = overlay ?: return@post
            try {
                wm.removeView(v)
            } catch (_: Exception) { /* already detached */ }
            overlay = null
        }
    }

    private fun updateChannelText(channelName: String) {
        val v = overlay ?: return
        val tv = v.findViewById<TextView>(android.R.id.message) ?: return
        tv.text = context.getString(R.string.block_subtitle) + "\n\n" + channelName
    }

    private fun buildOverlay(channelName: String): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(235, 10, 10, 10))
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.block_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }

        val subtitle = TextView(context).apply {
            id = android.R.id.message
            text = context.getString(R.string.block_subtitle) + "\n\n" + channelName
            setTextColor(Color.argb(230, 240, 240, 240))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }

        container.addView(title)
        container.addView(subtitle)
        return container
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
