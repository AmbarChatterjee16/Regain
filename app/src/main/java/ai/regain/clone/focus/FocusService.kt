package ai.regain.clone.focus

import ai.regain.clone.MainActivity
import ai.regain.clone.R
import ai.regain.clone.data.Store
import ai.regain.clone.data.TimerMode
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusService : Service() {

    companion object {
        private const val CHANNEL_ID = "focus_session"
        private const val NOTIF_ID = 1001
        private const val ACTION_TOGGLE = "ai.regain.clone.TOGGLE"
        private const val ACTION_STOP = "ai.regain.clone.STOP"

        fun ensure(context: Context?) {
            val ctx = context ?: return
            val state = FocusEngine.state.value
            if (state.isActive || state.phase == Phase.COMPLETED) start(ctx) else stop(ctx)
        }

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, FocusService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FocusService::class.java)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var lastRenderedSeconds = -1
    private var lastPhase: Phase? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(FocusEngine.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        collectJob = scope.launch {
            FocusEngine.state.collectLatest { state ->
                if (!state.isActive && state.phase != Phase.COMPLETED) {
                    stopSelf()
                } else {
                    val display = state.displaySeconds
                    if (display != lastRenderedSeconds || lastPhase != state.phase) {
                        lastRenderedSeconds = display
                        lastPhase = state.phase
                        notify(buildNotification(state))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> FocusEngine.togglePause()
            ACTION_STOP -> {
                if (!(Store.settings.strictMode && FocusEngine.isFocusRunning())) {
                    FocusEngine.stop()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_focus),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live focus countdown"
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun notify(notification: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification) }
    }

    private fun buildNotification(state: TimerState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val strict = Store.settings.strictMode && state.mode == TimerMode.FOCUS
        val title = when (state.phase) {
            Phase.COMPLETED -> if (state.mode == TimerMode.FOCUS) "Session complete 🎉" else "Break over ☕"
            Phase.PAUSED -> "Paused · ${state.subject}"
            else -> when (state.mode) {
                TimerMode.FOCUS -> "Focusing · ${state.subject}"
                TimerMode.SHORT_BREAK -> "Short break"
                TimerMode.LONG_BREAK -> "Long break"
                TimerMode.STOPWATCH -> "Stopwatch running"
            }
        }
        val time = formatClock(state.displaySeconds)
        val text = when (state.phase) {
            Phase.COMPLETED -> "Nice work. Tap to open Regain."
            Phase.PAUSED -> "$time left · paused"
            else -> "$time " + if (state.mode == TimerMode.STOPWATCH) "elapsed" else "left" +
                if (strict) "  ·  strict mode" else ""
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(state.isActive)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.phase != Phase.COMPLETED) {
            val progress = if (state.mode == TimerMode.STOPWATCH) 0 else (state.progress * 100).toInt()
            if (state.mode != TimerMode.STOPWATCH) {
                builder.setProgress(100, progress, false)
            }
            builder.addAction(
                0,
                if (state.phase == Phase.PAUSED) "Resume" else "Pause",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, FocusService::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            if (!strict) {
                builder.addAction(
                    0, "Stop",
                    PendingIntent.getService(
                        this, 2,
                        Intent(this, FocusService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
        }
        return builder.build()
    }
}

fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}
