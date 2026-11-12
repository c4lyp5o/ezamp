package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.calypsocloud.ezamp.data.Album
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Prefs
import one.calypsocloud.ezamp.data.LibState
import one.calypsocloud.ezamp.data.Track
import one.calypsocloud.ezamp.player.PlayState
import one.calypsocloud.ezamp.player.PlayerHolder

private enum class Tab { Library, Search, Artists, Favourites }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Root(onSignOut: () -> Unit) {
    val lib by LibraryStore.state.collectAsState()
    val play by PlayerHolder.state.collectAsState()

    var tab by remember { mutableStateOf(Tab.Library) }
    var albumId by remember { mutableStateOf<String?>(null) }
    var playlistId by remember { mutableStateOf<String?>(null) }
    var showNow by remember { mutableStateOf(false) }
    var variant by remember { mutableStateOf(Prefs.variant) }
    var playMode by remember { mutableStateOf(Prefs.playMode) }
    var showSettings by remember { mutableStateOf(false) }

    if (showNow && play.active) {
        NowPlaying(play = play, onClose = { showNow = false })
        return
    }

    val imeOpen = WindowInsets.isImeVisible

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // One surface, and the IME inset lives here only. Padding the content as well
            // double-counted the keyboard and left the bar's background floating over the
            // mini player. Navigation is pointless while typing, so it steps aside.
            Column(Modifier.background(Bg).navigationBarsPadding().imePadding()) {
                if (play.active) {
                    MiniPlayer(play, onOpen = { showNow = true }, onToggle = { PlayerHolder.toggle() })
                }
                if (!imeOpen) {
                    NavBar(tab) { t ->
                        albumId = null
                        tab = t
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            when {
                playlistId != null -> PlaylistScreen(
                    playlistId = playlistId!!,
                    play = play,
                    onBack = { playlistId = null },
                )
                albumId != null -> AlbumScreen(
                    albumId = albumId!!,
                    play = play,
                    onBack = { albumId = null },
                )
                tab == Tab.Library -> HomeScreen(
                    lib = lib,
                    play = play,
                    variant = variant,
                    onOpenAlbum = { albumId = it },
                    onOpenPlaylist = { playlistId = it },
                    onSync = { LibraryStore.resync() },
                    onSettings = { showSettings = true },
                )
                tab == Tab.Search -> SearchScreen(play)
                tab == Tab.Artists -> ArtistsScreen(play) { albumId = it }
                else -> FavouritesScreen(lib, play)
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            variant = variant,
            onVariant = { Prefs.variant = it; variant = it },
            playMode = playMode,
            onPlayMode = { Prefs.playMode = it; playMode = it },
            onResync = { LibraryStore.resync() },
            onSignOut = { showSettings = false; onSignOut() },
            onClose = { showSettings = false },
        )
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun HomeScreen(
    lib: LibState,
    play: PlayState,
    variant: String,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
) {
    val art = variant == "art"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Library", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = Ink)
                    if (lib.downloadsOnly) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "OFFLINE",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Accent,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (lib.syncing) "SYNCING" else "SYNC",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = if (lib.syncing) Accent else Dim,
                        modifier = Modifier.clickable { if (!lib.syncing) onSync() }.padding(6.dp),
                    )
                    Text(
                        "SET",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Dim,
                        modifier = Modifier.clickable { onSettings() }.padding(6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        lib.error != null -> lib.error!!
                        lib.albums.isEmpty() && lib.syncing -> "reading your library…"
                        else -> "${lib.albums.size} albums · ${lib.trackCount} tracks"
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (lib.error != null) Ez.Bad else Dim,
                )
                val online by one.calypsocloud.ezamp.data.LibraryStore.online.collectAsState()
                if (!online) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (lib.downloadsOnly) "NO CONNECTION — showing what's on this device"
                        else "NO CONNECTION — tap to browse downloads",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Ez.Warn,
                        modifier = Modifier.clickable(enabled = !lib.downloadsOnly) {
                            one.calypsocloud.ezamp.data.LibraryStore.setDownloadsOnly(true)
                        }.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(if (art) 26.dp else 22.dp))
            }
        }

        if (lib.recent.isNotEmpty()) {
            item {
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
                Spacer(Modifier.height(if (art) 34.dp else 28.dp))
            }
        }

        if (lib.playlists.isNotEmpty()) {
            item {
                SectionLabel("PLAYLISTS")
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (art) 16.dp else 14.dp),
                ) {
                    items(lib.playlists, key = { it.id }) { pl ->
                        PlaylistCard(pl, size = if (art) 156.dp else 132.dp) { onOpenPlaylist(pl.id) }
                    }
                }
                Spacer(Modifier.height(if (art) 34.dp else 28.dp))
            }
        }

        item {
            SectionLabel("ALBUMS")
            Spacer(Modifier.height(if (art) 14.dp else 6.dp))
        }

        if (art) {
            // Art-forward: two comfortable columns, covers doing the talking.
            items(lib.albums.chunked(2), key = { it.first().id }) { row ->
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
            items(lib.albums, key = { it.id }) { a ->
                AlbumRow(a, playing = play.album == a.name, done = lib.doneCounts[a.id] ?: 0) { onOpenAlbum(a.id) }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            PerfFooter(lib)
        }
    }
}

