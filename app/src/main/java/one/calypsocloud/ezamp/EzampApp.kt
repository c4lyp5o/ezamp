package one.calypsocloud.ezamp

import android.app.Application
import android.os.SystemClock
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import one.calypsocloud.ezamp.data.LibraryStore
import one.calypsocloud.ezamp.data.Prefs
import one.calypsocloud.ezamp.player.PlayerHolder

class EzampApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        LibraryStore.attach(this)
        one.calypsocloud.ezamp.data.Downloads.init(this)

        // Images need the same auth as the API; a header interceptor keeps the token out of URLs.
        SingletonImageLoader.setSafe { ctx ->
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request()
                    val base = Prefs.baseUrl
                    val token = Prefs.token
                    if (base != null && token != null && req.url.toString().startsWith(base)) {
                        chain.proceed(
                            req.newBuilder()
                                .header("Authorization", "MediaBrowser Token=\"$token\"")
                                .build(),
                        )
                    } else {
                        chain.proceed(req)
                    }
                }
                .build()
            ImageLoader.Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .build()
        }

        PlayerHolder.connect(this)

        Prefs.userId?.let { uid ->
            LibraryStore.start(uid)
        }

        LibraryStore.markColdStart(SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis())
    }
}
