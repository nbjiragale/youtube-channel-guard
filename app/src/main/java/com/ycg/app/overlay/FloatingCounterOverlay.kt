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
import android.widget.FrameLayout
import android.widget.TextView

/**
 * A small persistent pill anchored to the top-right of the screen showing
 * the current scroll count for the active YouTube session.
 *
 * Uses TYPE_APPLICATION_OVERLAY (same as SmallBlockOverlay) and is
 * non-focusable so it doesn't intercept input. Tapping the pill calls the
 * `onTap` callback (we use that to "snooze" the counter for the rest of
 * the session — caller's responsibility).
 *
 * Caller owns the lifecycle: [show] → [update] zero or more times →
 * [hide]. Calling show() repeatedly with the same instance reuses the
 * existing window when possible.
 */
class FloatingCounterOverlay(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val wm: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile private var view: View? = null
    @Volatile private var label: TextView? = null
    @Volatile private var background: GradientDrawable? = null

    fun isShowing(): Boolean = view != null

    /**
     * Idempotent: showing while already shown is a no-op (use [update] to
     * change the count). Returns false if overlay permission isn't
     * granted.
     */
    fun show(initialCount: Int, threshold: Int, onTap: () -> Unit): Boolean {
        var attached = false
        main.post {
            if (view != null) return@post
            val (root, lbl, bg) = buildPill()
            root.setOnClickListener { onTap() }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(12)
                y = dp(72)
            }
            try {
                wm.addView(root, params)
                view = root
                label = lbl
                background = bg
                applyState(lbl, bg, initialCount, threshold)
                attached = true
            } catch (_: Exception) {
                view = null
                label = null
                background = null
            }
        }
        return attached
    }

    fun update(count: Int, threshold: Int) {
        main.post {
            val lbl = label ?: return@post
            val bg = background ?: return@post
            applyState(lbl, bg, count, threshold)
        }
    }

    fun hide() {
        main.post {
            val v = view ?: return@post
            try { wm.removeView(v) } catch (_: Exception) { /* already detached */ }
            view = null
            label = null
            background = null
        }
    }

    // -------------------------------------------------------------------
    // View construction.
    // -------------------------------------------------------------------

    private fun buildPill(): Triple<View, TextView, GradientDrawable> {
        val bg = GradientDrawable().apply {
            setColor(Color.argb(230, 22, 22, 22))
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.argb(80, 255, 255, 255))
        }

        val lbl = TextView(context).apply {
            text = "0"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val root = FrameLayout(context).apply {
            background = bg
            minimumWidth = dp(44)
            minimumHeight = dp(36)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            addView(lbl)
        }

        return Triple(root, lbl, bg)
    }

    private fun applyState(
        lbl: TextView,
        bg: GradientDrawable,
        count: Int,
        threshold: Int
    ) {
        lbl.text = count.toString()
        val warningWindow = (threshold - 5).coerceAtLeast(1)
        val (fill, stroke) = when {
            count >= threshold -> Color.argb(245, 200, 30, 30) to
                Color.argb(255, 255, 90, 90)
            count >= warningWindow -> Color.argb(240, 200, 130, 0) to
                Color.argb(255, 255, 199, 0)
            else -> Color.argb(230, 22, 22, 22) to
                Color.argb(80, 255, 255, 255)
        }
        bg.setColor(fill)
        bg.setStroke(dp(1), stroke)
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
