package org.milo.agenda.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Estado de un interruptor: encendido, apagado o no disponible (dispositivo offline en HA). */
enum class SwState { ON, OFF, UNAVAILABLE }

/**
 * Controla interruptores vía la API REST local de Home Assistant.
 * HA local va por HTTP en la LAN (p. ej. http://192.168.1.50:8123), así que se usa
 * [HttpURLConnection] y no la variante HTTPS del clima. El token sale de [Prefs.haToken]
 * (guardado solo en la tablet). El dominio del servicio se deduce del entity_id
 * (switch.* -> switch/toggle, light.* -> light/toggle, etc.); "toggle" existe en todos.
 */
object HomeAssistant {
    /** Estado por entity_id. Si la clave no está, aún no se consultó. */
    val states = mutableStateMapOf<String, SwState>()
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun configured(): Boolean {
        val p = Store.prefs
        return p.haUrl.isNotBlank() && p.haToken.isNotBlank()
    }

    // Quita cualquier espacio/salto de línea (los tokens pegados desde HA suelen venir cortados).
    private fun clean(s: String): String = s.filterNot { it.isWhitespace() }
    private fun base(): String = clean(Store.prefs.haUrl).trimEnd('/')

    /** Lee el estado de todos los interruptores configurados (con entity_id). */
    suspend fun refresh() {
        if (!configured() || loading) return
        loading = true; error = null
        try {
            val entities = Store.prefs.switches.map { it.entityId }.filter { it.isNotBlank() }.distinct()
            if (entities.isEmpty()) { error = "Añade el entity_id de tus interruptores."; return }
            var netFail = 0
            var auth = false
            val missing = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                entities.forEach { id ->
                    try {
                        val r = request("/api/states/$id", "GET")
                        when (r.code) {
                            200 -> states[id] = parseState(r.body)
                            401, 403 -> auth = true
                            404 -> { missing += id; states[id] = SwState.UNAVAILABLE }
                            else -> netFail++
                        }
                    } catch (e: Exception) {
                        netFail++
                    }
                }
            }
            error = when {
                auth -> "Token rechazado. Revisa el token de Home Assistant."
                netFail == entities.size -> "No se llega a ${base()}. Revisa IP/puerto y que la tablet esté en la misma WiFi."
                missing.isNotEmpty() -> "No existe: ${missing.joinToString(", ")}. Revisa el entity_id."
                else -> null
            }
        } finally {
            loading = false
        }
    }

    /** Cambia el interruptor y refresca su estado. */
    suspend fun toggle(entityId: String) {
        if (!configured() || entityId.isBlank()) return
        error = null
        try {
            val domain = entityId.substringBefore('.', "switch").ifBlank { "switch" }
            withContext(Dispatchers.IO) {
                val r = request("/api/services/$domain/toggle", "POST", JSONObject().put("entity_id", entityId).toString())
                when (r.code) {
                    in 200..299 -> runCatching {
                        val s = request("/api/states/$entityId", "GET")
                        if (s.code == 200) states[entityId] = parseState(s.body)
                    }
                    401, 403 -> error = "Token rechazado. Revisa el token de Home Assistant."
                    404 -> error = "No existe $entityId. Revisa el entity_id."
                    else -> error = "Home Assistant respondió ${r.code}."
                }
            }
        } catch (e: Exception) {
            error = "No se llega a ${base()}. Revisa IP/puerto y la WiFi."
            android.util.Log.w("Milo-HA", "toggle $entityId falló: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    // ---------- HTTP ----------
    private data class Resp(val code: Int, val body: String?)

    private fun parseState(json: String?): SwState =
        when (runCatching { JSONObject(json ?: "").optString("state") }.getOrNull()) {
            "on" -> SwState.ON
            "off" -> SwState.OFF
            else -> SwState.UNAVAILABLE // "unavailable", "unknown", vacío…
        }

    /**
     * Petición con reintentos: la WiFi de la tablet puede perder paquetes y hacer que el primer
     * intento dé timeout aunque el servidor esté bien. Reintenta hasta 3 veces antes de rendirse.
     */
    private fun request(path: String, method: String, body: String? = null): Resp {
        var last: Exception? = null
        // Timeout de conexión corto y varios reintentos: si el WiFi de la tablet parpadea,
        // así cada intento "cae rápido" y se vuelve a probar, aumentando la chance de pegar una ventana buena.
        repeat(6) { attempt ->
            val conn = URL(base() + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 4_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("Authorization", "Bearer ${clean(Store.prefs.haToken)}")
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val text = runCatching {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                return Resp(code, text)
            } catch (e: Exception) {
                last = e
                android.util.Log.w("Milo-HA", "$method $path intento ${attempt + 1}/6: ${e.javaClass.simpleName} ${e.message}")
            } finally {
                conn.disconnect()
            }
            Thread.sleep(300) // pequeño respiro antes de reintentar
        }
        throw last ?: java.io.IOException("sin conexión")
    }
}
