package dev.lluissunol.iptv

import android.app.Application
import dev.lluissunol.iptv.data.PlaylistRepository
import dev.lluissunol.iptv.data.SettingsStore
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class OpenTvApplication : Application() {
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
