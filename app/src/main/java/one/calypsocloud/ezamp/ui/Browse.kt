package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.calypsocloud.ezamp.data.Album
import one.calypsocloud.ezamp.data.Artist
import one.calypsocloud.ezamp.data.Genre
import one.calypsocloud.ezamp.data.LibState
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Playlist
import one.calypsocloud.ezamp.data.Track
import one.calypsocloud.ezamp.player.PlayState
import one.calypsocloud.ezamp.player.PlayerHolder
import kotlinx.coroutines.launch

/** The Library carousel. Declaration order is swipe order; the label strip is the only swipe surface. */
internal enum class LibPage(val label: String, val hint: String) {
    Albums("ALBUMS", "Search albums"),
    Tracks("TRACKS", "Search tracks"),
    Artists("ARTISTS", "Search artists"),
    Genres("GENRES", "Search genres"),
}

/** One list state per page, owned by Root so scroll positions survive an album round trip. */
internal class LibLists(
    val albums: LazyListState,
    val tracks: LazyListState,
    val artists: LazyListState,
    val genres: LazyListState,
) {
    fun forPage(p: LibPage): LazyListState = when (p) {
        LibPage.Albums -> albums
        LibPage.Tracks -> tracks
        LibPage.Artists -> artists
        LibPage.Genres -> genres
    }
}

// ------------------------------------------------------------------ library

@Composable
internal fun LibraryScreen(
    lib: LibState,
    play: PlayState,
    variant: String,
    pager: PagerState,
    query: String,
    onQuery: (String) -> Unit,
    lists: LibLists,
    allTracks: List<Track>?,
    artists: List<Artist>,
    artistsNeedResync: Boolean,
    genres: List<Genre>,
    genresNeedResync: Boolean,
    onPage: (Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
) {
    val art = variant == "art"
    val page = LibPage.entries[pager.currentPage]
    Column(Modifier.fillMaxSize()) {
        LibraryHeader(lib, onSync, onSettings)
        PageStrip(pager, onPage)
        SearchField(query, onQuery, page.hint)
        when (page) {
            LibPage.Albums -> AlbumsPage(lib, play, art, query, lists.albums, onOpenAlbum)
            LibPage.Tracks -> TracksPage(lib, play, query, allTracks, lists.tracks)
            LibPage.Artists -> ArtistsPage(artists, artistsNeedResync, query, lists.artists, onOpenArtist)
            LibPage.Genres -> GenresPage(genres, genresNeedResync, query, lists.genres, onOpenGenre)
        }
    }
}

@Composable
private fun LibraryHeader(lib: LibState, onSync: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.displaySmall, color = Ink)
            if (lib.downloadsOnly) {
                Spacer(Modifier.width(8.dp))
                Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (lib.syncing) "SYNCING" else "SYNC",
                style = MaterialTheme.typography.labelSmall,
                color = if (lib.syncing) Accent else Dim,
                modifier = Modifier.clickable { if (!lib.syncing) onSync() }.padding(6.dp),
            )
            Text(
                "SET",
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
                modifier = Modifier.clickable { onSettings() }.padding(6.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                lib.error != null -> lib.error
                lib.albums.isEmpty() && lib.syncing -> "reading your library…"
                else -> "${lib.albums.size} albums · ${lib.trackCount} tracks"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = if (lib.error != null) Ez.Bad else Dim,
        )
        val online by LibraryStore.online.collectAsState()
        if (!online) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (lib.downloadsOnly) "NO CONNECTION — showing what's on this device"
                else "NO CONNECTION — tap to browse downloads",
                style = MaterialTheme.typography.labelSmall,
                color = Ez.Warn,
                modifier = Modifier.clickable(enabled = !lib.downloadsOnly) {
                    LibraryStore.setDownloadsOnly(true)
                }.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * The carousel itself: page labels on a fixed-width pager with the active label centred, so its
 * neighbours stay visible (and tappable) on both sides. Left-aligning it hid every earlier page
 * off the left edge.
 *
 * The drag is ours, not the pager's: the label follows the finger but is clamped to one page
 * either way, and a quarter page commits. With the pager's own gesture a medium swipe dragged
 * past one label and the fling added another, so ALBUMS jumped straight to ARTISTS.
 */
@Composable
private fun PageStrip(pager: PagerState, onPage: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = 112.dp
        val sidePad = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fixed(pageWidth),
            contentPadding = PaddingValues(horizontal = sidePad),
            beyondViewportPageCount = LibPage.entries.size,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(pager) {
                    val pagePx = pageWidth.toPx()
                    var startPage = 0
                    var dragged = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startPage = pager.currentPage
                            dragged = 0f
                        },
                        onDragEnd = {
                            val target = when {
                                dragged < -pagePx / 4 -> startPage + 1
                                dragged > pagePx / 4 -> startPage - 1
                                else -> startPage
                            }.coerceIn(0, LibPage.entries.lastIndex)
                            scope.launch { pager.animateScrollToPage(target) }
                        },
                        onDragCancel = { scope.launch { pager.animateScrollToPage(startPage) } },
                    ) { change, dx ->
                        val next = (dragged + dx).coerceIn(-pagePx, pagePx)
                        pager.dispatchRawDelta(-(next - dragged))
                        dragged = next
                        change.consume()
                    }
                },
        ) { i ->
            val on = i == pager.currentPage
            Box(
                Modifier.fillMaxSize().clickable { onPage(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    LibPage.entries[i].label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Ink else Mute,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, hint: String) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f),
                // The whole 44 dp row is the field, not just the line of text.
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                hint,
                                style = MaterialTheme.typography.titleMedium,
                                color = Mute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Text(
                    "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = Dim,
                    modifier = Modifier.clickable { onQuery("") }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Hairline()
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = Dim,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
    )
}

/** In-memory filter for the small lists (albums, artists); logs its cost like the track search does. */
private inline fun <T> timedFilter(what: String, all: List<T>, query: String, text: (T) -> String): List<T> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return all
    val t0 = System.nanoTime()
    val out = all.filter { text(it).lowercase().contains(needle) }
    android.util.Log.i("ezamp-perf", "search[$what]=${(System.nanoTime() - t0) / 1_000_000}ms hits=${out.size}")
    return out
}

