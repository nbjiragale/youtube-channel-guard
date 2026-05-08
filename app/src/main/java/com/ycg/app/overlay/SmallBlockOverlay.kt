package com.ycg.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A small toast-like banner that floats at the top of the screen telling the
 * user the current video was blocked, and offering a one-tap "Allow" button
 * that adds that channel to the allow-list.
 *
 * Implementation
 * --------------
 * Uses a `TYPE_APPLICATION_OVERLAY` window so it can be shown on top of
 * YouTube. Auto-dismisses after [VISIBLE_DURATION_MS]. The "Allow" tap fires
 * [onAllow] and dismisses immediately.
 *
 * If `Settings.canDrawOverlays(...)` is false the call to [show] silently
 * returns false, letting the caller fall back to a different blocking
 * strategy (full-screen activity, etc.).
 */
class SmallBlockOverlay(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile private var view: View? = null
    private var dismissRunnable: Runnable? = null

    fun isShowing(): Boolean = view != null

    /**
     * @return true if the overlay was successfully attached, false if the app
     *   does not have the SYSTEM_ALERT_WINDOW permission or attaching failed.
     */
    fun show(channelName: String, onAllow: (String) -> Unit): Boolean {
        var attached = false
        main.post {
            // Already showing → just update the channel text + reset dismiss timer.
            view?.let { existing ->
                (existing.findViewWithTag<TextView>(TAG_BODY))?.text = bodyText(channelName)
                (existing.findViewWithTag<Button>(TAG_ALLOW))?.setOnClickListener {
                    onAllow(channelName); hide()
                }
                rescheduleDismiss()
                attached = true
                return@post
            }

            val v = buildBanner(channelName, onAllow)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(48)
            }
            try {
                wm.addView(v, params)
                view = v
                rescheduleDismiss()
                attached = true
            } catch (_: Exception) {
                // Permission missing or surface unavailable. Caller falls back.
                view = null
            }
        }
        return attached
    }

    fun hide() {
        main.post {
            cancelDismiss()
            val v = view ?: return@post
            try { wm.removeView(v) } catch (_: Exception) { /* already detached */ }
            view = null
        }
    }

    private fun rescheduleDismiss() {
        cancelDismiss()
        val r = Runnable { hide() }
        dismissRunnable = r
        main.postDelayed(r, VISIBLE_DURATION_MS)
    }

    private fun cancelDismiss() {
        dismissRunnable?.let { main.removeCallbacks(it) }
        dismissRunnable = null
    }

    // -------------------------------------------------------------------
    // View construction.
    // -------------------------------------------------------------------

    private fun buildBanner(channelName: String, onAllow: (String) -> Unit): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 24, 24, 24))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), dp(10), dp(10), dp(10))
        }

        val bodyView = TextView(context).apply {
            tag = TAG_BODY
            text = bodyText(channelName)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxWidth = dp(220)
        }

        val allowBtn = Button(context).apply {
            tag = TAG_ALLOW
            text = "Allow"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.argb(255, 255, 199, 0))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setOnClickListener { onAllow(channelName); hide() }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), 1)
        }

        container.addView(bodyView)
        container.addView(spacer)
        container.addView(allowBtn)
        return container
    }

    private fun bodyText(channelName: String): String =
        "Blocked: $channelName"

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val VISIBLE_DURATION_MS = 4_000L
        private const val TAG_BODY = "ycg_overlay_body"
        private const val TAG_ALLOW = "ycg_overlay_allow"
    }
}
