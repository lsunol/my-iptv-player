package dev.lluissunol.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.lluissunol.iptv.data.ImageHostHealth
import dev.lluissunol.iptv.data.PlaylistRepository
import dev.lluissunol.iptv.data.SettingsStore
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class OpenTvApplication : Application(), ImageLoaderFactory {
    /**
     * Tracks artwork hosts that refuse connections (see [ImageHostHealth]); the
     * playlist screen reads it to explain missing posters.
     */
    val imageHostHealth: ImageHostHealth by lazy { ImageHostHealth() }

    /**
     * Coil's singleton loader with its own OkHttp client: shorter connect
     * timeout than the playlist client (a poster is not worth 20 s) and the
     * unreachable-host short-circuit, so a grid of thousands of posters on a
     * firewalled image server does not open a socket per cell.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(imageHostHealth)
                .build()
        }
        .build()

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(Cache(File(cacheDir, "playlist-http"), 20L * 1024 * 1024))
            .build()
    }

    // cacheDir holds the download temp file used when a load has no cache
    // target; it is the app's own cache directory, cleared by the system when
    // storage runs low.
    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(httpClient, cacheDir)
    }
}
