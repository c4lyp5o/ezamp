package one.calypsocloud.ezamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

private enum class Tab { Library, Playlists }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Root(onSignOut: () -> Unit) {
    val lib by LibraryStore.state.collectAsState()
    val play by PlayerHolder.state.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.Library) }
    var albumId by remember { mutableStateOf<String?>(null) }
    var playlistId by remember { mutableStateOf<String?>(null) }
    var artistOpen by remember { mutableStateOf<String?>(null) }
    var genreOpen by remember { mutableStateOf<String?>(null) }
    // Favourites is a playlist now: pinned first in PLAYLISTS, opened like any other.
    var favouritesOpen by remember { mutableStateOf(false) }
    var showNow by remember { mutableStateOf(false) }
    var variant by remember { mutableStateOf(Prefs.variant) }
    var playMode by remember { mutableStateOf(Prefs.playMode) }
    var showSettings by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }

    // Library carousel, search and scroll positions live here so an album round trip keeps them.
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = Prefs.libPage.coerceIn(0, LibPage.entries.lastIndex),
    ) { LibPage.entries.size }
    LaunchedEffect(pager.settledPage) { Prefs.libPage = pager.settledPage }
    val page = LibPage.entries[pager.currentPage]
    var query by remember { mutableStateOf("") }
    val albumsList = rememberLazyListState()
    val tracksList = rememberLazyListState()
    val artistsList = rememberLazyListState()
    val genresList = rememberLazyListState()
    val lists = remember(albumsList, tracksList, artistsList, genresList) {
        LibLists(albumsList, tracksList, artistsList, genresList)
    }
    val playlistsList = rememberLazyListState()
    val favList = rememberLazyListState()

    // Artists are one row per name, so load them eagerly; every track only while TRACKS is open,
    // and again only when the sync, the offline scope or a favourite changed underneath.
    var artists by remember { mutableStateOf<List<one.calypsocloud.ezamp.data.Artist>>(emptyList()) }
    var artistsNeedResync by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf<List<one.calypsocloud.ezamp.data.Genre>>(emptyList()) }
    var genresNeedResync by remember { mutableStateOf(false) }
    LaunchedEffect(lib.lastSync, lib.downloadsOnly) {
        artists = LibraryStore.artists()
        artistsNeedResync = LibraryStore.artistsNeedResync()
        genres = LibraryStore.genres()
        genresNeedResync = LibraryStore.genresNeedResync()
    }
    var allTracks by remember { mutableStateOf<List<Track>?>(null) }
    var tracksLoadedFor by remember { mutableStateOf<Any?>(null) }
    val tracksKey = Triple(lib.lastSync, lib.downloadsOnly, lib.favourites)
    val wantTracks = tab == Tab.Library && page == LibPage.Tracks
    LaunchedEffect(wantTracks, tracksKey) {
        if (wantTracks && tracksLoadedFor != tracksKey) {
            allTracks = LibraryStore.allTracks()
            tracksLoadedFor = tracksKey
        }
    }

    // Back peels one layer at a time: player, detail screens, other tabs, the search, then the
    // carousel back to ALBUMS. Only a bare Library lets the system take it to the launcher.
    // The settings sheet handles its own back through onDismissRequest.
    val nowOpen = showNow && play.active
    val bareLibrary = tab == Tab.Library && playlistId == null && !favouritesOpen && albumId == null &&
        artistOpen == null && genreOpen == null && query.isEmpty() && pager.currentPage == 0
    androidx.activity.compose.BackHandler(enabled = nowOpen || !bareLibrary) {
        when {
            nowOpen -> showNow = false
            playlistId != null -> playlistId = null
            favouritesOpen -> favouritesOpen = false
            albumId != null -> albumId = null
            artistOpen != null -> artistOpen = null
            genreOpen != null -> genreOpen = null
            tab != Tab.Library -> tab = Tab.Library
            query.isNotEmpty() -> query = ""
            else -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    if (nowOpen) {
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
                        val atRoot = albumId == null && playlistId == null && !favouritesOpen &&
                            artistOpen == null && genreOpen == null
                        if (t == tab && atRoot) {
                            // Tapping the tab you are already on goes back to the top of its list.
                            val list = when (t) {
                                Tab.Library -> lists.forPage(page)
                                Tab.Playlists -> playlistsList
                            }
                            scope.launch { list.animateScrollToItem(0) }
                        }
                        albumId = null
                        playlistId = null
                        favouritesOpen = false
                        artistOpen = null
                        genreOpen = null
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
                favouritesOpen -> FavouritesScreen(lib, play, favList) { favouritesOpen = false }
                albumId != null -> AlbumScreen(
                    albumId = albumId!!,
                    play = play,
                    onBack = { albumId = null },
                )
                artistOpen != null -> ArtistScreen(
                    name = artistOpen!!,
                    play = play,
                    onBack = { artistOpen = null },
                    onOpenAlbum = { albumId = it },
                )
                genreOpen != null -> GenreScreen(
                    name = genreOpen!!,
                    play = play,
                    onBack = { genreOpen = null },
                )
                tab == Tab.Library -> LibraryScreen(
                    lib = lib,
                    play = play,
                    variant = variant,
                    pager = pager,
                    query = query,
                    onQuery = { query = it },
                    lists = lists,
                    allTracks = allTracks,
                    artists = artists,
                    artistsNeedResync = artistsNeedResync,
                    genres = genres,
                    genresNeedResync = genresNeedResync,
                    onPage = { scope.launch { pager.animateScrollToPage(it) } },
                    onOpenAlbum = { albumId = it },
                    onOpenArtist = { artistOpen = it },
                    onOpenGenre = { genreOpen = it },
                    onSync = { LibraryStore.resync() },
                    onSettings = { showSettings = true },
                )
                else -> PlaylistsScreen(
                    lib = lib,
                    art = variant == "art",
                    state = playlistsList,
                    onOpenFavourites = { favouritesOpen = true },
                    onOpen = { playlistId = it },
                    onNew = { newPlaylist = true },
                )
            }
        }
    }

    heldTrack.value?.let { held -> TrackSheet(held) { heldTrack.value = null } }
    if (newPlaylist) {
        NewPlaylistSheet(
            onCreated = { id -> newPlaylist = false; playlistId = id },
            onClose = { newPlaylist = false },
        )
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

@Composable
internal fun AlbumTile(a: Album, playing: Boolean, done: Int = 0, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun AlbumCard(a: Album, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
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
internal fun AlbumRow(a: Album, playing: Boolean, done: Int = 0, onClick: () -> Unit) {
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
                    plural("track", a.trackCount).takeIf { a.trackCount > 0 },
                    partialLabel(done, a.trackCount),
                ).joinToString(" · "),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Dim,
                maxLines = 1,
            )
        }
    }
}

