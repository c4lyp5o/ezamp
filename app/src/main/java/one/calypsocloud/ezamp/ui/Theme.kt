@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package one.calypsocloud.ezamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.calypsocloud.ezamp.R

/**
 * ez house style, ported 1:1 from ez.css (shared by every ez* app, itself ported from
 * ezllm/web + ezdyndns): a dark data terminal, colour on data and status only, one accent
 * per app, radius 6, Public Sans, tabular figures.
 *
 * ezamp's accent is rose — the only accent slot not already taken by ezhost (#ffc93c),
 * ezjot (#fb923c), eztunes (#f6e016), ezconv (#4a8ad6), ezmon (#67c7dd), ezopen (#6b8fe6)
 * or ezups (#2dd4bf).
 */
object Ez {
    // dark tokens (ez.css `.dark`)
    val Bg = Color(0xFF171719)
    val Surface = Color(0xFF131217)
    val Raised = Color(0xFF212023)
    val Hover = Color(0xFF26252A)
    val Line = Color(0xFF232326)
    val LineStrong = Color(0xFF3A3A3E)
    val Ink = Color(0xFFF2F2F3)
    val Dim = Color(0xFFBDBDC0)
    val Mute = Color(0xFF8A8C92)

    // app accent
    val Accent = Color(0xFFEF7A98)
    val AccentInk = Color(0xFF1A0A10)

    // status
    val Ok = Color(0xFF44A282)
    val Warn = Color(0xFFD9A441)
    val Bad = Color(0xFFE5566F)

    val Radius = 6.dp
    val DurFast = 140
    val DurBase = 220
}

// Legacy names kept so the existing screens keep compiling against house-style values.
val Bg = Ez.Bg
val SurfaceC = Ez.Surface
val SurfaceHi = Ez.Raised
val Ink = Ez.Ink
val Dim = Ez.Dim
val Mute = Ez.Mute
val Accent = Ez.Accent

private val PublicSans = FontFamily(
    Font(R.font.public_sans, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.public_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.public_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.public_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private fun t(
    size: Float,
    weight: FontWeight,
    tracking: Float = 0f,
    lineHeight: Float = size * 1.55f,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = PublicSans,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = tracking.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

private val type = Typography(
    displaySmall = t(32f, FontWeight.Light, tracking = -0.9f, lineHeight = 36f),
    headlineSmall = t(22f, FontWeight.Normal, tracking = -0.3f, lineHeight = 28f),
    titleMedium = t(15f, FontWeight.Medium, tracking = -0.1f, lineHeight = 21f),
    titleSmall = t(13.5f, FontWeight.Medium, lineHeight = 19f),
    bodyMedium = t(13.5f, FontWeight.Normal, lineHeight = 20f),
    bodySmall = t(12f, FontWeight.Normal, lineHeight = 18f),
    labelSmall = t(10.5f, FontWeight.Medium, tracking = 1.2f, lineHeight = 14f),
    labelMedium = t(11.5f, FontWeight.Medium, tracking = 0.6f, lineHeight = 15f),
)

/** Durations and counts: house style sets tabular figures on every number. */
val Tabular = TextStyle(fontFamily = PublicSans, fontFeatureSettings = "tnum")

private val scheme = darkColorScheme(
    background = Ez.Bg,
    surface = Ez.Surface,
    surfaceVariant = Ez.Raised,
    onBackground = Ez.Ink,
    onSurface = Ez.Ink,
    onSurfaceVariant = Ez.Dim,
    primary = Ez.Accent,
    onPrimary = Ez.AccentInk,
    secondary = Ez.Accent,
    onSecondary = Ez.AccentInk,
    primaryContainer = Ez.Raised,
    onPrimaryContainer = Ez.Ink,
    error = Ez.Bad,
    outline = Ez.LineStrong,
    outlineVariant = Ez.Line,
)

@Composable
fun EzampTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
