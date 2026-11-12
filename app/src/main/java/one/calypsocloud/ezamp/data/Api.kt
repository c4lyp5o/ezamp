package one.calypsocloud.ezamp.data

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------------------------------------------------------------- DTOs (PascalCase, Jellyfin wire format)

@Serializable
data class UserDto(
    val Id: String,
    val Name: String = "",
    val HasPassword: Boolean = false,
)

@Serializable
data class UserDataDto(
    val IsFavorite: Boolean = false,
    val PlaybackPositionTicks: Long = 0,
    val PlayCount: Int = 0,
)

@Serializable
data class ItemDto(
    val Id: String,
    val Name: String = "",
    val Album: String? = null,
    val AlbumId: String? = null,
    val AlbumArtist: String? = null,
    val AlbumPrimaryImageTag: String? = null,
    val PrimaryImageTag: String? = null,
    val Artists: List<String> = emptyList(),
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val ChildCount: Int? = null,
    val ProductionYear: Int? = null,
    val RunTimeTicks: Long? = null,
    val Container: String? = null,
    val DateCreated: String? = null,
    val Etag: String? = null,
    val SortName: String? = null,
    val UserData: UserDataDto? = null,
)

@Serializable
data class ItemsResponse(
    val Items: List<ItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
)

@Serializable
data class PublicSystemInfo(
    val ServerName: String = "",
    val Version: String = "",
    val ProductName: String = "",
    val Id: String = "",
)

@Serializable
private data class AuthRequest(val Username: String, val Pw: String)

@Serializable
data class AuthResult(
    val User: UserDto,
    val AccessToken: String = "",
    val ServerId: String? = null,
)

// ---------------------------------------------------------------- persisted settings

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("ezamp", Context.MODE_PRIVATE)
    }

    var baseUrl: String?
        get() = sp.getString("base", null)
        set(v) = sp.edit().putString("base", v).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var userId: String?
        get() = sp.getString("uid", null)
        set(v) = sp.edit().putString("uid", v).apply()

    var userName: String?
        get() = sp.getString("uname", null)
        set(v) = sp.edit().putString("uname", v).apply()

    var lastSync: String?
        get() = sp.getString("lastsync", null)
        set(v) = sp.edit().putString("lastsync", v).apply()

    var serverName: String?
        get() = sp.getString("srvname", null)
        set(v) = sp.edit().putString("srvname", v).apply()

    var serverVersion: String?
        get() = sp.getString("srvver", null)
        set(v) = sp.edit().putString("srvver", v).apply()

    /** "art" = art-forward layout, "classic" = the dense list layout. Switchable at any time. */
    var variant: String
        get() = sp.getString("variant", "art") ?: "art"
        set(v) = sp.edit().putString("variant", v).apply()

    /**
     * "direct" plays the file as it sits on the server; "saver" asks Jellyfin to transcode to mp3.
     * Downloads follow the same setting, so one switch governs both bytes and storage.
     */
    var playMode: String
        get() = sp.getString("playmode", "direct") ?: "direct"
        set(v) = sp.edit().putString("playmode", v).apply()

    var saverKbps: Int
        get() = sp.getInt("saverkbps", 192)
        set(v) = sp.edit().putInt("saverkbps", v).apply()

    /**
     * Cap for offline copies, in MB. 0 disables it. The cap refuses new downloads rather than
     * evicting old ones — see [StoragePolicy].
     */
    var storageCapMb: Int
        get() = sp.getInt("storagemb", 4096)
        set(v) = sp.edit().putInt("storagemb", v).apply()

    /**
     * Offline-only scope: every screen reads downloaded tracks only. Applied in SQL, not in RAM —
     * a filter in the UI would still load the full index to throw most of it away. One flag, one
     * set of queries, everything else just reads.
     */
    var downloadsOnly: Boolean
        get() = sp.getBoolean("dlonly", false)
        set(v) = sp.edit().putBoolean("dlonly", v).apply()

    /** Stable per-install id; Jellyfin ties sessions and playback reports to it. */
    val deviceId: String
        get() = sp.getString("deviceid", null) ?: java.util.UUID.randomUUID().toString().also {
            sp.edit().putString("deviceid", it).apply()
        }

    fun reset() = sp.edit().clear().apply()
}

