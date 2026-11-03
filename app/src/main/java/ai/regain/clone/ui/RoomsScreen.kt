package ai.regain.clone.ui

import ai.regain.clone.data.Store
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class FocusRoom(
    val name: String,
    val subject: String,
    val emoji: String,
    val people: Int,
    val minutes: Int,
    val accent: Color
)

@Composable
fun RoomsScreen(onBack: () -> Unit, onJoin: (Int, String) -> Unit) {
    val p = LocalPalette.current
    val rooms = listOf(
        FocusRoom("NEET Night Shift", "Biology", "🌙", 842, 50, Color(0xFF7C9DFF)),
        FocusRoom("JEE Physics Grind", "Physics", "⚛", 1203, 90, Color(0xFF5FE3C0)),
        FocusRoom("Silent Library", "Any subject", "📚", 3410, 25, Color(0xFFC8F560)),
        FocusRoom("Code & Coffee", "Coding", "☕", 517, 45, Color(0xFFFF9F6B)),
        FocusRoom("CA Finals Sprint", "Accounts", "📈", 289, 60, Color(0xFFB68CFF))
    )
    val leaderboard = listOf(
        "Aarav" to 312,
        "Meera" to 268,
        "Ishaan" to 241,
        "You" to Store.todayMinutes(),
        "Sofia" to 96,
        "Kabir" to 75
    ).sortedByDescending { it.second }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = p.text)
            }
            Column(Modifier.weight(1f)) {
                Text("Focus rooms", fontSize = 24.sp, fontWeight = FontWeight.Black, color = p.text)
                Text("12,480 people focusing right now", fontSize = 11.sp, color = p.muted)
            }
            Icon(Icons.Filled.Groups, null, tint = p.accent, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.height(14.dp))

        GlassCard(background = p.accent.copy(alpha = 0.08f), borderColor = p.accent.copy(alpha = 0.25f)) {
            Text("Accountability, without a group chat.", fontSize = 13.sp, color = p.text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Join a room, start a session, and everyone's timer runs together. Rooms in this offline build use simulated peers.",
                fontSize = 11.sp,
                color = p.muted,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Live rooms", "Tap to join a timer")
        Spacer(Modifier.height(10.dp))

        rooms.forEach { room ->
            GlassCard(Modifier.padding(bottom = 10.dp), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(room.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(room.emoji, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(room.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
                        Text(
                            "${room.subject} · ${room.people} focusing · ${room.minutes} min",
                            fontSize = 10.5.sp,
                            color = p.muted
                        )
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(room.accent)
                            .clickable { onJoin(room.minutes, room.subject) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = p.onAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Join", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.onAccent)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionHeader("Today's leaderboard", "Minutes focused")
        Spacer(Modifier.height(10.dp))

        GlassCard {
            leaderboard.forEachIndexed { index, (name, minutes) ->
                if (index > 0) DividerLine()
                val isMe = name == "You"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMe) p.accent.copy(alpha = 0.20f) else p.card
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMe) p.accent else p.muted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        name,
                        fontSize = 13.sp,
                        color = if (isMe) p.accent else p.text,
                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text("$minutes min", fontSize = 11.5.sp, color = p.muted)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
