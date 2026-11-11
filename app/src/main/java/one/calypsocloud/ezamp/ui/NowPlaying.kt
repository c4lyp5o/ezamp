package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.calypsocloud.ezamp.player.PlayState
import one.calypsocloud.ezamp.player.PlayerHolder

@Composable
fun NowPlaying(play: PlayState, onClose: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        PlayerHolder.setPositionTracking(true)
        onDispose { PlayerHolder.setPositionTracking(false) }
    }

    val frac = when {
        dragging -> dragValue
        play.durationMs > 0 -> (play.positionMs.toFloat() / play.durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "⌄",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = Dim,
                modifier = Modifier.clickable { onClose() }.padding(vertical = 14.dp, horizontal = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "NOW PLAYING",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(28.dp))
        }

        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth
            coil3.compose.AsyncImage(
                model = play.artUri,
                contentDescription = null,
                modifier = Modifier.size(side).background(SurfaceHi),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            play.title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            listOf(play.artist, play.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        Slider(
            value = frac,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                PlayerHolder.seekToFraction(dragValue)
            },
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = SurfaceHi,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                fmt(play.positionMs),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                fmt(play.durationMs),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Dim,
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipGlyph(next = false, size = 34.dp) { PlayerHolder.prev() }
            Spacer(Modifier.width(34.dp))
            PlayPause(playing = play.playing, size = 68.dp) { PlayerHolder.toggle() }
            Spacer(Modifier.width(34.dp))
            SkipGlyph(next = true, size = 34.dp) { PlayerHolder.next() }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (play.count > 0) "${play.index + 1} of ${play.count}" else "",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Dim,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
    }
}

