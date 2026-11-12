package one.calypsocloud.ezamp.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Track

/** The track whose hold-sheet is open, and the local playlist it was held in (if any). */
internal data class HeldTrack(val track: Track, val playlistId: String?)

/** One sheet for the whole app; Root draws it. A hold on any track row sets this. */
internal val heldTrack = mutableStateOf<HeldTrack?>(null)

/** Tap plays; hold opens the track sheet, with the long-press tick. TalkBack offers it as an action. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.trackPress(t: Track, playlistId: String?, onClick: () -> Unit): Modifier = composed {
    val view = LocalView.current
    combinedClickable(
        onLongClickLabel = "Add to playlist",
        onLongClick = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            heldTrack.value = HeldTrack(t, playlistId)
        },
        onClick = onClick,
    )
}

/** Run a local-playlist edit; a failure is logged, never thrown into the UI. */
private fun edit(scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> Unit) {
    scope.launch {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("ezamp-lists", "local playlist edit failed: ${e.message}")
        }
    }
}

/** ADD TO PLAYLIST: every local playlist as a ●/○ row (tap toggles), then a new one inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackSheet(held: HeldTrack, onClose: () -> Unit): Unit {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val lib by LibraryStore.state.collectAsState()
    val t = held.track
    var member by remember(t.id) { mutableStateOf<Set<String>>(emptySet()) }
    var ready by remember(t.id) { mutableStateOf(false) }
    var pending by remember(t.id) { mutableStateOf(0) }
    var revision by remember(t.id) { mutableStateOf(0) }
    LaunchedEffect(t.id, lib.localVersion, pending) {
        if (pending != 0) return@LaunchedEffect
        val readingRevision = revision
        val saved = LibraryStore.localPlaylistsWith(t.id)
        if (pending == 0 && readingRevision == revision) {
            member = saved
            ready = true
        }
    }
    var naming by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = Ez.Surface,
        contentColor = Ink,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 34.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(t.name, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            Text("ADD TO PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(4.dp))
            lib.localPlaylists.forEach { pl ->
                val inIt = pl.id in member
                ChoiceRow(title = pl.name, detail = plural("track", pl.trackCount), selected = inIt) {
                    if (ready) {
                        // Read live state: a second tap can arrive before this row recomposes.
                        val adding = pl.id !in member
                        member = if (adding) member + pl.id else member - pl.id
                        revision++
                        pending++
                        val result = LibraryStore.setInLocal(pl.id, t.id, adding)
                        edit(scope) {
                            try { result.await() } finally { pending-- }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (naming) {
                NameField(initial = "", action = "CREATE") { name ->
                    naming = false
                    edit(scope) { LibraryStore.createLocal(name, t.id) }
                }
            } else {
                Text(
                    "+  NEW PLAYLIST",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.clickable { naming = true }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** NEW from the Playlists tab: just a name. Opens the new, empty playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPlaylistSheet(onCreated: (String) -> Unit, onClose: () -> Unit): Unit {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = Ez.Surface,
        contentColor = Ink,
        dragHandle = null,
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 34.dp)) {
            Text("NEW PLAYLIST", style = MaterialTheme.typography.labelSmall, color = Mute)
            Spacer(Modifier.height(14.dp))
            NameField(initial = "", action = "CREATE") { name ->
                edit(scope) { onCreated(LibraryStore.createLocal(name)) }
            }
        }
    }
}

/** A one-line name with its action label on the right, in the search field's style. Focused on open. */
@Composable
internal fun NameField(initial: String, action: String, onDone: (String) -> Unit): Unit {
    // Cursor at the end, so RENAME edits the old name instead of typing in front of it.
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val text = field.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ok = text.isNotBlank()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = field,
                onValueChange = { field = if (it.text.length <= 80) it else field },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) onDone(text) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text("Name", style = MaterialTheme.typography.titleMedium, color = Mute)
                        inner()
                    }
                },
            )
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = if (ok) Accent else Mute,
                modifier = Modifier.clickable(enabled = ok) { onDone(text) }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
            )
        }
        Hairline()
    }
}
