package app.opentv

import android.app.Application
import app.opentv.data.PlaylistRepository
import app.opentv.data.SettingsStore
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

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(httpClient) }
}