// ------------------------------------------------------------------ playlist

@Composable
private fun PlaylistScreen(playlistId: String, play: PlayState, onBack: () -> Unit) {
    val lib by LibraryStore.state.collectAsState()
    val pl = (lib.localPlaylists + lib.playlists).firstOrNull { it.id == playlistId }
    val local = LibraryStore.isLocal(playlistId)
    var tracks by remember(playlistId) { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(playlistId, lib.localVersion) { tracks = LibraryStore.playlistTracks(playlistId) }
    val scope = rememberCoroutineScope()
    var renaming by remember(playlistId) { mutableStateOf(false) }
    var confirmDelete by remember(playlistId) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            kotlinx.coroutines.delay(3_000)
            confirmDelete = false
        }
    }

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
                Art(pl?.artUri?.ifEmpty { null }, size = 200.dp, corner = 10.dp, label = pl?.name)
                Spacer(Modifier.height(16.dp))
                if (renaming) {
                    NameField(initial = pl?.name.orEmpty(), action = "SAVE") { name ->
                        renaming = false
                        scope.launch {
                            try {
                                LibraryStore.renameLocal(playlistId, name)
                            } catch (e: Exception) {
                                android.util.Log.w("ezamp-lists", "rename failed: ${e.message}")
                            }
                        }
                    }
                } else {
                    Text(pl?.name ?: "", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Ink)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (local && tracks.isEmpty()) "nothing yet — hold any track to add it" else plural("track", tracks.size),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = Dim,
                )
                if (local && !renaming) {
                    Row {
                        Text(
                            "RENAME",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Dim,
                            modifier = Modifier.clickable { renaming = true }.padding(top = 10.dp, bottom = 6.dp, end = 22.dp),
                        )
                        Text(
                            if (confirmDelete) "TAP AGAIN TO DELETE" else "DELETE",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = if (confirmDelete) Ez.Bad else Dim,
                            modifier = Modifier
                                .clickable {
                                    if (!confirmDelete) {
                                        confirmDelete = true
                                    } else {
                                        scope.launch {
                                            try {
                                                LibraryStore.removeLocal(playlistId)
                                                onBack()
                                            } catch (e: Exception) {
                                                android.util.Log.w("ezamp-lists", "delete failed: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                .padding(top = 10.dp, bottom = 6.dp),
                        )
                    }
                }
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
                        "SHUFFLE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = if (tracks.size > 1) Accent else Mute,
                        modifier = Modifier
                            .clickable(enabled = tracks.size > 1) { PlayerHolder.playShuffled(tracks) }
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
            TrackRow(t, playing = play.itemId == t.id, playlistId = playlistId.takeIf { local }) { PlayerHolder.play(tracks, i) }
        }
    }
}

// ------------------------------------------------------------------ settings

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                selected = variant == "art",
            ) { onVariant("art") }
            ChoiceRow(
                title = "Classic",
                selected = variant == "classic",
            ) { onVariant("classic") }

            Spacer(Modifier.height(22.dp))
            Text("PLAYBACK", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(4.dp))
            ChoiceRow(
                title = "Direct",
                detail = "lossless",
                selected = playMode == "direct",
            ) { onPlayMode("direct") }
            ChoiceRow(
                title = "Saver",
                detail = "transcoded mp3 ${Prefs.saverKbps}k",
                selected = playMode == "saver",
            ) { onPlayMode("saver") }

            Spacer(Modifier.height(22.dp))
            SoundSection()

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
            FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4096, 8192, 16384, 32768, 65536, 0).forEach { mb ->
                    val selected = capMb == mb
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .border(1.dp, if (selected) Accent else Ez.Line, RoundedCornerShape(6.dp))
                            .clickable { Prefs.storageCapMb = mb; capMb = mb }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (mb == 0) "UNLIMITED" else "${mb / 1024} GB",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = if (selected) Accent else Mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                                plural("track", u.tracks),
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
internal fun ChoiceRow(title: String, detail: String = "", selected: Boolean, onClick: () -> Unit) {
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
            if (detail.isNotEmpty()) {
                Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Mute)
            }
        }
        Text(
            if (selected) "●" else "○",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = if (selected) Accent else Ez.LineStrong,
        )
    }
}