@Composable
private fun AlbumsPage(
    lib: LibState,
    play: PlayState,
    art: Boolean,
    query: String,
    state: LazyListState,
    onOpenAlbum: (String) -> Unit,
) {
    val searching = query.isNotBlank()
    val shown = remember(lib.albums, query) { timedFilter("albums", lib.albums, query) { "${it.name} ${it.artist}" } }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (searching) {
            item { Caption("${shown.size} albums") }
        } else if (lib.recent.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                SectionLabel("RECENTLY ADDED")
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (art) 16.dp else 14.dp),
                ) {
                    items(lib.recent, key = { it.id }) { a ->
                        AlbumCard(a, size = if (art) 156.dp else 132.dp) { onOpenAlbum(a.id) }
                    }
                }
                Spacer(Modifier.height(if (art) 30.dp else 24.dp))
                SectionLabel("ALL ALBUMS")
                Spacer(Modifier.height(if (art) 4.dp else 6.dp))
            }
        }

        if (art) {
            // Art-forward: two comfortable columns, covers doing the talking.
            items(shown.chunked(2), key = { it.first().id }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { a ->
                        AlbumTile(
                            a = a,
                            playing = play.album == a.name,
                            done = lib.doneCounts[a.id] ?: 0,
                            modifier = Modifier.weight(1f),
                        ) { onOpenAlbum(a.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(shown, key = { it.id }) { a ->
                AlbumRow(a, playing = play.album == a.name, done = lib.doneCounts[a.id] ?: 0) { onOpenAlbum(a.id) }
            }
        }

        if (!searching) {
            item {
                Spacer(Modifier.height(18.dp))
                PerfFooter(lib)
            }
        }
    }
}

@Composable
private fun TracksPage(
    lib: LibState,
    play: PlayState,
    query: String,
    all: List<Track>?,
    state: LazyListState,
) {
    LaunchedEffect(query) { LibraryStore.search(query) }
    val searching = query.isNotBlank()
    val rows = if (searching) lib.results else all.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Caption(
                when {
                    searching -> "${lib.results.size} results · ${lib.searchMs} ms · on-device"
                    all == null -> "reading tracks…"
                    else -> "${all.size} tracks"
                },
            )
        }
        itemsIndexed(rows, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, playing = play.itemId == t.id) { playWindow(rows, i) }
        }
    }
}

/**
 * Queue a window around the tapped row rather than the whole list: the full Tracks page would
 * push 5.5k media items through the controller and into queue.json on every tap.
 */
internal fun playWindow(rows: List<Track>, index: Int, size: Int = 300) {
    if (rows.size <= size) {
        PlayerHolder.play(rows, index)
        return
    }
    val start = (index - size / 3).coerceIn(0, rows.size - size)
    PlayerHolder.play(rows.subList(start, start + size).toList(), index - start)
}

@Composable
private fun ArtistsPage(
    artists: List<Artist>,
    needResync: Boolean,
    query: String,
    state: LazyListState,
    onOpenArtist: (String) -> Unit,
) {
    val shown = remember(artists, query) { timedFilter("artists", artists, query) { it.name } }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column {
                if (needResync) {
                    Text(
                        "Featured artists arrive with the next sync — tap SYNC above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ez.Warn,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
                    )
                }
                Caption("${shown.size} artists")
            }
        }
        items(shown, key = { it.name }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(a.name) }
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    a.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${a.albumCount}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Mute,
                )
            }
        }
    }
}

@Composable
private fun GenresPage(
    genres: List<Genre>,
    needResync: Boolean,
    query: String,
    state: LazyListState,
    onOpenGenre: (String) -> Unit,
) {
    val shown = remember(genres, query) { timedFilter("genres", genres, query) { it.name } }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column {
                if (needResync) {
                    Text(
                        "Genres arrive with the next sync — tap SYNC above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ez.Warn,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
                    )
                }
                Caption("${shown.size} genres")
            }
        }
        items(shown, key = { it.name }) { g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGenre(g.name) }
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    g.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${g.trackCount}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Mute,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ genre

