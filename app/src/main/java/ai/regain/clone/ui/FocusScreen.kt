package ai.regain.clone.ui

import ai.regain.clone.audio.Ambience
import ai.regain.clone.data.Store
import ai.regain.clone.data.TimerMode
import ai.regain.clone.focus.FocusEngine
import ai.regain.clone.focus.Phase
import ai.regain.clone.focus.formatClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FocusScreen(perms: PermState, onOpenGuard: () -> Unit) {
    val p = LocalPalette.current
    val state by FocusEngine.state.collectAsState()
    val settings = Store.settings

    var preset by rememberSaveable { mutableIntStateOf(settings.focusMin) }
    var selectedMode by rememberSaveable { mutableStateOf(TimerMode.FOCUS) }
    var subject by rememberSaveable { mutableStateOf(Store.subjects.firstOrNull()?.name ?: "Other") }
    var showCustom by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(settings.ambienceVolume) }

    val idle = state.phase == Phase.IDLE
    androidx.compose.runtime.LaunchedEffect(state.mode, state.phase) {
        if (state.phase != Phase.IDLE) selectedMode = state.mode
    }

    val idleSeconds = when (selectedMode) {
        TimerMode.FOCUS -> preset * 60
        TimerMode.SHORT_BREAK -> preset * 60
        TimerMode.LONG_BREAK -> preset * 60
        TimerMode.STOPWATCH -> 0
    }
    val clock = if (idle) idleSeconds else state.displaySeconds
    val progress = if (idle) 0f else state.progress
    val accent = if (selectedMode == TimerMode.FOCUS) p.accent else p.accentAlt
    val isBreak = selectedMode == TimerMode.SHORT_BREAK || selectedMode == TimerMode.LONG_BREAK

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenTitle(
            eyebrow = if (state.phase == Phase.COMPLETED) "SESSION COMPLETE" else "FOCUS ENGINE",
            title = when {
                state.phase == Phase.COMPLETED && state.mode == TimerMode.FOCUS -> "That's a wrap"
                state.phase == Phase.COMPLETED -> "Break finished"
                selectedMode == TimerMode.STOPWATCH -> "Stopwatch"
                selectedMode == TimerMode.FOCUS -> "Deep focus"
                else -> "Recovery"
            },
            subtitle = when {
                state.phase == Phase.RUNNING && state.mode == TimerMode.FOCUS ->
                    "Round ${state.round} · ${state.subject}"
                isBreak -> "Step away properly. ${settings.roundsBeforeLong} focus rounds → long break."
                settings.strictMode -> "Strict mode is on — this session runs to the end."
                else -> "One session at a time. Phone stays quiet."
            }
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                Triple(TimerMode.FOCUS, "Focus", ""),
                Triple(TimerMode.SHORT_BREAK, "Short", ""),
                Triple(TimerMode.LONG_BREAK, "Long", ""),
                Triple(TimerMode.STOPWATCH, "Stopwatch", "")
            ).forEach { (mode, label, _) ->
                Pill(
                    text = label,
                    selected = selectedMode == mode,
                    accent = accent,
                    onClick = {
                        if (idle) {
                            selectedMode = mode
                            if (mode == TimerMode.SHORT_BREAK) preset = settings.shortBreakMin
                            if (mode == TimerMode.LONG_BREAK) preset = settings.longBreakMin
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        FocusRing(
            progress = progress,
            size = 272.dp,
            stroke = 15.dp,
            accent = accent
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(
                    when {
                        state.phase == Phase.COMPLETED -> "DONE"
                        state.phase == Phase.PAUSED -> "PAUSED"
                        state.phase == Phase.RUNNING -> "IN SESSION"
                        else -> if (selectedMode == TimerMode.STOPWATCH) "READY" else "${preset} MIN"
                    },
                    color = accent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    formatClock(if (selectedMode == TimerMode.STOPWATCH && idle) 0 else clock),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = p.text,
                    letterSpacing = (-2.5).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        state.isActive -> state.subject
                        selectedMode == TimerMode.STOPWATCH -> "Count up"
                        else -> subject
                    },
                    fontSize = 12.sp,
                    color = p.muted
                )
                if (state.mode == TimerMode.FOCUS && !idle) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(5.dp))
                        Text("Round ${state.round}", fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when {
            state.phase == Phase.COMPLETED -> {
                Text(
                    if (state.mode == TimerMode.FOCUS) "Logged to your stats. Breathe." else "Back to it when you're ready.",
                    color = p.muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                AccentButton("Continue") { FocusEngine.stop(force = true) }
            }

            state.isActive -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleAction(
                        icon = if (state.phase == Phase.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        size = 76.dp,
                        background = accent,
                        tint = p.onAccent
                    ) { FocusEngine.togglePause() }
                    Spacer(Modifier.width(26.dp))
                    CircleAction(
                        icon = Icons.Filled.Stop,
                        size = 58.dp,
                        background = p.card,
                        tint = p.text,
                        enabled = !(settings.strictMode && state.mode == TimerMode.FOCUS)
                    ) { FocusEngine.stop() }
                }
                if (settings.strictMode && state.mode == TimerMode.FOCUS) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShapePill)
                            .background(p.accent.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = p.accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Strict mode — you can't end this one early", fontSize = 11.sp, color = p.accent)
                    }
                }
            }

            else -> {
                AccentButton(
                    text = when (selectedMode) {
                        TimerMode.FOCUS -> "Start focus · $preset min"
                        TimerMode.SHORT_BREAK -> "Start short break · ${settings.shortBreakMin} min"
                        TimerMode.LONG_BREAK -> "Start long break · ${settings.longBreakMin} min"
                        TimerMode.STOPWATCH -> "Start stopwatch"
                    },
                    accent = accent,
                    leading = Icons.Filled.PlayArrow
                ) {
                    when (selectedMode) {
                        TimerMode.FOCUS -> FocusEngine.startFocus(preset, subject)
                        TimerMode.SHORT_BREAK -> FocusEngine.startBreak(preset, false)
                        TimerMode.LONG_BREAK -> FocusEngine.startBreak(preset, true)
                        TimerMode.STOPWATCH -> FocusEngine.startStopwatch()
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (idle && selectedMode != TimerMode.STOPWATCH) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = if (isBreak) listOf(5, 10, 15, 20, 30) else listOf(15, 25, 45, 50, 90)
                options.forEach { minutes ->
                    Pill(
                        text = "$minutes",
                        selected = preset == minutes,
                        accent = accent,
                        onClick = { preset = minutes }
                    )
                }
                if (!isBreak) {
                    Pill(text = "Custom", selected = preset !in options, accent = accent) { showCustom = true }
                }
            }
        }

        if (idle && selectedMode == TimerMode.FOCUS) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Store.subjects.forEach { s ->
                    val selected = subject == s.name
                    Box(
                        Modifier
                            .clip(RoundedCornerShapePill)
                            .background(if (selected) Color(s.color).copy(alpha = 0.18f) else p.card)
                            .clickable { subject = s.name }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${s.emoji}  ${s.name}",
                            fontSize = 12.sp,
                            color = if (selected) Color(s.color) else p.muted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.MusicNote, tint = p.accentAlt, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Text("Focus music", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Spacer(Modifier.weight(1f))
                Text(
                    Ambience.sounds.firstOrNull { it.id == settings.ambience }?.label ?: "None",
                    fontSize = 11.sp,
                    color = p.accentAlt,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Ambience.sounds.forEach { sound ->
                    Pill(
                        text = sound.label,
                        selected = settings.ambience == sound.id,
                        accent = p.accentAlt
                    ) {
                        Store.update { it.copy(ambience = sound.id) }
                        FocusEngine.refreshAmbience()
                    }
                }
            }
            if (settings.ambience != "none") {
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        Ambience.setVolume(it)
                    },
                    onValueChangeFinished = { Store.update { s -> s.copy(ambienceVolume = volume) } },
                    colors = SliderDefaults.colors(
                        thumbColor = p.accentAlt,
                        activeTrackColor = p.accentAlt,
                        inactiveTrackColor = p.cardBorder
                    )
                )
            }
        }

        if (!perms.all) {
            Spacer(Modifier.height(14.dp))
            GlassCard(
                Modifier.clickable { onOpenGuard() },
                background = p.danger.copy(alpha = 0.08f),
                borderColor = p.danger.copy(alpha = 0.25f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Focus Guard is not fully set up.", fontSize = 12.5.sp, color = p.danger, modifier = Modifier.weight(1f))
                    Text("Fix", fontSize = 12.sp, color = p.danger, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }

    if (showCustom) {
        var text by remember { mutableStateOf(preset.toString()) }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            containerColor = p.bgBottom,
            title = { Text("Custom focus length", color = p.text) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { it.isDigit() }.take(3) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { preset = it.coerceIn(1, 600) }
                    showCustom = false
                }) { Text("Set", color = p.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCustom = false }) { Text("Cancel", color = p.muted) }
            }
        )
    }
}

private val RoundedCornerShapePill = androidx.compose.foundation.shape.RoundedCornerShape(50)

@Composable
private fun CircleAction(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    background: Color,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.38f))
    }
}
