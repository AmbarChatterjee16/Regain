package ai.regain.clone.ui

import ai.regain.clone.block.Perms
import ai.regain.clone.block.WatchdogService
import ai.regain.clone.data.Store
import ai.regain.clone.data.TimerMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

data class PermState(
    val overlay: Boolean = false,
    val usage: Boolean = false,
    val accessibility: Boolean = false,
    val notifications: Boolean = true
) {
    val all get() = overlay && accessibility
    val missingCount get() = listOf(overlay, accessibility).count { !it }
}

@Composable
fun HomeScreen(
    perms: PermState,
    onStartFocus: (Int, String) -> Unit,
    onOpenFocus: () -> Unit,
    onOpenGuard: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val settings = Store.settings
    val today = Store.todayMinutes()
    val goal = settings.dailyGoalMin.coerceAtLeast(1)
    val fraction = (today.toFloat() / goal).coerceIn(0f, 1f)
    val streak = Store.streak()
    val sessionCount = Store.focusSessions().count {
        it.start >= Store.dayStart(System.currentTimeMillis())
    }

    var subject by rememberSaveable { mutableStateOf(Store.subjects.firstOrNull()?.name ?: "Other") }
    var preset by rememberSaveable { mutableIntStateOf(settings.focusMin) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night focus"
    }
    val todayLabel = android.text.format.DateFormat.format("EEEE, d MMM", System.currentTimeMillis()).toString()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow(todayLabel)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        greeting,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = p.text,
                        letterSpacing = (-0.8).sp
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(p.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = p.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("$streak", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            GlassCard(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("TODAY'S FOCUS")
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$today",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                color = p.text,
                                letterSpacing = (-1.5).sp
                            )
                            Text(
                                " / $goal min",
                                fontSize = 13.sp,
                                color = p.muted,
                                modifier = Modifier.padding(bottom = 7.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(p.cardBorder)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(p.accent)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                fraction >= 1f -> "Daily goal complete. Anything else is a bonus."
                                today == 0 -> "Nothing logged yet. The first 25 minutes are the hardest."
                                else -> "${goal - today} min left to hit today's goal."
                            },
                            fontSize = 11.5.sp,
                            color = p.muted
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    FocusRing(progress = fraction, size = 92.dp, stroke = 9.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(fraction * 100).toInt()}%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = p.accent
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                AccentButton(
                    text = "Start focus · $preset min",
                    leading = Icons.Filled.Bolt
                ) { onStartFocus(preset, subject) }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 50, 90).forEach { minutes ->
                        Pill(
                            text = "$minutes min",
                            selected = preset == minutes,
                            onClick = { preset = minutes }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subject", fontSize = 11.sp, color = p.muted)
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Store.subjects.forEach { s ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (subject == s.name) Color(s.color).copy(alpha = 0.20f) else Color.Transparent
                                    )
                                    .clickable { subject = s.name }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "${s.emoji}  ${s.name}",
                                    fontSize = 11.5.sp,
                                    color = if (subject == s.name) Color(s.color) else p.muted,
                                    fontWeight = if (subject == s.name) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f), padding = 14.dp) {
                    StatBlock("$sessionCount", "sessions today")
                }
                GlassCard(Modifier.weight(1f), padding = 14.dp) {
                    StatBlock("${Store.totalMinutes() / 60}h", "focused all time", accent = p.accentAlt)
                }
                GlassCard(Modifier.weight(1f), padding = 14.dp) {
                    StatBlock("${Store.bestDayMinutes()}", "best day (min)")
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Shield)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Focus Guard", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
                        Text(
                            guardSummary(settings.blockedApps.size, settings.blockReels, settings.blockShorts, settings.youtubeMode),
                            fontSize = 11.5.sp,
                            color = p.muted,
                            lineHeight = 15.sp
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = settings.blockingEnabled,
                        onCheckedChange = { on ->
                            Store.update { it.copy(blockingEnabled = on) }
                            WatchdogService.sync(context)
                            if (on && !perms.all) onOpenGuard()
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = p.onAccent,
                            checkedTrackColor = p.accent,
                            uncheckedThumbColor = p.muted,
                            uncheckedTrackColor = p.card,
                            uncheckedBorderColor = p.cardBorder
                        )
                    )
                }
                if (!perms.all) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.danger.copy(alpha = 0.10f))
                            .clickable { onOpenGuard() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${perms.missingCount} permission${if (perms.missingCount > 1) "s" else ""} missing — finish setup",
                            fontSize = 12.sp,
                            color = p.danger,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ChevronRight, null, tint = p.danger, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.clickable { onOpenRooms() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Groups, tint = p.accentAlt)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Focus rooms", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.text)
                        Text(
                            "Study alongside 12,480 people right now",
                            fontSize = 11.5.sp,
                            color = p.muted
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = p.muted, modifier = Modifier.size(20.dp))
                }
            }
        }

        item {
            GlassCard(background = p.accent.copy(alpha = 0.07f), borderColor = p.accent.copy(alpha = 0.22f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("“", fontSize = 30.sp, color = p.accent, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Discipline is choosing between what you want now and what you want most.",
                            fontSize = 13.sp,
                            color = p.text,
                            lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("— Abraham Lincoln", fontSize = 10.5.sp, color = p.muted)
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GhostButton("Stats", Modifier.weight(1f), leading = Icons.Filled.Timer) { onOpenStats() }
                GhostButton("Themes", Modifier.weight(1f), leading = Icons.Filled.Lock) { onOpenSettings() }
            }
        }
    }
}

private fun guardSummary(apps: Int, reels: Boolean, shorts: Boolean, youtubeMode: String): String {
    if (apps == 0 && !reels && !shorts && youtubeMode == "off") {
        return "No rules yet — pick apps, Reels, Shorts or a YouTube allowlist."
    }
    val parts = mutableListOf<String>()
    if (apps > 0) parts.add("$apps app${if (apps > 1) "s" else ""}")
    if (reels) parts.add("Reels")
    if (shorts) parts.add("Shorts")
    if (youtubeMode == "allowlist") parts.add("channel allowlist")
    if (youtubeMode == "block_all") parts.add("YouTube off")
    return parts.joinToString(" · ")
}