/** A genre is a track list: PLAY ALL from the top, SHUFFLE over (a sample of) all of it. */
@Composable
internal fun GenreScreen(name: String, play: PlayState, onBack: () -> Unit) {
    var tracks by remember(name) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(name) { tracks = LibraryStore.tracksByGenre(name) }
    val rows = tracks.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "‹  BACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.clickable { onBack() }.padding(vertical = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (tracks == null) "reading tracks…" else "${rows.size} tracks",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PLAY ALL",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rows.isEmpty()) Mute else Accent,
                        modifier = Modifier
                            .clickable(enabled = rows.isNotEmpty()) { playWindow(rows, 0) }
                            .padding(vertical = 10.dp),
                    )
                    Spacer(Modifier.width(26.dp))
                    Text(
                        "SHUFFLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rows.size > 1) Accent else Mute,
                        modifier = Modifier
                            .clickable(enabled = rows.size > 1) {
                                // Same queue budget as playWindow: a sample of 300, shuffled.
                                PlayerHolder.playShuffled(if (rows.size > 300) rows.shuffled().take(300) else rows)
                            }
                            .padding(vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        itemsIndexed(rows, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, playing = play.itemId == t.id) { playWindow(rows, i) }
        }
    }
}

// ------------------------------------------------------------------ artist

@Composable
internal fun ArtistScreen(name: String, play: PlayState, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
    var albums by remember(name) { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(name) { albums = LibraryStore.albumsByArtist(name) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "‹  BACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.clickable { onBack() }.padding(vertical = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${albums.size} albums",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        items(albums, key = { it.id }) { a ->
            AlbumRow(a, playing = play.album == a.name) { onOpenAlbum(a.id) }
        }
    }
}

// ------------------------------------------------------------------ playlists tab

/**
 * Favourites lead the list as a permanent playlist (a null entry below), then the server's
 * music playlists. It is local, so it is there even with no server playlists at all.
 */
@Composable
internal fun PlaylistsScreen(
    lib: LibState,
    art: Boolean,
    state: LazyListState,
    onOpenFavourites: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val entries: List<Playlist?> = listOf(null) + lib.playlists
    val favCount = "${lib.favourites.size} tracks"
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                Text("Playlists", style = MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${entries.size} playlists",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
                Spacer(Modifier.height(if (art) 18.dp else 12.dp))
            }
        }
        if (art) {
            items(entries.chunked(2), key = { it.first()?.id ?: "favourites" }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { pl ->
                        if (pl == null) {
                            PlaylistTile("Favourites", favCount, Modifier.weight(1f), cover = { FavouritesArt(it) }, onClick = onOpenFavourites)
                        } else {
                            PlaylistTile(pl.name, "${pl.trackCount} tracks", Modifier.weight(1f), cover = {
                                ArtBox(url = pl.artUri, label = pl.name, corner = 6.dp, modifier = it)
                            }) { onOpen(pl.id) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(entries, key = { it?.id ?: "favourites" }) { pl ->
                if (pl == null) {
                    PlaylistRow("Favourites", favCount, cover = { FavouritesArt(it, corner = 4.dp) }, onClick = onOpenFavourites)
                } else {
                    PlaylistRow(pl.name, "${pl.trackCount} tracks", cover = {
                        ArtBox(url = pl.artUri, label = pl.name, corner = 4.dp, modifier = it)
                    }) { onOpen(pl.id) }
                }
            }
        }
    }
}

/**
 * The Favourites cover: no art to fetch, just a drawn heart on the raised surface. A text "♥" at
 * this size fell back to the red system emoji instead of the accent.
 */
@Composable
internal fun FavouritesArt(modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        modifier
            .aspectRatio(1f)
            .background(SurfaceHi, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize(0.32f)) {
            val w = size.width
            val h = size.height
            val heart = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.50f, h * 0.88f)
                cubicTo(w * 0.12f, h * 0.62f, w * 0.00f, h * 0.40f, w * 0.14f, h * 0.22f)
                cubicTo(w * 0.28f, h * 0.05f, w * 0.46f, h * 0.12f, w * 0.50f, h * 0.28f)
                cubicTo(w * 0.54f, h * 0.12f, w * 0.72f, h * 0.05f, w * 0.86f, h * 0.22f)
                cubicTo(w * 1.00f, h * 0.40f, w * 0.88f, h * 0.62f, w * 0.50f, h * 0.88f)
                close()
            }
            drawPath(heart, Accent)
        }
    }
}

@Composable
private fun PlaylistTile(
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    cover: @Composable (Modifier) -> Unit,
    onClick: () -> Unit,
) {
    Column(modifier.clickable { onClick() }) {
        cover(Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistRow(name: String, detail: String, cover: @Composable (Modifier) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover(Modifier.size(46.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
