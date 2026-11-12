package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.calypsocloud.ezamp.player.Eq
import kotlin.math.roundToInt

/** SOUND in settings: off, the phone's presets, or a custom curve. Moving a band makes it custom. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SoundSection(): Unit {
    val ctx = LocalContext.current
    val eq by Eq.state.collectAsState()
    LaunchedEffect(Unit) { Eq.probe(ctx) }

    Text("SOUND", style = MaterialTheme.typography.labelSmall, color = Dim)
    Spacer(Modifier.height(10.dp))
    if (!eq.available) {
        Text("NO EQUALIZER ON THIS PHONE", style = MaterialTheme.typography.bodySmall, color = Mute)
        return
    }
    val choices = listOf(Eq.OFF to "OFF") + eq.presets.mapIndexed { i, n -> i to n.uppercase() } + (Eq.CUSTOM to "CUSTOM")
    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (mode, label) ->
            val selected = eq.mode == mode
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .border(1.dp, if (selected) Accent else Ez.Line, RoundedCornerShape(6.dp))
                    .clickable { Eq.select(mode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Accent else Mute)
            }
        }
    }
    if (eq.mode == Eq.OFF) return

    Spacer(Modifier.height(6.dp))
    val line = Ez.Line
    val tick = Ez.LineStrong
    val stepMb = 100
    val steps = ((eq.maxMb - eq.minMb) / stepMb - 1).coerceAtLeast(0)
    eq.freqsHz.forEachIndexed { band, hz ->
        val mb = eq.levels.getOrElse(band) { 0 }
        val label = if (hz >= 1000) "${(hz / 100) / 10.0}k".replace(".0k", "k") else "$hz"
        val db = (mb / 100.0).roundToInt()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = Mute, modifier = Modifier.width(40.dp))
            Slider(
                value = mb.toFloat(),
                onValueChange = { v -> Eq.setBand(band, (v / stepMb).roundToInt() * stepMb) },
                onValueChangeFinished = { Eq.commit() },
                valueRange = eq.minMb.toFloat()..eq.maxMb.toFloat(),
                steps = steps,
                thumb = {
                    Box(Modifier.size(14.dp).background(Accent, CircleShape))
                },
                track = { st ->
                    val span = st.valueRange.endInclusive - st.valueRange.start
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val y = size.height / 2
                        val zero = size.width * (0f - st.valueRange.start) / span
                        val at = size.width * (st.value - st.valueRange.start) / span
                        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                        drawLine(Accent, Offset(zero, y), Offset(at, y), strokeWidth = 2.dp.toPx())
                        drawCircle(tick, radius = 2.dp.toPx(), center = Offset(zero, y))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$label hertz, ${if (db > 0) "+" else ""}$db decibels" },
            )
            Text(
                if (db > 0) "+$db" else "$db",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = if (db == 0) Mute else Ink,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp),
            )
        }
    }
}
