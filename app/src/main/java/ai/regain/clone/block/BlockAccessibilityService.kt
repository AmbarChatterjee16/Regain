package ai.regain.clone.block

import ai.regain.clone.data.Store
import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BlockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BlockAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingInspect: Runnable? = null
    private var lastPackage: String = ""
    private var lastInspectAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        OverlayController.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        OverlayController.hide()
        super.onDestroy()
    }

    override fun onInterrupt() {
        instance = null
    }

    /**
     * True when our overlay window is actually on screen. Android can attach the window
     * but force-hide it when the foreground app declared HIDE_OVERLAY_WINDOWS.
     */
    fun overlayVisible(): Boolean {
        if (!OverlayController.isShowing) return false
        return try {
            val list = windows
            if (list.isNullOrEmpty()) true
            else list.any { info ->
                info.root?.packageName?.toString() == packageName
            }
        } catch (t: Throwable) {
            // If the platform refuses to enumerate windows, don't bounce the user by mistake.
            true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) {
            // Our own overlay and activity generate events too. Never let those knock the wall down.
            return
        }

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != lastPackage) {
                    lastPackage = pkg
                    Enforcer.onForeground(this, pkg)
                }
                scheduleInspect(pkg, 220)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg != lastPackage) {
                    lastPackage = pkg
                    Enforcer.onForeground(this, pkg)
                }
                scheduleInspect(pkg, 420)
            }
        }
    }

    private fun scheduleInspect(pkg: String, delayMs: Long) {
        val settings = Store.settings
        if (!settings.blockingEnabled) return
        val needsContent = (pkg == Enforcer.YOUTUBE &&
            (settings.blockShorts || settings.youtubeMode == "allowlist" || settings.youtubeMode == "block_all")) ||
            (pkg == Enforcer.INSTAGRAM && settings.blockReels)
        if (!needsContent) return

        val now = System.currentTimeMillis()
        if (now - lastInspectAt < 250) return
        lastInspectAt = now

        pendingInspect?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            try {
                val root = rootInActiveWindow
                if (root == null) return@Runnable
                val verdict = Detector.inspect(root, pkg, Store.settings)
                if (verdict.block) {
                    Enforcer.onContent(this, pkg, verdict)
                } else {
                    Enforcer.onContentCleared()
                }
            } catch (t: Throwable) {
                Log.w("RegainBlocker", "inspect failed", t)
            }
        }
        pendingInspect = runnable
        handler.postDelayed(runnable, delayMs)
    }
}
