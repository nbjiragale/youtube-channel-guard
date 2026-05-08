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
 * A small modal-style window centred on the screen.
 *
 * Two variants:
 * - **Channel block** (`show`): "Channel not allowed / @ChannelName / [Allow] [OK]"
 * - **Lockdown** (`showLockdown`): "Lockdown active / <label> until 7:00 AM / [OK]"
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
 * No auto-dismiss. The overlay waits for the user to tap a button.
 *
 * If `Settings.canDrawOverlays(...)` is false the call to `show*` silently
 * returns false and the caller falls back to a no-overlay "silent dismiss"
 * flow.
 */
class SmallBlockOverlay(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile private var view: View? = null

    fun isShowing(): Boolean = view != null

    /**
     * Show the channel-block variant.
     *
     * @param onAllow user tapped *Allow* — add this channel to the allow-list
     *   and just hide the overlay.
     * @param onOk user tapped *OK* — close the disallowed video but stay in
     *   YouTube.
     */
    fun show(
        channelName: String,
        onAllow: (String) -> Unit,
        onOk: (String) -> Unit
    ): Boolean = attach { _ ->
        buildChannelBlock(channelName, onAllow, onOk)
    }

    /**
     * Show the lockdown-mode variant. No Allow button — by definition the
     * user has set the lockdown for themselves.
     *
     * @param subtitle e.g. "Sleep until 7:00 AM".
     * @param onOk user tapped OK — close the disallowed video.
     */
    fun showLockdown(
        title: String,
        subtitle: String,
        onOk: () -> Unit
    ): Boolean = attach { _ ->
        buildLockdown(title, subtitle, onOk)
    }

    fun hide() {
        main.post {
            val v = view ?: return@post
            try { wm.removeView(v) } catch (_: Exception) { /* already detached */ }
            view = null
        }
    }

    // -------------------------------------------------------------------
    // Window attach.
    // -------------------------------------------------------------------

    private fun attach(buildContent: (Context) -> View): Boolean {
        var attached = false
        main.post {
            // If something is already showing, swap it out with the new content.
            view?.let { existing ->
                try { wm.removeView(existing) } catch (_: Exception) { /* ignore */ }
                view = null
            }
            val v = buildContent(context)
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

    // -------------------------------------------------------------------
    // View construction.
    // -------------------------------------------------------------------

    private fun cardContainer(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 22, 22, 22))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(120, 255, 199, 0))
            }
            setPadding(dp(20), dp(18), dp(20), dp(14))
            minimumWidth = dp(280)
        }

    private fun buildChannelBlock(
        channelName: String,
        onAllow: (String) -> Unit,
        onOk: (String) -> Unit
    ): View {
        val card = cardContainer()

        val title = TextView(context).apply {
            text = "Channel not allowed"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val channel = TextView(context).apply {
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

    private fun buildLockdown(
        titleText: String,
        subtitleText: String,
        onOk: () -> Unit
    ): View {
        val card = cardContainer()

        val title = TextView(context).apply {
            text = titleText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(context).apply {
            text = subtitleText
            setTextColor(Color.argb(255, 255, 199, 0))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(8), 0, dp(14))
        }

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val okBtn = Button(context).apply {
            text = "OK"
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.argb(255, 255, 199, 0))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(20), dp(6), dp(20), dp(6))
            setOnClickListener { onOk(); hide() }
        }

        buttonRow.addView(okBtn)

        card.addView(title)
        card.addView(subtitle)
        card.addView(buttonRow)
        return card
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
