package dev.lluissunol.iptv.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

@androidx.annotation.OptIn(UnstableApi::class)
fun PlaybackException.userMessage(): String {
    val http = findHttpCause(this)
    if (http != null) {
        return when (http.responseCode) {
            401, 403 -> "El proveedor rechazó el canal (HTTP ${http.responseCode}). Puede requerir otro User-Agent o que la suscripción esté activa."
            404 -> "El canal ya no existe en el proveedor (HTTP 404). Actualiza la lista."
            in 500..599 -> "El servidor del proveedor falló (HTTP ${http.responseCode}). Inténtalo más tarde."
            else -> "El proveedor respondió HTTP ${http.responseCode}."
        }
    }

    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "El canal tardó demasiado en responder. Comprueba tu conexión."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "No hay conexión con el proveedor del canal."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "El proveedor devolvió un formato que no se puede reproducir."
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "El móvil no puede decodificar este canal (códec no soportado). Prueba otra variante."
        else ->
            "No se pudo reproducir el canal (${errorCodeName})."
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun findHttpCause(error: Throwable?): HttpDataSource.InvalidResponseCodeException? {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 6) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current
        current = current.cause
        depth++
    }
    return null
}
