package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/** Hand-drawn transport controls: no icon dependency, no font-dependent glyphs. */
@Composable
fun PlayPause(playing: Boolean, size: Dp = 56.dp, tint: Color = Ink, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size).clickable { onClick() },
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
    Box(
        modifier = Modifier.size(size).clickable { onClick() },
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