@Composable
private fun AlbumTile(a: Album, playing: Boolean, done: Int = 0, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }) {
        ArtBox(url = a.artUri, label = a.name, corner = 6.dp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            a.name,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = if (playing) Accent else Ink,
            maxLines = 1,
        )
        Text(
            listOfNotNull(
                a.artist.takeIf { it.isNotBlank() },
                a.year?.toString(),
                partialLabel(done, a.trackCount),
            ).joinToString(" · "),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Dim,
            maxLines = 1,
        )
    }
}

/** "3/12" while partially downloaded. Null when untouched, complete, or unknown so rows stay clean. */
private fun partialLabel(done: Int, total: Int): String? {
    if (total <= 0 || done <= 0 || done >= total) return null
    return "$done/$total"
}

@Composable
private fun AlbumCard(a: Album, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable { onClick() }) {
        Art(a.artUri, size = size, corner = 6.dp, label = a.name)
        Spacer(Modifier.height(8.dp))
        Text(
            a.name,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 2,
            minLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            a.artist.ifBlank { a.year?.toString() ?: "" },
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Dim,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumRow(a: Album, playing: Boolean, done: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(a.artUri, size = 46.dp, corner = 4.dp, label = a.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.name,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = if (playing) Accent else Ink,
                maxLines = 1,
            )
            Text(
                listOfNotNull(
                    a.artist.takeIf { it.isNotBlank() },
                    a.year?.toString(),
                    "${a.trackCount} tracks".takeIf { a.trackCount > 0 },
                    partialLabel(done, a.trackCount),
                ).joinToString(" · "),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Dim,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlaylistCard(pl: one.calypsocloud.ezamp.data.Playlist, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).clickable { onClick() }) {
        Art(pl.artUri, size = size, corner = 6.dp, label = pl.name)
        Spacer(Modifier.height(8.dp))
        Text(
            pl.name,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = Ink,
            maxLines = 2,
            minLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${pl.trackCount} tracks",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = Dim,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ playlist

@Composable
private fun PlaylistScreen(playlistId: String, play: PlayState, onBack: () -> Unit) {
    val lib by LibraryStore.state.collectAsState()
    val pl = lib.playlists.firstOrNull { it.id == playlistId }
    var tracks by remember(playlistId) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(playlistId) { tracks = LibraryStore.playlistTracks(playlistId) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dl by one.calypsocloud.ezamp.data.Downloads.state.collectAsState()
    val (done, total) = one.calypsocloud.ezamp.data.DownloadPlan.progress(tracks.map { it.id }.distinct(), dl)
    val busy = one.calypsocloud.ezamp.data.Downloads.playlistBusy(playlistId)
    val atCap = one.calypsocloud.ezamp.data.Downloads.atCap()
    val complete = total > 0 && done >= total
    // No delete here: playlist tracks live under their albums, so removal stays per album in STORAGE.
    val dlLabel = when {
        busy -> "DOWNLOADING $done/$total"
        complete -> "DOWNLOADED $done/$total"
        atCap -> "CAP REACHED"
        done > 0 -> "DOWNLOAD REST $done/$total"
        else -> "DOWNLOAD"
    }
    val dlEnabled = tracks.isNotEmpty() && !complete && (busy || !atCap)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "‹  BACK",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.clickable { onBack() }.padding(vertical = 12.dp),
                )
                Spacer(Modifier.height(2.dp))
                Art(pl?.artUri, size = 200.dp, corner = 10.dp, label = pl?.name)
                Spacer(Modifier.height(16.dp))
                Text(pl?.name ?: "", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${tracks.size} tracks",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PLAY ALL",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = if (tracks.isEmpty()) Mute else Accent,
                        modifier = Modifier
                            .clickable(enabled = tracks.isNotEmpty()) { PlayerHolder.play(tracks, 0) }
                            .padding(vertical = 10.dp),
                    )
                    Spacer(Modifier.width(26.dp))
                    Text(
                        dlLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            .copy(fontFeatureSettings = "tnum"),
                        color = when {
                            complete -> Dim
                            dlEnabled -> Accent
                            else -> Mute
                        },
                        modifier = Modifier
                            .clickable(enabled = dlEnabled) {
                                if (busy) one.calypsocloud.ezamp.data.Downloads.cancelPlaylist(ctx, playlistId)
                                else one.calypsocloud.ezamp.data.Downloads.downloadPlaylist(ctx, playlistId, tracks)
                            }
                            .padding(vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, playing = play.itemId == t.id) { PlayerHolder.play(tracks, i) }
        }
    }
}

// ------------------------------------------------------------------ artists

@Composable
private fun ArtistsScreen(play: PlayState, onOpenAlbum: (String) -> Unit) {
    var artists by remember { mutableStateOf<List<one.calypsocloud.ezamp.data.Artist>>(emptyList()) }
    var open by remember { mutableStateOf<String?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var needResync by remember { mutableStateOf(false) }
    val lib by LibraryStore.state.collectAsState()

    // Keyed on the last sync so the list refills by itself once a resync lands.
    LaunchedEffect(lib.lastSync) {
        artists = LibraryStore.artists()
        needResync = LibraryStore.artistsNeedResync()
    }
    LaunchedEffect(open) {
        albums = open?.let { LibraryStore.albumsByArtist(it) } ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
            Text(
                if (open == null) "ARTISTS" else "ARTIST",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            Spacer(Modifier.height(10.dp))
            if (open == null) {
                Text("Artists", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(6.dp))
                if (needResync) {
                    Text(
                        "Featured artists arrive with the next sync — tap SYNC on Library.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Ez.Warn,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "${artists.size} artists",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
            } else {
                Text(
                    "‹  BACK",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.clickable { open = null }.padding(vertical = 6.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(open ?: "", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${albums.size} albums",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (open == null) {
                items(artists, key = { it.name }) { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = a.name }
                            .padding(horizontal = 22.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            "${a.albumCount}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = Mute,
                        )
                    }
                }
            } else {
                items(albums, key = { it.id }) { a ->
                    AlbumRow(a, playing = play.album == a.name) { onOpenAlbum(a.id) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ------------------------------------------------------------------ settings

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    variant: String,
    onVariant: (String) -> Unit,
    playMode: String,
    onPlayMode: (String) -> Unit,
    onResync: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dl by one.calypsocloud.ezamp.data.Downloads.state.collectAsState()
    val used by one.calypsocloud.ezamp.data.Downloads.used.collectAsState()
    var capMb by remember { mutableStateOf(Prefs.storageCapMb) }
    var usage by remember {
        mutableStateOf<List<one.calypsocloud.ezamp.data.Downloads.AlbumUsage>>(emptyList())
    }
    LaunchedEffect(dl.size, used) { usage = one.calypsocloud.ezamp.data.Downloads.albumUsage() }
    androidx.compose.material3.ModalBottomSheet(
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
            Text("SETTINGS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Mute)
            Spacer(Modifier.height(22.dp))

            Text("LAYOUT", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(4.dp))
            ChoiceRow(
                title = "Art-forward",
                detail = "big covers, two columns, more air",
                selected = variant == "art",
            ) { onVariant("art") }
            ChoiceRow(
                title = "Classic",
                detail = "dense list, small covers",
                selected = variant == "classic",
            ) { onVariant("classic") }

            Spacer(Modifier.height(22.dp))
            Text("PLAYBACK", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(4.dp))
            ChoiceRow(
                title = "Direct",
                detail = "server's own file · lossless · fastest start",
                selected = playMode == "direct",
            ) { onPlayMode("direct") }
            ChoiceRow(
                title = "Saver",
                detail = "transcoded mp3 ${Prefs.saverKbps}k · downloads follow this",
                selected = playMode == "saver",
            ) { onPlayMode("saver") }

            Spacer(Modifier.height(22.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            Text("OFFLINE", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(4.dp))
            val lib by one.calypsocloud.ezamp.data.LibraryStore.state.collectAsState()
            ChoiceRow(
                title = "Downloaded only",
                detail = "hide everything not on this device",
                selected = lib.downloadsOnly,
            ) { one.calypsocloud.ezamp.data.LibraryStore.setDownloadsOnly(!lib.downloadsOnly) }

            Spacer(Modifier.height(22.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            Text("SERVER", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(8.dp))
            Text(
                Prefs.baseUrl ?: "—",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Text(
                listOfNotNull(
                    Prefs.serverName,
                    Prefs.serverVersion?.let { "Jellyfin $it" },
                    Prefs.userName,
                ).joinToString(" · ").ifBlank { "—" },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Mute,
            )

            Spacer(Modifier.height(22.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            Text("STORAGE", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(8.dp))
            val capBytes = one.calypsocloud.ezamp.data.StoragePolicy.capBytes(capMb)
            Text(
                when {
                    capBytes <= 0L -> "${sizeOf(used)} on device · no cap"
                    used > capBytes -> "${sizeOf(used)} of ${sizeOf(capBytes)} · OVER CAP"
                    else -> "${sizeOf(used)} of ${sizeOf(capBytes)}"
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = when {
                    used > capBytes && capBytes > 0L -> Ez.Bad
                    used > 0 -> Ez.Ok
                    else -> Mute
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(1024, 2048, 4096, 8192).forEach { mb ->
                    val selected = capMb == mb
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .border(1.dp, if (selected) Accent else Ez.Line, RoundedCornerShape(6.dp))
                            .clickable { Prefs.storageCapMb = mb; capMb = mb }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${mb / 1024} GB",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = if (selected) Accent else Mute,
                        )
                    }
                }
            }

            if (usage.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "NOTHING DOWNLOADED",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Mute,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                usage.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                u.name.ifBlank { "—" },
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${u.tracks} tracks",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = Mute,
                            )
                        }
                        Text(
                            sizeOf(u.bytes),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = Dim,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Text(
                            "DELETE",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Ez.Bad,
                            modifier = Modifier
                                .clickable { one.calypsocloud.ezamp.data.Downloads.deleteAlbum(ctx, u.albumId) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                        )
                    }
                }
                if (usage.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "DELETE EVERYTHING",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Ez.Bad,
                        modifier = Modifier
                            .clickable { one.calypsocloud.ezamp.data.Downloads.deleteAll(ctx) }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "RESYNC",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.clickable { onResync(); onClose() }.padding(vertical = 8.dp, horizontal = 2.dp),
                )
                Spacer(Modifier.width(26.dp))
                Text(
                    "SIGN OUT",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = Ez.Bad,
                    modifier = Modifier.clickable { onSignOut() }.padding(vertical = 8.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = if (selected) Accent else Ink,
            )
            Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Mute)
        }
        Text(
            if (selected) "●" else "○",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = if (selected) Accent else Ez.LineStrong,
        )
    }
}

// ------------------------------------------------------------------ search

@Composable
private fun SearchScreen(play: PlayState) {
    val lib by LibraryStore.state.collectAsState()
    var q by remember { mutableStateOf("") }

    LaunchedEffect(q) { LibraryStore.search(q) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
            Text("SEARCH", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) {
                        Text(
                            "Songs, artists, albums",
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF3A3A3E),
                        )
                    }
                    BasicTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        textStyle = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(color = Ink),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Hairline()
            Spacer(Modifier.height(8.dp))
            Text(
                if (q.isBlank()) "${lib.trackCount} tracks indexed locally"
                else "${lib.results.size} results · ${lib.searchMs} ms · on-device",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(lib.results, key = { it.id }) { t ->
                TrackRow(t, playing = play.itemId == t.id) {
                    PlayerHolder.play(lib.results, lib.results.indexOf(t))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ favourites

@Composable
private fun FavouritesScreen(lib: LibState, play: PlayState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                Text("FAVOURITES", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
                Spacer(Modifier.height(10.dp))
                Text("Loved", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (lib.favourites.isEmpty()) "nothing yet" else "${lib.favourites.size} tracks",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
                Spacer(Modifier.height(18.dp))
            }
        }
        itemsIndexed(lib.favourites, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, playing = play.itemId == t.id) {
                PlayerHolder.play(lib.favourites, i)
            }
        }
    }
}

// ------------------------------------------------------------------ album

@Composable
private fun AlbumScreen(albumId: String, play: PlayState, onBack: () -> Unit) {
    val lib by LibraryStore.state.collectAsState()
    val album = lib.albums.firstOrNull { it.id == albumId }
    var tracks by remember(albumId) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(albumId) { tracks = LibraryStore.albumTracks(albumId) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dl by one.calypsocloud.ezamp.data.Downloads.state.collectAsState()
    val rows = dl.values.filter { it.albumId == albumId }
    val done = rows.count { it.state == one.calypsocloud.ezamp.data.Downloads.DONE }
    val running = rows.count { it.state == one.calypsocloud.ezamp.data.Downloads.RUNNING }
    val queued = rows.count { it.state == one.calypsocloud.ezamp.data.Downloads.QUEUED }
    val failed = rows.count { it.state == one.calypsocloud.ezamp.data.Downloads.FAILED }
    val busy = running + queued > 0
    val atCap = one.calypsocloud.ezamp.data.Downloads.atCap()
    val dlLabel = when {
        busy -> "DOWNLOADING ${done}/${tracks.size}"
        tracks.isNotEmpty() && done >= tracks.size -> "DOWNLOADED ${done}/${tracks.size}"
        failed > 0 -> "RETRY ${failed}"
        done > 0 -> "DOWNLOADED ${done}/${tracks.size}"
        atCap -> "CAP REACHED"
        else -> "DOWNLOAD"
    }
    val dlAction: () -> Unit = {
        when {
            busy -> one.calypsocloud.ezamp.data.Downloads.cancelAlbum(ctx, albumId)
            tracks.isNotEmpty() && done >= tracks.size ->
                one.calypsocloud.ezamp.data.Downloads.deleteAlbum(ctx, albumId)
            else -> one.calypsocloud.ezamp.data.Downloads.downloadAlbum(ctx, albumId, tracks)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "‹  BACK",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Dim,
                    modifier = Modifier.clickable { onBack() }.padding(vertical = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
                Art(album?.artUri, size = 232.dp, corner = 10.dp, label = album?.name)
                Spacer(Modifier.height(18.dp))
                Text(
                    album?.name ?: "",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        album?.artist?.takeIf { it.isNotBlank() },
                        album?.year?.toString(),
                        "${tracks.size} tracks",
                    ).joinToString(" · "),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayPause(
                        playing = play.playing && play.album == (album?.name ?: ""),
                        size = 44.dp,
                        tint = Accent,
                    ) {
                        if (play.album == (album?.name ?: "") && play.active) PlayerHolder.toggle()
                        else PlayerHolder.play(tracks, 0)
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(
                        "PLAY",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = Accent,
                        modifier = Modifier.clickable { PlayerHolder.play(tracks, 0) },
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        dlLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = when {
                            busy -> Ez.Warn
                            atCap && done == 0 -> Ez.Bad
                            done > 0 && !busy -> Ez.Ok
                            else -> Dim
                        },
                        modifier = Modifier
                            .clickable(enabled = tracks.isNotEmpty() && !(atCap && done == 0 && !busy)) { dlAction() }
                            .padding(vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (play.notice != null) {
                    Text(
                        play.notice!!,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Ez.Warn,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Hairline()
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { PlayerHolder.play(tracks, i) }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (t.trackNo ?: (i + 1)).toString().padStart(2, '0'),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Dim,
                    modifier = Modifier.width(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        t.name,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = if (play.itemId == t.id) Accent else Ink,
                        maxLines = 1,
                    )
                    if (t.artist.isNotBlank() && t.artist != album?.artist) {
                        Text(
                            t.artist,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = Dim,
                            maxLines = 1,
                        )
                    }
                }
                var fav by remember(t.id, t.favourite) { mutableStateOf(t.favourite) }
                Text(
                    if (fav) "♥" else "♡",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = if (fav) Accent else Ez.LineStrong,
                    modifier = Modifier
                        .clickable {
                            fav = !fav
                            LibraryStore.toggleFavourite(t.id, fav)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    fmt(t.durationMs),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ shared bits

@Composable
fun TrackRow(t: Track, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(t.artUri, size = 44.dp, corner = 5.dp, label = t.name)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.name,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = if (playing) Accent else Ink,
                maxLines = 1,
            )
            Text(
                listOfNotNull(t.artist.takeIf { it.isNotBlank() }, t.album.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = Dim,
                maxLines = 1,
            )
        }
        var fav by remember(t.id, t.favourite) { mutableStateOf(t.favourite) }
        Text(
            if (fav) "♥" else "♡",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = if (fav) Accent else Ez.LineStrong,
            modifier = Modifier
                .clickable {
                    fav = !fav
                    LibraryStore.toggleFavourite(t.id, fav)
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            fmt(t.durationMs),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = Dim,
        )
    }
}

@Composable
private fun MiniPlayer(play: PlayState, onOpen: () -> Unit, onToggle: () -> Unit) {
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    play.title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                Text(
                    play.artist,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Dim,
                    maxLines = 1,
                )
            }
            if (play.transcoding) {
                Text(
                    "⇄",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Mute,
                    modifier = Modifier
                        .border(1.dp, Ez.Line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            PlayPause(playing = play.playing, size = 36.dp) { onToggle() }
            Spacer(Modifier.width(10.dp))
            SkipGlyph(next = true, size = 28.dp) { PlayerHolder.next() }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun NavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Tab.entries.forEach { t ->
                val on = t == current
                Text(
                    t.name.uppercase(),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = if (on) Ink else Dim,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clickable { onSelect(t) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = Dim,
        modifier = Modifier.padding(horizontal = 22.dp),
    )
}

@Composable
private fun PerfFooter(lib: LibState) {
    Column(Modifier.padding(horizontal = 22.dp)) {
        Hairline()
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Dim)) { append("cold ") }
                withStyle(SpanStyle(color = Ink)) { append("${lib.coldStartMs} ms") }
                withStyle(SpanStyle(color = Dim)) { append("   sync ") }
                withStyle(SpanStyle(color = Ink)) { append("${lib.syncMs} ms") }
                withStyle(SpanStyle(color = Dim)) { append("   tap→audio ") }
                withStyle(SpanStyle(color = Ink)) { append(if (lib.firstAudioMs > 0) "${lib.firstAudioMs} ms" else "—") }
            },
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