// ------------------------------------------------------------------ favourites

@Composable
private fun FavouritesScreen(
    lib: LibState,
    play: PlayState,
    state: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
) {
    val tracks = lib.favourites
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 32.dp)) {
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
                FavouritesArt(Modifier.size(200.dp), corner = 10.dp)
                Spacer(Modifier.height(16.dp))
                Text("Favourites", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (tracks.isEmpty()) "nothing yet — tap ♡ on any track" else plural("track", tracks.size),
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
                        "SHUFFLE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = if (tracks.size > 1) Accent else Mute,
                        modifier = Modifier
                            .clickable(enabled = tracks.size > 1) { PlayerHolder.playShuffled(tracks) }
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
                        plural("track", tracks.size),
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
                        "SHUFFLE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = if (tracks.size > 1) Accent else Mute,
                        modifier = Modifier
                            .clickable(enabled = tracks.size > 1) { PlayerHolder.playShuffled(tracks) }
                            .padding(vertical = 10.dp),
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
                    .trackPress(t, null) { PlayerHolder.play(tracks, i) }
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
                    if (fav) "\u2665\uFE0E" else "\u2661\uFE0E",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = if (fav) Accent else Ez.LineStrong,
                    modifier = Modifier
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            fav = !fav
                            LibraryStore.toggleFavourite(t.id, fav)
                        }
                        .semantics { contentDescription = if (fav) "Remove from favourites" else "Add to favourites" }
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
fun TrackRow(t: Track, playing: Boolean, playlistId: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .trackPress(t, playlistId, onClick)
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
            if (fav) "\u2665\uFE0E" else "\u2661\uFE0E",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = if (fav) Accent else Ez.LineStrong,
            modifier = Modifier
                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    fav = !fav
                    LibraryStore.toggleFavourite(t.id, fav)
                }
                .semantics { contentDescription = if (fav) "Remove from favourites" else "Add to favourites" }
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
    // Swipe left for next, right for previous; the row follows the finger and springs back.
    var slide by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val skipAt = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val tap = rememberTap()
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(slide.toInt(), 0) }
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { dx -> slide += dx },
                    onDragStopped = {
                        when {
                            slide < -skipAt -> { tap(); PlayerHolder.next() }
                            slide > skipAt -> { tap(); PlayerHolder.prev() }
                        }
                        scope.launch { androidx.compose.animation.core.animate(slide, 0f) { v, _ -> slide = v } }
                    },
                )
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
            // Both buttons pad themselves out to 48 dp touch targets, so no spacers between them.
            PlayPause(playing = play.playing, size = 36.dp) { onToggle() }
            SkipGlyph(next = true, size = 28.dp) { PlayerHolder.next() }
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
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = Dim,
        modifier = Modifier.padding(horizontal = 22.dp),
    )
}
