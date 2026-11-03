package ai.regain.clone.ui

import ai.regain.clone.data.Store
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen() {
    val p = LocalPalette.current
    val week = Store.last7Days()
    val values = week.map { it.second }
    val labels = week.map { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it.first)) }
    val weekTotal = values.sum()
    val subjectTotals = Store.subjectTotals(7)
    val maxSubject = (subjectTotals.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    val today = Store.todayMinutes()
    val recent = Store.focusSessions().sortedByDescending { it.start }.take(8)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(
            eyebrow = "INSIGHTS",
            title = "Receipts of focus",
            subtitle = "Small sessions stack up. This is the quiet evidence that you're not wasting the day."
        )

        Spacer(Modifier.height(16.dp))

        GlassCard {
            Row {
                StatBlock("$today", "min today", Modifier.weight(1f))
                StatBlock("$weekTotal", "min this week", Modifier.weight(1f), accent = p.accentAlt)
                StatBlock("${Store.streak()}", "day streak", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            DividerLine()
            Row {
                val lifetimeMin = Store.totalMinutes()
                StatBlock(
                    if (lifetimeMin < 60) "${lifetimeMin}m" else "${lifetimeMin / 60}h",
                    "lifetime focus", Modifier.weight(1f)
                )
                StatBlock("${Store.totalSessions()}", "sessions done", Modifier.weight(1f), accent = p.accentAlt)
                StatBlock("${Store.bestDayMinutes()}", "best day (min)", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard {
            SectionHeader("Last 7 days", "Minutes of focused work")
            Spacer(Modifier.height(12.dp))
            MiniBars(values, labels)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    values.all { it == 0 } -> "No sessions yet this week. Start with 15 minutes — the bar is easy to fill."
                    values.last() >= (Store.settings.dailyGoalMin) -> "Goal reached today. Momentum is doing its job."
                    else -> "Today: ${values.lastOrNull() ?: 0} min. Average: ${weekTotal / 7} min/day."
                },
                fontSize = 11.sp,
                color = p.muted,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        GlassCard {
            SectionHeader("Where the time went", "Per subject, last 7 days")
            Spacer(Modifier.height(12.dp))
            if (subjectTotals.isEmpty()) {
                Muted("Nothing tracked yet. Pick a subject before your next session and this chart fills itself.")
            } else {
                subjectTotals.forEach { (subject, minutes) ->
                    val color = Color(Store.subjectColor(subject))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(color, 7.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(subject, fontSize = 12.sp, color = p.text, modifier = Modifier.weight(1f))
                        Text("$minutes min", fontSize = 11.sp, color = p.muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(p.cardBorder)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((minutes.toFloat() / maxSubject).coerceIn(0.03f, 1f))
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard {
            SectionHeader("Recent sessions", if (recent.isEmpty()) "None yet" else "Latest first")
            Spacer(Modifier.height(6.dp))
            if (recent.isEmpty()) {
                Muted("Finish a session and it lands here with the subject and length.")
            } else {
                recent.forEachIndexed { index, session ->
                    if (index > 0) DividerLine()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Dot(Color(Store.subjectColor(session.subject)), 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.subject,
                                fontSize = 13.sp,
                                color = p.text,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(session.start)),
                                fontSize = 10.sp,
                                color = p.muted
                            )
                        }
                        Text(
                            "${session.minutes} min",
                            fontSize = 12.sp,
                            color = if (session.completed) p.accent else p.muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!session.completed) {
                            Spacer(Modifier.width(6.dp))
                            Chip("PARTIAL", p.muted)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