// ---------------------------------------------------------------- onboarding: probe + sign-in

object Auth {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 15_000
        }
    }

    fun normalize(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        return u.trimEnd('/')
    }

    /** Server errors belong to the server, not to Ktor's exception text. */
    private fun humanize(base: String, e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Can't find a server at that address"
        is java.net.ConnectException -> "Nothing is listening at $base"
        is java.net.SocketTimeoutException -> "No answer from $base"
        else -> {
            val msg = e.message ?: ""
            when {
                msg.contains("Connect timeout", true) -> "No answer from $base — right address, and is Jellyfin running?"
                msg.contains("timeout", true) -> "$base took too long to answer"
                msg.contains("Unable to resolve host", true) -> "Can't find a server at that address"
                msg.contains("Failed to connect", true) -> "Nothing is listening at $base"
                else -> "Couldn't reach $base"
            }
        }
    }

    private fun deviceHeader(): String =
        "MediaBrowser Client=\"ezamp\", Device=\"android\", DeviceId=\"${Prefs.deviceId}\", Version=\"0.1.0\""

    /** Step 1 — does this address answer as a Jellyfin server? Anonymous endpoint, no credentials. */
    suspend fun probe(rawUrl: String): PublicSystemInfo {
        val base = normalize(rawUrl)
        if (base.isEmpty()) throw IllegalStateException("Enter your server address")
        val r = try {
            client.get("$base/System/Info/Public")
        } catch (e: Exception) {
            throw IllegalStateException(humanize(base, e))
        }
        if (r.status.value != 200) throw IllegalStateException("$base answered ${r.status.value}, not Jellyfin")
        val info = try {
            r.body<PublicSystemInfo>()
        } catch (e: Exception) {
            throw IllegalStateException("$base answered, but not with Jellyfin data")
        }
        if (!info.ProductName.contains("jellyfin", ignoreCase = true)) {
            val what = info.ProductName.ifBlank { "something else" }
            throw IllegalStateException("That's $what, not Jellyfin")
        }
        return info
    }

    /** Step 2 — username + password to an access token. */
    suspend fun login(rawUrl: String, username: String, password: String): AuthResult {
        val base = normalize(rawUrl)
        if (username.isBlank()) throw IllegalStateException("Enter your username")
        val r = try {
            client.post("$base/Users/AuthenticateByName") {
                header("Authorization", deviceHeader())
                contentType(ContentType.Application.Json)
                setBody(AuthRequest(username, password))
            }
        } catch (e: Exception) {
            throw IllegalStateException(humanize(base, e))
        }
        when (r.status.value) {
            200 -> Unit
            401, 403 -> throw IllegalStateException("Wrong username or password")
            else -> throw IllegalStateException("Sign-in failed (${r.status.value})")
        }
        val res = r.body<AuthResult>()
        if (res.AccessToken.isBlank()) throw IllegalStateException("Server returned no token")
        return res
    }
}

// ---------------------------------------------------------------- API

class JellyfinApi(val baseUrl: String, val token: String) {

