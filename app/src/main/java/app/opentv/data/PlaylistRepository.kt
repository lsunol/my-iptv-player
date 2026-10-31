package app.opentv.data

import app.opentv.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class PlaylistException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidUrl : PlaylistException("URL no válida")
    class Http(val code: Int) : PlaylistException("HTTP $code")
    class Html : PlaylistException("HTML en lugar de una lista")
    class Empty : PlaylistException("Lista vacía")
    class TooLarge(val bytes: Long) : PlaylistException("Lista demasiado grande")
    class Network(cause: Throwable) : PlaylistException("Error de red", cause)
    class Unknown(cause: Throwable) : PlaylistException("Error desconocido", cause)
}

fun PlaylistException.userMessage(): String = when (this) {
    is PlaylistException.InvalidUrl ->
        "La URL no es válida. Debe empezar por http:// o https://."
    is PlaylistException.Http -> when (code) {
        401, 403 -> "El proveedor rechazó la lista (HTTP $code). Comprueba usuario/contraseña o que la suscripción esté activa."
        404 -> "La URL no existe (HTTP 404). Revisa que sea la dirección completa de la lista."
        in 500..599 -> "El servidor del proveedor falló (HTTP $code). Inténtalo más tarde."
        else -> "El proveedor respondió HTTP $code."
    }
    is PlaylistException.Html ->
        "La URL devolvió una página web, no una lista M3U. Comprueba el enlace y que la suscripción esté activa."
    is PlaylistException.Empty ->
        "La lista se descargó pero no contiene canales."
    is PlaylistException.TooLarge ->
        "La lista es demasiado grande (${bytes / 1_000_000} MB)."
    is PlaylistException.Network -> when (val cause = cause) {
        is SocketTimeoutException -> "La lista tardó demasiado en responder. Comprueba tu conexión o la URL."
        is UnknownHostException -> "No se pudo encontrar el servidor del proveedor. Comprueba la URL y tu conexión."
        else -> "No se pudo conectar con el proveedor. Comprueba tu conexión."
    }
    is PlaylistException.Unknown -> "No se pudo cargar la lista: ${cause?.message ?: message}"
}

data class LoadedPlaylist(val playlist: Playlist, val rawBytes: ByteArray)

class PlaylistRepository(private val client: OkHttpClient) {

    fun parseCached(bytes: ByteArray, baseUrl: String): Playlist {
        val text = decode(bytes, null).removePrefix("﻿")
        return PlaylistParser.parse(text, baseUrl, bytes.size.toLong())
    }

    suspend fun load(
        url: String,
        onProgress: (bytesRead: Long, totalBytes: Long, parsedEntries: Int) -> Unit
    ): LoadedPlaylist = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            throw PlaylistException.InvalidUrl()
        }

        val request = Request.Builder()
            .url(trimmed)
            .header("User-Agent", USER_AGENT)
            .header("Accept", ACCEPT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlaylistException.Http(response.code)

                val contentType = response.header("Content-Type")
                if (contentType?.contains("text/html", ignoreCase = true) == true) {
                    throw PlaylistException.Html()
                }

                val body = response.body
                    ?: throw PlaylistException.Unknown(IOException("Respuesta sin cuerpo"))
                val total = body.contentLength()
                val output = ByteArrayOutputStream(
                    if (total in 1..MAX_BYTES) total.toInt() else 1 shl 20
                )
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var read = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        if (read > MAX_BYTES) throw PlaylistException.TooLarge(read)
                        output.write(buffer, 0, count)
                        onProgress(read, total, 0)
                    }
                }

                val bytes = output.toByteArray()
                val text = decode(bytes, contentType).removePrefix("\uFEFF")
                val head = text.trimStart().take(64).lowercase()
                if (head.startsWith("<!doctype") || head.startsWith("<html")) {
                    throw PlaylistException.Html()
                }

                val finalUrl = response.request.url.toString()
                val playlist = PlaylistParser.parse(
                    text = text,
                    baseUrl = finalUrl,
                    bytes = bytes.size.toLong()
                ) { entries -> onProgress(bytes.size.toLong(), bytes.size.toLong(), entries) }

                if (playlist.channels.isEmpty()) throw PlaylistException.Empty()
                LoadedPlaylist(playlist, bytes)
            }
        } catch (e: PlaylistException) {
            throw e
        } catch (e: IOException) {
            throw PlaylistException.Network(e)
        } catch (e: Exception) {
            throw PlaylistException.Unknown(e)
        }
    }

    private fun decode(bytes: ByteArray, contentType: String?): String {
        val charset = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
        if (!charset.isNullOrBlank()) {
            return try {
                String(bytes, charset(charset))
            } catch (_: Exception) {
                String(bytes, Charsets.UTF_8)
            }
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, Charsets.ISO_8859_1) else utf8
    }

    private fun charset(name: String): java.nio.charset.Charset =
        java.nio.charset.Charset.forName(name)

    companion object {
        const val USER_AGENT = "OpenIPTV/0.1 (Android)"
        const val ACCEPT = "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*"
        const val MAX_BYTES = 32L * 1024 * 1024
    }
}
