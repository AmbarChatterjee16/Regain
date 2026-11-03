package ai.regain.clone.ui

import ai.regain.clone.audio.Ambience
import ai.regain.clone.block.Perms
import ai.regain.clone.block.WatchdogService
import ai.regain.clone.data.Store
import ai.regain.clone.focus.FocusEngine
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val settings = Store.settings
    var volume by remember { mutableFloatStateOf(settings.ambienceVolume) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(
            eyebrow = "SETTINGS",
            title = "Tune your system",
            subtitle = "Every Pro feature is unlocked in this build. Set it once, then stop negotiating with yourself."
        )

        Spacer(Modifier.height(16.dp))
        SectionHeader("Timer", "The shape of a focus block")
        Spacer(Modifier.height(8.dp))

        GlassCard {
            StepperRow("Focus length", "Default deep work block", settings.focusMin, "min", 5, 180) {
                Store.update { s -> s.copy(focusMin = it) }
            }
            DividerLine()
            StepperRow("Short break", "Between rounds", settings.shortBreakMin, "min", 1, 30) {
                Store.update { s -> s.copy(shortBreakMin = it) }
            }
            DividerLine()
            StepperRow("Long break", "After a full set", settings.longBreakMin, "min", 5, 60) {
                Store.update { s -> s.copy(longBreakMin = it) }
            }
            DividerLine()
            StepperRow("Rounds before long break", "Pomodoro rhythm", settings.roundsBeforeLong, "", 2, 8) {
                Store.update { s -> s.copy(roundsBeforeLong = it) }
            }
            DividerLine()
            StepperRow("Daily goal", "Minutes that count as a good day", settings.dailyGoalMin, "min", 15, 720, step = 15) {
                Store.update { s -> s.copy(dailyGoalMin = it) }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Discipline", "Make quitting inconvenient")
        Spacer(Modifier.height(8.dp))

        GlassCard {
            ToggleRow(
                title = "Strict mode",
                subtitle = "Focus sessions cannot be ended early",
                checked = settings.strictMode,
                icon = Icons.Filled.Lock
            ) { on -> Store.update { it.copy(strictMode = on) } }
            DividerLine()
            ToggleRow(
                title = "Lock Settings during sessions",
                subtitle = "Extra friction while strict mode is on",
                checked = settings.lockSettingsDuringStrict,
                icon = Icons.Filled.Shield,
                enabled = settings.strictMode
            ) { on -> Store.update { it.copy(lockSettingsDuringStrict = on) } }
            DividerLine()
            ToggleRow(
                title = "Focus Guard reminders",
                subtitle = "Nudge you back when you leave the app mid-session",
                checked = settings.focusGuard,
                icon = Icons.Filled.Bolt
            ) { on -> Store.update { it.copy(focusGuard = on) } }
            DividerLine()
            ToggleRow(
                title = "Auto-start breaks",
                subtitle = "Roll straight into the recovery timer",
                checked = settings.autoStartBreaks,
                icon = Icons.Filled.Timer
            ) { on -> Store.update { it.copy(autoStartBreaks = on) } }
            DividerLine()
            ToggleRow(
                title = "Auto-start next focus",
                subtitle = "Skip the manual start after a break",
                checked = settings.autoStartFocus,
                icon = Icons.Filled.Timer
            ) { on -> Store.update { it.copy(autoStartFocus = on) } }
            DividerLine()
            ToggleRow(
                title = "Haptics",
                subtitle = "Buzz when a session completes",
                checked = settings.haptics,
                icon = Icons.Filled.Vibration
            ) { on -> Store.update { it.copy(haptics = on) } }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Sound", "Ambience under the session")
        Spacer(Modifier.height(8.dp))

        GlassCard {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Ambience.sounds.forEach { sound ->
                    Pill(text = sound.label, selected = settings.ambience == sound.id) {
                        Store.update { it.copy(ambience = sound.id) }
                        FocusEngine.refreshAmbience()
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                Ambience.sounds.firstOrNull { it.id == settings.ambience }?.subtitle ?: "Silence",
                fontSize = 11.sp,
                color = p.muted
            )
            if (settings.ambience != "none") {
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        Ambience.setVolume(it)
                    },
                    onValueChangeFinished = { Store.update { s -> s.copy(ambienceVolume = volume) } },
                    colors = SliderDefaults.colors(
                        thumbColor = p.accent,
                        activeTrackColor = p.accent,
                        inactiveTrackColor = p.cardBorder
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Theme", "Change the room you study in")
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            palettes.forEach { palette ->
                val selected = settings.theme == palette.id
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(palette.bgBottom, palette.glowA))
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) palette.accent else p.cardBorder,
                                shape = CircleShape
                            )
                            .clickable { Store.update { it.copy(theme = palette.id) } }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        palette.name,
                        fontSize = 10.5.sp,
                        color = if (selected) palette.accent else p.muted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("System", "Permissions and upkeep")
        Spacer(Modifier.height(8.dp))

        GlassCard {
            SettingsLink("Accessibility service", "Required for Reels, Shorts and channel detection") {
                Perms.openAccessibilitySettings(context)
            }
            DividerLine()
            SettingsLink("Display over other apps", "Draw the focus wall") {
                Perms.openOverlaySettings(context)
            }
            DividerLine()
            SettingsLink("Usage access", "Backup foreground detection") {
                Perms.openUsageSettings(context)
            }
            DividerLine()
            SettingsLink("Notification permission", "Countdown and blocker status") {
                Perms.openNotificationSettings(context)
            }
            DividerLine()
            SettingsLink("Battery optimisation", "Stop Android from killing the blocker") {
                Perms.openBatterySettings(context)
            }
            DividerLine()
            SettingsLink("Re-sync background services", "Restart Focus Guard and timers") {
                WatchdogService.sync(context)
                FocusEngine.init(context)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Data")
        Spacer(Modifier.height(8.dp))

        GlassCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { confirmClear = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Filled.Refresh, tint = p.danger, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reset focus history", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                    Text("Deletes every logged session. Rules and themes stay.", fontSize = 10.5.sp, color = p.muted)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(background = p.accent.copy(alpha = 0.07f), borderColor = p.accent.copy(alpha = 0.22f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Shield, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Regain clone · 1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.text)
                    Text(
                        "All Pro features unlocked. Detection is heuristic — use the readout on the Focus Guard tab to verify rules on your device.",
                        fontSize = 10.5.sp,
                        color = p.muted,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = p.bgBottom,
            title = { Text("Erase focus history?", color = p.text) },
            text = {
                Text(
                    "Your streaks, charts and recent sessions will be gone. This cannot be undone.",
                    color = p.muted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.clearSessions()
                    confirmClear = false
                }) { Text("Erase", color = p.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep it", color = p.muted) }
            }
        )
    }
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Text(subtitle, fontSize = 10.5.sp, color = p.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { onChange((value - step).coerceAtLeast(min)) }
            Text(
                "$value${if (unit.isBlank()) "" else " $unit"}",
                modifier = Modifier.width(70.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = p.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            StepButton("+") { onChange((value + step).coerceAtMost(max)) }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(p.card)
            .border(1.dp, p.cardBorder, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            Text(subtitle, fontSize = 10.5.sp, color = p.muted)
        }
        Text("OPEN", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = p.accent, letterSpacing = 0.8.sp)
    }
}
