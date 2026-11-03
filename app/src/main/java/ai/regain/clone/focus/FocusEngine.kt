package ai.regain.clone.focus

import ai.regain.clone.audio.Ambience
import ai.regain.clone.data.Session
import ai.regain.clone.data.Store
import ai.regain.clone.data.TimerMode
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { IDLE, RUNNING, PAUSED, COMPLETED }

data class TimerState(
    val mode: TimerMode = TimerMode.FOCUS,
    val phase: Phase = Phase.IDLE,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val subject: String = "Math",
    val round: Int = 1,
    val startedAt: Long = 0L
) {
    val isActive: Boolean get() = phase == Phase.RUNNING || phase == Phase.PAUSED

    val progress: Float
        get() = when {
            mode == TimerMode.STOPWATCH -> 0f
            totalSeconds <= 0 -> 0f
            else -> (1f - remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        }

    val displaySeconds: Int
        get() = if (mode == TimerMode.STOPWATCH) elapsedSeconds else remainingSeconds
}

object FocusEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var completionJob: Job? = null
    private var endAt: Long = 0L
    private var pausedRemaining: Int = 0
    private var appContext: Context? = null

    private const val PREFS = "regain_timer"

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        restore()
    }

    fun startFocus(minutes: Int = Store.settings.focusMin, subject: String? = null) {
        val target = subject ?: Store.subjects.firstOrNull()?.name ?: "Other"
        begin(TimerMode.FOCUS, minutes.coerceIn(1, 600), target)
    }

    fun startBreak(minutes: Int, long: Boolean) {
        begin(if (long) TimerMode.LONG_BREAK else TimerMode.SHORT_BREAK, minutes.coerceIn(1, 120), "Break")
    }

    fun startStopwatch() {
        completionJob?.cancel()
        val s = _state.value
        endAt = 0L
        _state.value = TimerState(
            mode = TimerMode.STOPWATCH,
            phase = Phase.RUNNING,
            subject = s.subject,
            startedAt = System.currentTimeMillis(),
            round = s.round
        )
        launchTicker()
        persist()
        FocusService.ensure(appContext)
    }

    private fun begin(mode: TimerMode, minutes: Int, subject: String) {
        completionJob?.cancel()
        val total = minutes * 60
        endAt = System.currentTimeMillis() + total * 1000L
        _state.value = TimerState(
            mode = mode,
            phase = Phase.RUNNING,
            totalSeconds = total,
            remainingSeconds = total,
            subject = subject,
            startedAt = System.currentTimeMillis(),
            round = _state.value.round
        )
        launchTicker()
        persist()
        FocusService.ensure(appContext)
        startAmbience()
    }

    fun pause() {
        val s = _state.value
        if (s.phase != Phase.RUNNING) return
        ticker?.cancel()
        pausedRemaining = if (s.mode == TimerMode.STOPWATCH) s.elapsedSeconds else s.remainingSeconds
        _state.value = s.copy(phase = Phase.PAUSED, remainingSeconds = pausedRemaining)
        persist()
        Ambience.stop()
    }

    fun resume() {
        val s = _state.value
        if (s.phase != Phase.PAUSED) return
        if (s.mode == TimerMode.STOPWATCH) {
            _state.value = s.copy(phase = Phase.RUNNING, startedAt = System.currentTimeMillis() - pausedRemaining * 1000L)
        } else {
            endAt = System.currentTimeMillis() + pausedRemaining * 1000L
            _state.value = s.copy(phase = Phase.RUNNING, remainingSeconds = pausedRemaining)
        }
        launchTicker()
        persist()
        FocusService.ensure(appContext)
        startAmbience()
    }

    fun togglePause() {
        if (_state.value.phase == Phase.RUNNING) pause() else if (_state.value.phase == Phase.PAUSED) resume()
    }

    /** Ends the current session. Records partial focus time when it is worth keeping. */
    fun stop(force: Boolean = false) {
        val s = _state.value
        if (s.phase == Phase.IDLE) return
        if (s.phase == Phase.COMPLETED) {
            completionJob?.cancel()
            Ambience.stop()
            resetIdle(s.mode)
            return
        }
        ticker?.cancel()
        completionJob?.cancel()
        if (s.mode == TimerMode.FOCUS) {
            val elapsed = if (s.phase == Phase.RUNNING) {
                ((System.currentTimeMillis() - s.startedAt) / 1000).toInt()
            } else {
                s.totalSeconds - pausedRemaining
            }
            if (elapsed >= 60 && !force) {
                Store.addSession(
                    Session(
                        id = System.currentTimeMillis(),
                        start = s.startedAt,
                        seconds = elapsed,
                        subject = s.subject,
                        mode = s.mode.name,
                        completed = false
                    )
                )
            }
        }
        Ambience.stop()
        _state.value = TimerState(mode = s.mode, phase = Phase.IDLE, subject = s.subject, round = s.round)
        persist()
        FocusService.ensure(appContext)
    }

    fun completeNow() {
        completionJob?.cancel()
        ticker?.cancel()
        complete()
    }

    fun setSubject(name: String) {
        _state.value = _state.value.copy(subject = name)
        persist()
    }

    private fun launchTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val s = _state.value
                if (s.phase != Phase.RUNNING) break
                val now = System.currentTimeMillis()
                if (s.mode == TimerMode.STOPWATCH) {
                    val elapsed = ((now - s.startedAt) / 1000L).toInt().coerceAtLeast(0)
                    _state.value = s.copy(elapsedSeconds = elapsed, remainingSeconds = elapsed)
                } else {
                    val left = ((endAt - now) / 1000L).toInt().coerceAtLeast(0)
                    _state.value = s.copy(remainingSeconds = left)
                    if (left <= 0) {
                        complete()
                        break
                    }
                }
                delay(200)
            }
        }
    }

    private fun complete() {
        val s = _state.value
        if (s.phase == Phase.IDLE || s.phase == Phase.COMPLETED) return
        ticker?.cancel()
        Ambience.stop()
        if (s.mode == TimerMode.FOCUS) {
            Store.addSession(
                Session(
                    id = System.currentTimeMillis(),
                    start = s.startedAt,
                    seconds = s.totalSeconds,
                    subject = s.subject,
                    mode = s.mode.name,
                    completed = true
                )
            )
            _state.value = s.copy(phase = Phase.COMPLETED, remainingSeconds = 0, round = s.round + 1)
        } else {
            _state.value = s.copy(phase = Phase.COMPLETED, remainingSeconds = 0)
        }
        persist()
        vibrate()
        FocusService.ensure(appContext)

        completionJob = scope.launch {
            delay(if (s.mode == TimerMode.FOCUS) 3200 else 2000)
            advance(s)
        }
    }

    private fun advance(finished: TimerState) {
        val settings = Store.settings
        when (finished.mode) {
            TimerMode.FOCUS -> {
                if (settings.autoStartBreaks) {
                    val round = finished.round + 1
                    val long = round % settings.roundsBeforeLong == 0
                    begin(
                        if (long) TimerMode.LONG_BREAK else TimerMode.SHORT_BREAK,
                        if (long) settings.longBreakMin else settings.shortBreakMin,
                        "Break"
                    )
                    return
                }
            }
            TimerMode.SHORT_BREAK, TimerMode.LONG_BREAK -> {
                if (settings.autoStartFocus) {
                    startFocus(settings.focusMin, lastFocusSubject())
                    return
                }
            }
            else -> Unit
        }
        resetIdle(finished.mode)
    }

    private fun lastFocusSubject(): String =
        Store.focusSessions().lastOrNull()?.subject ?: Store.subjects.firstOrNull()?.name ?: "Other"

    private fun resetIdle(mode: TimerMode = TimerMode.FOCUS) {
        _state.value = TimerState(
            mode = mode,
            phase = Phase.IDLE,
            subject = _state.value.subject,
            round = _state.value.round
        )
        persist()
        FocusService.ensure(appContext)
    }

    private fun startAmbience() {
        val settings = Store.settings
        if (settings.ambience != "none") {
            Ambience.start(settings.ambience, settings.ambienceVolume)
        }
    }

    fun refreshAmbience() {
        Ambience.stop()
        if (_state.value.phase == Phase.RUNNING) startAmbience()
    }

    private fun vibrate() {
        val ctx = appContext ?: return
        if (!Store.settings.haptics) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 220), -1))
        }
    }

    // ---------- persistence so a killed process does not lose the session ----------

    private fun persist() {
        val ctx = appContext ?: return
        val s = _state.value
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", s.mode.name)
            .putString("phase", s.phase.name)
            .putInt("total", s.totalSeconds)
            .putInt("remaining", s.remainingSeconds)
            .putInt("elapsed", s.elapsedSeconds)
            .putString("subject", s.subject)
            .putInt("round", s.round)
            .putLong("startedAt", s.startedAt)
            .putLong("endAt", endAt)
            .apply()
    }

    private fun restore() {
        val ctx = appContext ?: return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val modeName = p.getString("mode", null) ?: return
        val phaseName = p.getString("phase", null) ?: return
        val mode = runCatching { TimerMode.valueOf(modeName) }.getOrDefault(TimerMode.FOCUS)
        val phase = runCatching { Phase.valueOf(phaseName) }.getOrDefault(Phase.IDLE)
        if (phase == Phase.IDLE || phase == Phase.COMPLETED) return

        val total = p.getInt("total", 0)
        val remaining = p.getInt("remaining", 0)
        val elapsed = p.getInt("elapsed", 0)
        val subject = p.getString("subject", "Math") ?: "Math"
        val round = p.getInt("round", 1)
        val startedAt = p.getLong("startedAt", System.currentTimeMillis())
        val savedEnd = p.getLong("endAt", 0L)

        if (phase == Phase.PAUSED) {
            pausedRemaining = remaining
            _state.value = TimerState(mode, Phase.PAUSED, total, remaining, elapsed, subject, round, startedAt)
            return
        }

        if (mode == TimerMode.STOPWATCH) {
            _state.value = TimerState(mode, Phase.RUNNING, 0, 0, elapsed, subject, round, startedAt)
            launchTicker()
            return
        }

        val left = ((savedEnd - System.currentTimeMillis()) / 1000L).toInt()
        if (left <= 0) {
            resetIdle(mode)
            return
        }
        endAt = savedEnd
        _state.value = TimerState(mode, Phase.RUNNING, total, left, 0, subject, round, startedAt)
        launchTicker()
        FocusService.ensure(ctx)
        startAmbience()
    }

    fun isFocusRunning(): Boolean =
        _state.value.phase == Phase.RUNNING && _state.value.mode == TimerMode.FOCUS
}
