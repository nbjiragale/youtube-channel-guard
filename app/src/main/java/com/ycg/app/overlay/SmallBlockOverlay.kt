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
 * A small modal-style window centred on the screen showing
 *
 *   "Channel not allowed
 *    @ChannelName
 *    [Allow]   [OK]"
 *
 * Implementation notes
 * --------------------
 * Uses a `TYPE_APPLICATION_OVERLAY` window so it can be drawn on top of
 * YouTube. The window is *not* focusable — that way `performGlobalAction(
 * GLOBAL_ACTION_BACK)` in the AccessibilityService still goes to YouTube
 * (collapsing the watch page) instead of being intercepted here. Touch
 * events inside the card still work because focus and touch are independent
 * concepts on Android.
 *
 * No auto-dismiss. The overlay waits for the user to tap OK or Allow.
 *
 * If `Settings.canDrawOverlays(...)` is false the call to [show] silently
 * returns false and the caller falls back to a no-overlay "silent dismiss"
 * flow.
 */
class SmallBlockOverlay(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile private var view: View? = null
    @Volatile private var currentChannel: String? = null

    fun isShowing(): Boolean = view != null

    /**
     * @param onAllow user tapped *Allow* — add this channel to the allow-list
     *   and just hide the overlay.
     * @param onOk user tapped *OK* — close the disallowed video but stay in
     *   YouTube.
     * @return true if the overlay was successfully attached, false if the
     *   app does not have the SYSTEM_ALERT_WINDOW permission or attaching
     *   failed.
     */
    fun show(
        channelName: String,
        onAllow: (String) -> Unit,
        onOk: (String) -> Unit
    ): Boolean {
        var attached = false
        main.post {
            currentChannel = channelName

            // Already showing → just refresh the channel + handlers.
            view?.let { existing ->
                existing.findViewWithTag<TextView>(TAG_CHANNEL)?.text = channelName
                existing.findViewWithTag<Button>(TAG_ALLOW)?.setOnClickListener {
                    onAllow(channelName); hide()
                }
                existing.findViewWithTag<Button>(TAG_OK)?.setOnClickListener {
                    onOk(channelName); hide()
                }
                attached = true
                return@post
            }

            val v = buildModal(channelName, onAllow, onOk)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            try {
                wm.addView(v, params)
                view = v
                attached = true
            } catch (_: Exception) {
                view = null
            }
        }
        return attached
    }

    fun hide() {
        main.post {
            val v = view ?: return@post
            try { wm.removeView(v) } catch (_: Exception) { /* already detached */ }
            view = null
            currentChannel = null
        }
    }

    // -------------------------------------------------------------------
    // View construction.
    // -------------------------------------------------------------------

    private fun buildModal(
        channelName: String,
        onAllow: (String) -> Unit,
        onOk: (String) -> Unit
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 22, 22, 22))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(120, 255, 199, 0))
            }
            setPadding(dp(20), dp(18), dp(20), dp(14))
            minimumWidth = dp(280)
        }

        val title = TextView(context).apply {
            text = "Channel not allowed"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val channel = TextView(context).apply {
            tag = TAG_CHANNEL
            text = channelName
            setTextColor(Color.argb(255, 255, 199, 0))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(8), 0, dp(14))
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val allowBtn = Button(context).apply {
            tag = TAG_ALLOW
            text = "Allow"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(180, 255, 255, 255))
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { onAllow(channelName); hide() }
        }

        val gap = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), 1)
        }

        val okBtn = Button(context).apply {
            tag = TAG_OK
            text = "OK"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.argb(255, 255, 199, 0))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(20), dp(6), dp(20), dp(6))
            setOnClickListener { onOk(channelName); hide() }
        }

        buttonRow.addView(allowBtn)
        buttonRow.addView(gap)
        buttonRow.addView(okBtn)

        card.addView(title)
        card.addView(channel)
        card.addView(buttonRow)
        return card
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG_CHANNEL = "ycg_overlay_channel"
        private const val TAG_ALLOW = "ycg_overlay_allow"
        private const val TAG_OK = "ycg_overlay_ok"
    }
}
