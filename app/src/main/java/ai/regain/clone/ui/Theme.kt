package ai.regain.clone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class Palette(
    val id: String,
    val name: String,
    val tagline: String,
    val bgTop: Color,
    val bgBottom: Color,
    val glowA: Color,
    val glowB: Color,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val card: Color,
    val cardBorder: Color,
    val text: Color,
    val muted: Color,
    val danger: Color
)

val palettes = listOf(
    Palette(
        id = "midnight",
        name = "Midnight",
        tagline = "Deep ink, lime signal",
        bgTop = Color(0xFF05070F),
        bgBottom = Color(0xFF0B1224),
        glowA = Color(0xFFC8F560),
        glowB = Color(0xFF8B5CF6),
        accent = Color(0xFFC8F560),
        accentAlt = Color(0xFF8B5CF6),
        onAccent = Color(0xFF0A0E1A),
        card = Color(0x14FFFFFF),
        cardBorder = Color(0x1FFFFFFF),
        text = Color(0xFFF2F5FA),
        muted = Color(0xFF9AA6B8),
        danger = Color(0xFFFF7CAB)
    ),
    Palette(
        id = "forest",
        name = "Forest",
        tagline = "Moss, rain, quiet air",
        bgTop = Color(0xFF04120C),
        bgBottom = Color(0xFF0A2018),
        glowA = Color(0xFF7CE0A3),
        glowB = Color(0xFF3AA6A0),
        accent = Color(0xFF9DF6B6),
        accentAlt = Color(0xFF5FD3C8),
        onAccent = Color(0xFF06231A),
        card = Color(0x14FFFFFF),
        cardBorder = Color(0x22A9F0C4),
        text = Color(0xFFF0F7F1),
        muted = Color(0xFF93AFA1),
        danger = Color(0xFFFF8E8E)
    ),
    Palette(
        id = "dusk",
        name = "Dusk",
        tagline = "Amber afterglow",
        bgTop = Color(0xFF140A10),
        bgBottom = Color(0xFF251119),
        glowA = Color(0xFFFF9F6B),
        glowB = Color(0xFFFF7CAB),
        accent = Color(0xFFFFB38A),
        accentAlt = Color(0xFFFF7CAB),
        onAccent = Color(0xFF26120C),
        card = Color(0x16FFFFFF),
        cardBorder = Color(0x26FFB38A),
        text = Color(0xFFFFF3EC),
        muted = Color(0xFFB39A92),
        danger = Color(0xFFFF7CAB)
    ),
    Palette(
        id = "ocean",
        name = "Ocean",
        tagline = "Cold light, clear head",
        bgTop = Color(0xFF03111C),
        bgBottom = Color(0xFF072436),
        glowA = Color(0xFF6FD7FF),
        glowB = Color(0xFF7C9DFF),
        accent = Color(0xFF9BE7FF),
        accentAlt = Color(0xFF7C9DFF),
        onAccent = Color(0xFF06202E),
        card = Color(0x14FFFFFF),
        cardBorder = Color(0x229BE7FF),
        text = Color(0xFFECF7FF),
        muted = Color(0xFF8FACBE),
        danger = Color(0xFFFF8FB1)
    ),
    Palette(
        id = "lofi",
        name = "Lofi",
        tagline = "Cassette warm, 2 a.m.",
        bgTop = Color(0xFF120E0A),
        bgBottom = Color(0xFF241A12),
        glowA = Color(0xFFFFD166),
        glowB = Color(0xFFE07A5F),
        accent = Color(0xFFFFD98A),
        accentAlt = Color(0xFFE07A5F),
        onAccent = Color(0xFF231A0B),
        card = Color(0x16FFFFFF),
        cardBorder = Color(0x26FFD98A),
        text = Color(0xFFFFF6E8),
        muted = Color(0xFFB3A18C),
        danger = Color(0xFFFF8E8E)
    )
)

fun paletteFor(id: String): Palette = palettes.firstOrNull { it.id == id } ?: palettes.first()

val LocalPalette = staticCompositionLocalOf { palettes.first() }

private fun regainTypography(): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        letterSpacing = (-1.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp
    )
)

@Composable
fun RegainTheme(themeId: String, content: @Composable () -> Unit) {
    val p = paletteFor(themeId)
    val scheme = darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accent.copy(alpha = 0.16f),
        onPrimaryContainer = p.text,
        secondary = p.accentAlt,
        onSecondary = p.onAccent,
        background = p.bgBottom,
        onBackground = p.text,
        surface = p.bgBottom,
        onSurface = p.text,
        surfaceVariant = p.card,
        onSurfaceVariant = p.muted,
        outline = p.cardBorder,
        error = p.danger
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = regainTypography(), content = content)
    }
}
