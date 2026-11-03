package ai.regain.clone.block

import ai.regain.clone.data.Store
import ai.regain.clone.focus.FocusEngine
import ai.regain.clone.focus.formatClock
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object Enforcer {

    private const val TAG = "RegainGuard"

    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    private const val PREFS = "regain_block"
    private val ACCENT_LIME = 0xFFC8F560.toInt()
    private val ACCENT_ROSE = 0xFFFF7CAB.toInt()
    private val ACCENT_AMBER = 0xFFFFD166.toInt()
    private val ACCENT_VIOLET = 0xFFB68CFF.toInt()
    private val ACCENT_BLUE = 0xFF7C9DFF.toInt()

    private val guardShown = ConcurrentHashMap<String, Long>()
    private val wallVerifier = Handler(Looper.getMainLooper())
    private var bypassUntil = 0L
    private var loaded = false

    private fun loadBypass(context: Context) {
        if (loaded) return
        loaded = true
        bypassUntil = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("bypass_until", 0L)
    }

    fun bypassFor(context: Context, minutes: Int) {
        bypassUntil = System.currentTimeMillis() + minutes * 60_000L
        loaded = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("bypass_until", bypassUntil).apply()
        OverlayController.hide()
    }

    fun isBypassed(context: Context): Boolean {
        loadBypass(context)
        return System.currentTimeMillis() < bypassUntil
    }

    fun bypassRemainingMinutes(context: Context): Int {
        loadBypass(context)
        val ms = bypassUntil - System.currentTimeMillis()
        return if (ms <= 0) 0 else ((ms + 59_999) / 60_000).toInt()
    }

    /** Called whenever a new foreground package is detected. */
    fun onForeground(context: Context, pkg: String) {
        if (pkg.isBlank()) return
        if (pkg == context.packageName) {
            // Regain itself. MainActivity.onResume clears the wall when the user opens the app.
            // Do not hide here: UsageStats can report stale resume events and knock the wall down.
            return
        }
        if (isLauncherOrSystem(context, pkg)) {
            OverlayController.hide()
            return
        }
        if (isBypassed(context)) {
            OverlayController.hide()
            return
        }
        val settings = Store.settings
        if (!settings.blockingEnabled) {
            OverlayController.hide()
            return
        }

        Log.d(TAG, "fg=$pkg enabled=${settings.blockingEnabled} blocked=${settings.blockedApps.contains(pkg)} yt=${settings.youtubeMode}")

        if (settings.blockedApps.contains(pkg)) {
            blockApp(context, pkg, "App blocked")
            return
        }

        if (pkg == YOUTUBE && settings.youtubeMode == "block_all") {
            blockApp(
                context, pkg, "YouTube is paused",
                message = "YouTube is switched off while Regain guards your focus. If you only need study channels, switch the allowlist on from the Focus Guard tab."
            )
            return
        }

        if (settings.strictMode && settings.lockSettingsDuringStrict && FocusEngine.isFocusRunning() && isSettings(pkg)) {
            OverlayController.show(
                context,
                key = "strict:settings",
                title = "Strict mode is on",
                message = "Settings stay locked until this focus session ends. That is the point — you already decided.",
                chip = "STRICT MODE",
                label = "Locked for ${formatClock(FocusEngine.state.value.remainingSeconds)}",
                accentColor = ACCENT_AMBER,
                allowBypass = false,
                onLeave = { goHome(context) }
            )
            return
        }

        OverlayController.hideIf("content:")
        maybeFocusGuard(context, pkg)
    }

    /**
     * Re-asserts app-level rules without touching content overlays. Called on every watchdog
     * tick so a wall survives accessibility reconnects or a dropped window.
     */
    fun enforceAppRules(context: Context, pkg: String) {
        if (pkg.isBlank() || pkg == context.packageName) return
        if (!Store.settings.blockingEnabled || isBypassed(context)) return
        val settings = Store.settings
        if (isLauncherOrSystem(context, pkg)) {
            OverlayController.hideIf("app:")
            OverlayController.hideIf("strict:")
            return
        }
        if (settings.blockedApps.contains(pkg)) {
            blockApp(context, pkg, "App blocked")
            return
        }
        if (pkg == YOUTUBE && settings.youtubeMode == "block_all") {
            blockApp(
                context, pkg, "YouTube is paused",
                message = "YouTube is switched off while Regain guards your focus. If you only need study channels, switch the allowlist on from the Focus Guard tab."
            )
            return
        }
        if (settings.strictMode && settings.lockSettingsDuringStrict &&
            FocusEngine.isFocusRunning() && isSettings(pkg)
        ) {
            OverlayController.show(
                context,
                key = "strict:settings",
                title = "Strict mode is on",
                message = "Settings stay locked until this focus session ends. That is the point — you already decided.",
                chip = "STRICT MODE",
                label = "Locked for ${formatClock(FocusEngine.state.value.remainingSeconds)}",
                accentColor = ACCENT_AMBER,
                allowBypass = false,
                onLeave = { goHome(context) }
            )
            return
        }
        OverlayController.hideIf("app:")
        OverlayController.hideIf("strict:")
    }

    fun onContent(context: Context, pkg: String, verdict: Detector.Verdict) {
        if (!verdict.block) return
        if (!Store.settings.blockingEnabled || isBypassed(context)) return
        val accent = when {
            verdict.key.contains("reels") -> ACCENT_ROSE
            verdict.key.contains("shorts") -> ACCENT_AMBER
            verdict.key.contains("channel") -> ACCENT_VIOLET
            else -> ACCENT_LIME
        }
        val key = verdict.key.ifBlank { "content:$pkg" }
        val alreadyWalled = OverlayController.currentKey == key
        OverlayController.show(
            context,
            key = key,
            title = verdict.title,
            message = verdict.message,
            chip = verdict.chip,
            label = verdict.label,
            accentColor = accent,
            allowBypass = !(Store.settings.strictMode && FocusEngine.isFocusRunning()),
            onLeave = { goHome(context) },
            onBypass = { bypassFor(context, 5) }
        )
        if (!alreadyWalled) verifyWallVisible(context, key)
    }

    fun onContentCleared() {
        OverlayController.hideIf("content:")
    }

    private fun maybeFocusGuard(context: Context, pkg: String) {
        val settings = Store.settings
        if (!settings.focusGuard) return
        if (!FocusEngine.isFocusRunning()) return
        if (isEssential(pkg)) return

        val now = System.currentTimeMillis()
        val last = guardShown[pkg] ?: 0L
        if (now - last < 120_000) return
        guardShown[pkg] = now

        val left = FocusEngine.state.value.remainingSeconds
        OverlayController.show(
            context,
            key = "guard:$pkg",
            title = "Stay in the session",
            message = "You left Regain with ${formatClock(left)} still on the clock. The hardest part is coming back — this is the way back.",
            chip = "FOCUS GUARD",
            label = "${FocusEngine.state.value.subject} · ${formatClock(left)} left",
            accentColor = ACCENT_BLUE,
            allowBypass = true,
            onLeave = { openRegain(context) },
            onBypass = { bypassFor(context, 5) }
        )
    }

    private fun blockApp(context: Context, pkg: String, title: String, message: String? = null) {
        val label = appLabel(context, pkg)
        val alreadyWalled = OverlayController.currentKey == "app:$pkg"
        if (!alreadyWalled) Log.d(TAG, "blocking $pkg ($label)")
        OverlayController.show(
            context,
            key = "app:$pkg",
            title = title,
            message = message ?: "$label is on your block list. You can still open it when the session is over.",
            chip = "FOCUS GUARD · BLOCKED",
            label = label,
            accentColor = ACCENT_LIME,
            allowBypass = !(Store.settings.strictMode && FocusEngine.isFocusRunning()),
            onLeave = { goHome(context) },
            onBypass = { bypassFor(context, 5) }
        )
        if (!alreadyWalled) verifyWallVisible(context, "app:$pkg")
    }

    /**
     * Some apps declare HIDE_OVERLAY_WINDOWS, which makes Android hide every non-system
     * overlay we draw (the window is attached but marked invisible). A wall that cannot be
     * seen is not a block, so fall back to bouncing the user home.
     */
    private fun verifyWallVisible(context: Context, key: String) {
        wallVerifier.removeCallbacksAndMessages(null)
        wallVerifier.postDelayed({
            if (OverlayController.currentKey != key || !OverlayController.isShowing) return@postDelayed
            val service = BlockAccessibilityService.instance ?: return@postDelayed
            if (service.overlayVisible()) return@postDelayed
            Log.d(TAG, "wall force-hidden over ${key.removePrefix("app:")} -> home")
            OverlayController.hide()
            goHome(context)
        }, 650)
    }

    fun appLabel(context: Context, pkg: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (t: Throwable) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun isSettings(pkg: String): Boolean =
        pkg == "com.android.settings" || pkg.endsWith(".settings") || pkg == "com.google.android.apps.wellbeing"

    private fun isEssential(pkg: String): Boolean {
        val essentials = listOf(
            "dialer", "incallui", "phone", "camera", "messaging", "mms", "deskclock", "clock",
            "alarm", "emergency", "calculator", "health"
        )
        val lower = pkg.lowercase()
        return essentials.any { lower.contains(it) }
    }

    private fun isLauncherOrSystem(context: Context, pkg: String): Boolean {
        if (pkg == "com.android.systemui") return true
        val lower = pkg.lowercase()
        if (lower.contains("launcher") || lower == "com.android.home") return true
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved?.activityInfo?.packageName == pkg
        } catch (t: Throwable) {
            false
        }
    }

    fun goHome(context: Context) {
        val service = BlockAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    fun openRegain(context: Context) {
        runCatching {
            val intent = Intent(context, ai.regain.clone.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        }
        OverlayController.hide()
    }

    fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
}
