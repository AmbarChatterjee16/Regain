package ai.regain.clone.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenBackground(accentGlow: Boolean = true) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (!accentGlow) return@Canvas
            val r1 = size.width * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glowA.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.06f),
                    radius = r1
                ),
                radius = r1,
                center = Offset(size.width * 0.12f, size.height * 0.06f)
            )
            val r2 = size.width * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glowB.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.34f),
                    radius = r2
                ),
                radius = r2,
                center = Offset(size.width * 0.95f, size.height * 0.34f)
            )
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    shapeRadius: Dp = 24.dp,
    borderColor: Color = LocalPalette.current.cardBorder,
    background: Color = LocalPalette.current.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(shapeRadius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(shapeRadius))
            .padding(padding),
        content = content
    )
}

@Composable
fun Eyebrow(text: String, color: Color = LocalPalette.current.accent, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = LocalPalette.current.text
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 12.sp, color = LocalPalette.current.muted)
            }
        }
        if (action != null && onAction != null) {
            Text(
                action,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onAction() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = LocalPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = LocalPalette.current.accent,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val bg by animateColorAsState(
        if (selected) accent else p.card,
        label = "pillBg"
    )
    val fg by animateColorAsState(
        if (selected) p.onAccent else p.muted,
        label = "pillFg"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else p.cardBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AccentButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalPalette.current.accent,
    leading: ImageVector? = null,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) accent else accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = p.onAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalPalette.current.accent,
    leading: ImageVector? = null,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(1.dp, p.cardBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FocusRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 264.dp,
    stroke: Dp = 14.dp,
    accent: Color = LocalPalette.current.accent,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalPalette.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f + 2.dp.toPx()
            val side = this.size.minDimension - inset * 2
            val topLeft = Offset(
                (this.size.width - side) / 2f,
                (this.size.height - side) / 2f
            )
            drawArc(
                color = p.cardBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(side, side),
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
            if (progress > 0.002f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent.copy(alpha = 0.55f), accent, accent)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(side, side),
                    style = Stroke(strokePx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color = LocalPalette.current.accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) p.text else p.muted
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.5.sp, color = p.muted, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = p.muted,
                uncheckedTrackColor = p.card,
                uncheckedBorderColor = p.cardBorder
            )
        )
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, accent: Color = LocalPalette.current.accent) {
    Column(modifier) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = LocalPalette.current.muted, lineHeight = 14.sp)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 8.dp),
        thickness = 1.dp,
        color = LocalPalette.current.cardBorder
    )
}

@Composable
fun MiniBars(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, value ->
            val isLast = index == values.lastIndex
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (value > 0) "$value" else "·",
                    fontSize = 9.sp,
                    color = if (isLast) p.accent else p.muted
                )
                Spacer(Modifier.height(4.dp))
                val fraction = (value.toFloat() / max).coerceIn(0.03f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((fraction * 82).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isLast) p.accent else p.accent.copy(alpha = 0.30f)
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(labels.getOrElse(index) { "" }, fontSize = 9.5.sp, color = p.muted)
            }
        }
    }
}

@Composable
fun Chip(text: String, color: Color = LocalPalette.current.accent, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier.clip(RoundedCornerShape(50)),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun CenterNote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = LocalPalette.current.muted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp
    )
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = LocalPalette.current.text, lineHeight = 34.sp)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 13.sp, color = LocalPalette.current.muted, lineHeight = 19.sp)
        }
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = LocalPalette.current.muted, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = LocalPalette.current.accent, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
