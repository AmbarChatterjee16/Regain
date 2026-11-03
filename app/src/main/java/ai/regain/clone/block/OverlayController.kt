package ai.regain.clone.block

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full screen "focus wall" drawn above whatever app is open. Implemented with classic
 * Views on purpose: it must be inflatable from a Service without an Activity lifecycle.
 */
object OverlayController {

    private var windowManager: WindowManager? = null
    private var root: View? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    var currentKey: String? = null
        private set
    var isShowing: Boolean = false
        private set

    private var lastShownAt = 0L
    private var lastKey: String? = null

    fun show(
        context: Context,
        key: String,
        title: String,
        message: String,
        chip: String,
        label: String?,
        accentColor: Int,
        allowBypass: Boolean,
        onLeave: () -> Unit,
        onBypass: (() -> Unit)? = null
    ) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post {
                show(context, key, title, message, chip, label, accentColor, allowBypass, onLeave, onBypass)
            }
            return
        }
        val now = System.currentTimeMillis()
        if (root != null && key == lastKey) {
            lastShownAt = now
            return
        }
        hide()

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xF70A0E1A.toInt())
            setPadding(dp(26), dp(30), dp(26), dp(28))
            isClickable = true
            isFocusable = true
            alpha = 0f
            setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
        }

        val topSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 0.9f)
        }
        container.addView(topSpacer)

        val glyph = TextView(context).apply {
            text = if (accentColor == 0xFFC8F560.toInt()) "🛡" else "⏳"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f)
            gravity = Gravity.CENTER
        }
        container.addView(glyph)

        val chipView = TextView(context).apply {
            text = chip.uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.18f
            setTextColor(accentColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(100).toFloat()
                setColor(Color.argb(28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
                setStroke(dp(1), Color.argb(90, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        }
        container.addView(chipView)

        if (!label.isNullOrBlank()) {
            val labelView = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.parseColor("#7E8AA0"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(18) }
            }
            container.addView(labelView)
        }

        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        container.addView(titleView)

        val messageView = TextView(context).apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#AAB4C6"))
            gravity = Gravity.CENTER
            setLineSpacing(dp(5).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        container.addView(messageView)

        val button = TextView(context).apply {
            text = "Back to what matters"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#0A0E1A"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(17), dp(22), dp(17))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(accentColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
            setOnClickListener {
                hide()
                onLeave()
            }
        }
        container.addView(button)

        if (allowBypass && onBypass != null) {
            val bypass = TextView(context).apply {
                text = "Need in for 5 minutes? Unlock temporarily"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#6F7A8D"))
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
                setOnClickListener {
                    hide()
                    onBypass()
                }
            }
            container.addView(bypass)
        }

        val bottomSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 0, 1f)
        }
        container.addView(bottomSpacer)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        @SuppressLint("InlinedApi")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            android.graphics.PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching {
            wm.addView(container, params)
            windowManager = wm
            root = container
            isShowing = true
            currentKey = key
            lastShownAt = now
            lastKey = key
            container.animate().alpha(1f).setDuration(160).start()
        }.onFailure { error ->
            android.util.Log.w("RegainGuard", "overlay add failed: ${error.message}", error)
            root = null
            isShowing = false
            currentKey = null
        }
    }

    fun hide() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        val v = root
        val wm = windowManager
        root = null
        windowManager = null
        isShowing = false
        currentKey = null
        if (v != null) {
            runCatching {
                v.animate().alpha(0f).setDuration(110).withEndAction {
                    runCatching { wm?.removeView(v) }
                }.start()
            }.onFailure { runCatching { wm?.removeView(v) } }
        }
    }

    fun hideIf(prefix: String) {
        val key = currentKey
        if (key != null && key.startsWith(prefix)) hide()
    }
}
