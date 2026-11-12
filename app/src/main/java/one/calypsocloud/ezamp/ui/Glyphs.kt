package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * A short key-press tick for transport controls. View haptics need no VIBRATE permission and follow
 * the phone's own "Touch feedback" setting, so listeners who turned it off feel nothing.
 */
@Composable
internal fun rememberTap(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) } }
}

/** Hand-drawn transport controls: no icon dependency, no font-dependent glyphs. */
@Composable
fun PlayPause(playing: Boolean, size: Dp = 56.dp, tint: Color = Ink, onClick: () -> Unit) {
    val tap = rememberTap()
    Box(
        modifier = Modifier
            .size(size)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { tap(); onClick() }
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            if (playing) {
                val barW = w * 0.16f
                drawRoundRect(tint, Offset(w * 0.30f - barW / 2, h * 0.24f), Size(barW, h * 0.52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3))
                drawRoundRect(tint, Offset(w * 0.70f - barW / 2, h * 0.24f), Size(barW, h * 0.52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3))
            } else {
                val p = Path().apply {
                    moveTo(w * 0.36f, h * 0.24f)
                    lineTo(w * 0.76f, h * 0.50f)
                    lineTo(w * 0.36f, h * 0.76f)
                    close()
                }
                drawPath(p, tint)
            }
        }
    }
}

@Composable
fun SkipGlyph(next: Boolean, size: Dp = 34.dp, tint: Color = Dim, onClick: () -> Unit) {
    val tap = rememberTap()
    Box(
        modifier = Modifier
            .size(size)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { tap(); onClick() }
            .semantics { contentDescription = if (next) "Next track" else "Previous track" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            drawSkip(this, next, tint)
        }
    }
}

private fun drawSkip(scope: DrawScope, next: Boolean, tint: Color) {
    with(scope) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (next) {
                moveTo(w * 0.22f, h * 0.26f)
                lineTo(w * 0.62f, h * 0.50f)
                lineTo(w * 0.22f, h * 0.74f)
            } else {
                moveTo(w * 0.78f, h * 0.26f)
                lineTo(w * 0.38f, h * 0.50f)
                lineTo(w * 0.78f, h * 0.74f)
            }
            close()
        }
        drawPath(p, tint)
        val barX = if (next) w * 0.70f else w * 0.26f
        drawRoundRect(
            tint,
            Offset(barX, h * 0.26f),
            Size(w * 0.06f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
        )
    }
}

/** Two crossing arrows. Accent when on. */
@Composable
fun ShuffleGlyph(on: Boolean, size: Dp = 30.dp, onClick: () -> Unit) {
    val tint = if (on) Accent else Mute
    Box(
        modifier = Modifier
            .size(size + 14.dp)
            .clickable { onClick() }
            .semantics { contentDescription = if (on) "Shuffle on" else "Shuffle off" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val a = Path().apply {
                moveTo(w * 0.16f, h * 0.32f); lineTo(w * 0.36f, h * 0.32f)
                lineTo(w * 0.62f, h * 0.68f); lineTo(w * 0.82f, h * 0.68f)
            }
            val b = Path().apply {
                moveTo(w * 0.16f, h * 0.68f); lineTo(w * 0.36f, h * 0.68f)
                lineTo(w * 0.62f, h * 0.32f); lineTo(w * 0.82f, h * 0.32f)
            }
            drawPath(a, tint, style = stroke)
            drawPath(b, tint, style = stroke)
            arrowHead(this, w * 0.86f, h * 0.32f, right = true, tint)
            arrowHead(this, w * 0.86f, h * 0.68f, right = true, tint)
        }
    }
}

/** A loop of two arrows; repeat-one adds a dot in the middle. Accent unless off. */
@Composable
fun RepeatGlyph(mode: Int, size: Dp = 30.dp, onClick: () -> Unit) {
    // Player.REPEAT_MODE_OFF = 0, _ONE = 1, _ALL = 2.
    val tint = if (mode == 0) Mute else Accent
    Box(
        modifier = Modifier
            .size(size + 14.dp)
            .clickable { onClick() }
            .semantics {
                contentDescription = when (mode) {
                    1 -> "Repeat one"
                    2 -> "Repeat all"
                    else -> "Repeat off"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val top = Path().apply {
                moveTo(w * 0.20f, h * 0.58f); lineTo(w * 0.20f, h * 0.34f); lineTo(w * 0.74f, h * 0.34f)
            }
            val bottom = Path().apply {
                moveTo(w * 0.80f, h * 0.42f); lineTo(w * 0.80f, h * 0.66f); lineTo(w * 0.26f, h * 0.66f)
            }
            drawPath(top, tint, style = stroke)
            drawPath(bottom, tint, style = stroke)
            arrowHead(this, w * 0.80f, h * 0.34f, right = true, tint)
            arrowHead(this, w * 0.20f, h * 0.66f, right = false, tint)
            if (mode == 1) drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }
}

private fun arrowHead(scope: DrawScope, x: Float, y: Float, right: Boolean, tint: Color) {
    with(scope) {
        val s = size.width * 0.11f
        val p = Path().apply {
            if (right) {
                moveTo(x, y); lineTo(x - s, y - s * 0.9f); lineTo(x - s, y + s * 0.9f)
            } else {
                moveTo(x, y); lineTo(x + s, y - s * 0.9f); lineTo(x + s, y + s * 0.9f)
            }
            close()
        }
        drawPath(p, tint)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ez.Line))
}

@Composable
fun Art(url: String?, size: Dp, corner: Dp = 6.dp, label: String? = null) {
    ArtBox(url = url, label = label, corner = corner, modifier = Modifier.size(size))
}

/** Cover that fills whatever width it is given, keeping a square aspect. */
@Composable
fun ArtBox(url: String?, label: String?, corner: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .background(SurfaceHi, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn first, so a real cover hides it; a missing cover degrades to an initial, not a grey hole.
        if (!label.isNullOrBlank()) {
            Text(
                label.trim().take(1).uppercase(),
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = Dim,
            )
        }
        if (!url.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(corner)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
    }
}
