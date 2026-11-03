package ai.regain.clone.block

import ai.regain.clone.MainActivity
import ai.regain.clone.R
import ai.regain.clone.data.Store
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Backup enforcement path. The accessibility service is the fast one; this keeps the
 * process alive and double checks the foreground app through UsageStats when available.
 */
class WatchdogService : Service() {

    companion object {
        private const val CHANNEL_ID = "blocker_status"
        private const val NOTIF_ID = 1002

        fun sync(context: Context) {
            val shouldRun = Store.settings.blockingEnabled && Perms.hasUsageAccess(context)
            if (shouldRun) start(context) else stop(context)
        }

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, WatchdogService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WatchdogService::class.java)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        loop = scope.launch {
            while (isActive) {
                val pkg = foregroundPackage()
                if (!pkg.isNullOrBlank()) {
                    Enforcer.enforceAppRules(this@WatchdogService, pkg)
                }
                delay(1200)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        OverlayController.hide()
        super.onDestroy()
    }

    private fun foregroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            // 60s lookback: resume events only exist when the user switches apps,
            // so a short window would report "null" while sitting in one app.
            val events = usm.queryEvents(now - 60_000, now)
            val event = UsageEvents.Event()
            var pkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (resumed) pkg = event.packageName
            }
            pkg
        } catch (t: Throwable) {
            null
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_block),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Focus Guard is watching the foreground app"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): android.app.Notification {
        val settings = Store.settings
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val bits = mutableListOf<String>()
        if (settings.blockedApps.isNotEmpty()) bits.add("${settings.blockedApps.size} apps")
        if (settings.blockReels) bits.add("Reels")
        if (settings.blockShorts) bits.add("Shorts")
        if (settings.youtubeMode == "allowlist") bits.add("${settings.allowedChannels.size} channels allowed")
        if (settings.youtubeMode == "block_all") bits.add("YouTube off")
        val subtitle = if (bits.isEmpty()) "No rules yet" else bits.joinToString(" · ")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus Guard is on")
            .setContentText(subtitle)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