    val client: HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 60_000
        }
    }

    val authHeader: String = "MediaBrowser Token=\"$token\", Client=\"ezamp\", Device=\"android\", DeviceId=\"ezamp\", Version=\"0.1.0\""

    suspend fun users(): List<UserDto> =
        client.get("$baseUrl/Users") { header("Authorization", authHeader) }.body()

    /**
     * Jellyfin's play-state endpoints. Without these the server thinks nothing is ever played:
     * no play counts, no "continue listening", no resume positions from other clients.
     */
    suspend fun reportPlayback(
        event: String,
        itemId: String,
        positionMs: Long,
        isPaused: Boolean,
    ): Int {
        val body = buildJsonObject {
            put("ItemId", itemId)
            put("PositionTicks", positionMs * 10_000L)
            put("IsPaused", isPaused)
            put("IsMuted", false)
            put("CanSeek", true)
            put("PlayMethod", "DirectPlay")
            Prefs.deviceId?.let { put("PlaySessionId", it) }
        }
        val path = when (event) {
            "start" -> "/Sessions/Playing"
            "progress" -> "/Sessions/Playing/Progress"
            else -> "/Sessions/Playing/Stopped"
        }
        val res = client.post("$baseUrl$path") {
            header("Authorization", authHeader)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return res.status.value
    }

    /** Playlist membership is NOT a folder query: /Items?ParentId returns nothing for playlists. */
    suspend fun playlistItems(
        userId: String,
        playlistId: String,
        limit: Int = 1000,
        fields: String = "RunTimeTicks,AlbumId,Artists,IndexNumber,ParentIndexNumber,MediaSources,Etag,AlbumPrimaryImageTag,SortName",
    ): ItemsResponse =
        client.get("$baseUrl/Playlists/$playlistId/Items") {
            header("Authorization", authHeader)
            parameter("userId", userId)
            parameter("Limit", limit)
            parameter("Fields", fields)
        }.body()

    suspend fun items(
        userId: String,
        types: String,
        limit: Int = 500,
        start: Int = 0,
        fields: String = "RunTimeTicks,AlbumId,Artists,IndexNumber,ParentIndexNumber,MediaSources,Etag,AlbumPrimaryImageTag,DateCreated",
        sortBy: String = "SortName",
        minDate: String? = null,
        filters: String? = null,
        parentId: String? = null,
        recursive: Boolean = true,
    ): ItemsResponse =
        client.get("$baseUrl/Items") {
            header("Authorization", authHeader)
            parameter("userId", userId)
            parameter("IncludeItemTypes", types)
            parameter("Recursive", if (recursive) "true" else "false")
            parameter("Limit", limit)
            parameter("StartIndex", start)
            parameter("Fields", fields)
            parameter("SortBy", sortBy)
            parameter("EnableImages", "false")
            parameter("EnableUserData", "true")
            parameter("EnableTotalRecordCount", "false")
            if (minDate != null) parameter("MinDateLastSaved", minDate)
            if (filters != null) parameter("Filters", filters)
            if (parentId != null) parameter("ParentId", parentId)
        }.body()

    /** Direct-play URL. Auth travels in a header on the data source, never in the URL. */
    fun streamUrl(id: String, container: String?): String {
        val ext = container?.lowercase()?.takeIf { it in DIRECT_CONTAINERS }
        val path = if (ext != null) "$baseUrl/Audio/$id/stream.$ext" else "$baseUrl/Audio/$id/stream"
        return "$path?static=true&mediaSourceId=$id"
    }

    /**
     * Progressive mp3 transcode. `static=false` makes the server run ffmpeg; `startTimeTicks`
     * restarts the transcode at an offset, which is the only way to seek a progressive transcode
     * (Jellyfin does not serve Range on transcoded output).
     */
    fun saverUrl(id: String, kbps: Int = 192, startMs: Long = 0): String {
        val seek = if (startMs > 0) "&startTimeTicks=${startMs * 10_000}" else ""
        return "$baseUrl/Audio/$id/stream.mp3?static=false&audioCodec=mp3" +
            "&audioBitRate=${kbps * 1000}&maxAudioChannels=2$seek"
    }

    fun transcodeUrl(id: String, bitrate: Int = 256_000): String =
        "$baseUrl/Audio/$id/universal?audioCodec=aac&maxStreamingBitrate=$bitrate&transcodingContainer=ts&transcodingProtocol=hls"

    /**
     * Mark/clear a favourite. Jellyfin models this as a sub-resource on the user, not a field on
     * the item: POST creates it, DELETE removes it, and the item's own row never changes.
     */
    suspend fun setFavorite(userId: String, itemId: String, favourite: Boolean): Int {
        val url = "$baseUrl/Users/$userId/FavoriteItems/$itemId"
        val res = if (favourite) {
            client.post(url) { header("Authorization", authHeader) }
        } else {
            client.delete(url) { header("Authorization", authHeader) }
        }
        return res.status.value
    }

    fun imageUrl(itemId: String, tag: String?, height: Int = 400): String {
        val t = if (tag.isNullOrBlank()) "" else "&tag=$tag"
        return "$baseUrl/Items/$itemId/Images/Primary?maxHeight=$height&quality=88$t"
    }

    suspend fun reportsPlaying(userId: String, itemId: String, positionMs: Long) {
        // alpha: no-op shim; wired to /Sessions/Playing* in M2
    }
}
