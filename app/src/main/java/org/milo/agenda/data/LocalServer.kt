package org.milo.agenda.data

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Servidor HTTP mínimo (sin dependencias) para usar desde otro dispositivo de la misma red WiFi.
 * La tablet escucha en el puerto [PORT]; el teléfono abre http://<ip-tablet>:<puerto> en el navegador.
 *
 * Dos pestañas:
 *  - "Compras": escribe y el ítem se agrega a la nota fijada (igual que antes).
 *  - "Luces": muestra los interruptores de Home Assistant y los enciende/apaga al tocarlos.
 *
 * Sin nube ni cuentas: solo red local. Cualquiera en la misma WiFi puede usarlo (uso doméstico).
 */
object LocalServer {
    const val PORT = 8080

    @Volatile
    var running = false
        private set

    private var serverSocket: ServerSocket? = null
    private var onAdd: (String) -> Unit = {}
    private var onToggle: (String) -> Unit = {}
    private var iconBytes: ByteArray? = null

    @Volatile
    private var refreshing = false

    /** Relee el estado de los interruptores sin bloquear la respuesta; evita refrescos solapados. */
    private fun backgroundRefresh() {
        if (refreshing) return
        refreshing = true
        Thread {
            try { HomeAssistant.refreshSync() } finally { refreshing = false }
        }.apply { isDaemon = true; name = "milo-ha-refresh"; start() }
    }

    fun start(
        onAdd: (String) -> Unit,
        onToggle: (String) -> Unit = {},
        iconBytes: ByteArray? = null,
    ) {
        if (running) return
        running = true
        this.onAdd = onAdd
        this.onToggle = onToggle
        this.iconBytes = iconBytes
        Thread {
            try {
                ServerSocket(PORT).use { ss ->
                    serverSocket = ss
                    while (running) {
                        val client = try { ss.accept() } catch (e: Exception) { break }
                        // Un hilo por conexión: así una consulta lenta a Home Assistant (o HA caído)
                        // nunca bloquea otras peticiones —p. ej. cambiar de pestaña o el favicon—.
                        Thread { try { handle(client) } catch (_: Exception) {} }
                            .apply { isDaemon = true; start() }
                    }
                }
            } catch (e: Exception) {
                running = false
            }
        }.apply { isDaemon = true; name = "milo-localserver"; start() }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    /** IPv4 local (de la WiFi) para mostrarle al usuario qué dirección abrir. */
    fun ipAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    }.getOrNull()

    private fun handle(client: Socket) {
        client.use { sock ->
            val input = sock.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "" }
            val path = parts.getOrElse(1) { "/" }

            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (method.equals("POST", true) && contentLength > 0) {
                val buf = CharArray(contentLength)
                var n = 0
                while (n < contentLength) {
                    val r = input.read(buf, n, contentLength - n)
                    if (r < 0) break
                    n += r
                }
                String(buf, 0, n)
            } else ""

            val out = sock.getOutputStream()

            // Icono (favicon / "añadir a pantalla de inicio").
            if (method.equals("GET", true) && path.startsWith("/icon.png")) {
                val img = iconBytes
                if (img != null) writeResponse(out, "image/png", img)
                else writeResponse(out, "text/plain; charset=utf-8", "no icon".toByteArray(), code = "404 Not Found")
                return
            }

            val html = when {
                method.equals("POST", true) && path.startsWith("/add") -> {
                    val text = formValue(body, "text")?.trim()
                    val added = if (!text.isNullOrBlank()) { onAdd(text); text } else null
                    comprasPage(added)
                }
                method.equals("POST", true) && path.startsWith("/toggle") -> {
                    val entity = formValue(body, "entity")?.trim()
                    if (!entity.isNullOrBlank()) onToggle(entity) // bloqueante: press() actualiza el estado
                    lucesPage()
                }
                path.startsWith("/luces") -> {
                    backgroundRefresh() // refresca en segundo plano; la página se dibuja ya con lo último conocido
                    lucesPage()
                }
                else -> comprasPage(null)
            }
            writeResponse(out, "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
        }
    }

    private fun writeResponse(out: java.io.OutputStream, contentType: String, bytes: ByteArray, code: String = "200 OK") {
        out.write(
            ("HTTP/1.1 $code\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
        )
        out.write(bytes)
        out.flush()
    }

    private fun formValue(body: String, key: String): String? {
        for (pair in body.split("&")) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            if (pair.substring(0, i) == key) {
                return runCatching {
                    URLDecoder.decode(pair.substring(i + 1).replace("+", " "), "UTF-8")
                }.getOrNull()
            }
        }
        return null
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Cabecera común: estilos + las dos pestañas (Compras / Luces). */
    private fun head(title: String): String = """<!doctype html><html lang=es><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>$title · Agenda Milo</title>
<link rel=icon href=/icon.png>
<link rel=apple-touch-icon href=/icon.png>
<style>
*{box-sizing:border-box}
body{margin:0 auto;max-width:560px;padding:20px;font-family:system-ui,-apple-system,sans-serif;background:#ECEAE3;color:#1B1C18}
h1{font-size:24px;margin:0 0 4px}
.tabs{display:flex;gap:8px;margin:0 0 4px}
.tabs a{flex:1;text-align:center;text-decoration:none;font-size:17px;font-weight:600;padding:12px 0;border-radius:14px;background:#FBF9F3;color:#1B1C18;border:1px solid #C5C8BB}
.tabs a.on{background:#4C6A3D;color:#fff;border-color:#4C6A3D}
form.add{display:flex;gap:8px;margin:16px 0}
input[type=text]{flex:1;font-size:17px;padding:14px 16px;border:1px solid #C5C8BB;border-radius:14px;background:#FBF9F3;color:#1B1C18}
input[type=text]:focus{outline:2px solid #4C6A3D;border-color:#4C6A3D}
button.go{font-size:17px;font-weight:600;padding:0 22px;border:none;border-radius:14px;background:#4C6A3D;color:#fff}
.ok{background:#CDEDB7;color:#0B2000;padding:12px 16px;border-radius:12px;margin-bottom:4px}
ul{list-style:none;padding:0;margin:8px 0}
li{background:#FBF9F3;padding:13px 16px;border-radius:12px;margin-bottom:8px;font-size:16px;border:1px solid #E3E3DA}
li.done{color:#75796E;text-decoration:line-through}
.hint{color:#44483F;font-size:13px;margin-top:20px}
.note{color:#44483F;font-size:15px;text-align:center;padding:24px}
.copy{width:100%;padding:14px 16px;background:#FBF9F3;color:#1B1C18;border:1px solid #C5C8BB;border-radius:14px;margin-top:4px;font-size:17px;font-weight:600}
.copy:active{background:#E3E3DA}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin:12px 0}
.grid form{margin:0}
.sw{width:100%;border:1px solid #C5C8BB;text-align:center;border-radius:14px;padding:12px 10px;background:#FBF9F3;color:#1B1C18;cursor:pointer;display:flex;flex-direction:column;align-items:center;gap:2px}
.sw.on{background:#4C6A3D;color:#fff;border-color:#4C6A3D}
.sw.off-air{opacity:.55}
.sw .name{font-size:17px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%}
.sw .st{font-size:12px;opacity:.8}
</style></head><body>
<div class=tabs>
<a href="/" class="${if (title == "Compras") "on" else ""}">🛒 Compras</a>
<a href="/luces" class="${if (title == "Luces") "on" else ""}">💡 Luces</a>
</div>"""

    private fun comprasPage(added: String?): String {
        val items = Store.notes.firstOrNull { it.title == "Compras" }?.items ?: emptyList()
        val list = items.joinToString("") { "<li class=\"${if (it.done) "done" else ""}\">${esc(it.text)}</li>" }
        val confirm = if (added != null) "<div class=ok>✓ Añadido: ${esc(added)}</div>" else ""
        val copyBtn = if (items.isEmpty()) "" else
            "<button type=button class=copy id=copy onclick=copyList()>Copiar lista</button>"
        return head("Compras") + """
$confirm
<form class=add method=post action=/add autocomplete=off>
<input type=text name=text placeholder="Añadir artículo" autofocus autocomplete=off>
<button type=submit class=go>Añadir</button>
</form>
<ul>$list</ul>
$copyBtn
<p class=hint>Agenda Milo · se guarda en la tablet, por tu red WiFi</p>
<script>
function copyList(){
  var text=Array.prototype.map.call(document.querySelectorAll('ul li'),function(li){return li.textContent;}).join('\n');
  var done=function(){var b=document.getElementById('copy');b.textContent='✓ Copiada';setTimeout(function(){b.textContent='Copiar lista';},1500);};
  if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text).then(done,function(){fallback(text,done);});}
  else{fallback(text,done);}
}
function fallback(text,done){
  var t=document.createElement('textarea');t.value=text;t.style.position='fixed';t.style.opacity='0';
  document.body.appendChild(t);t.focus();t.select();
  try{document.execCommand('copy');done();}catch(e){}
  document.body.removeChild(t);
}
</script>
</body></html>"""
    }

    private fun emoji(icon: String): String = when (icon) {
        "palm" -> "🌴"
        "pool" -> "🏊"
        "bulb" -> "💡"
        "water" -> "💧"
        "bolt" -> "⚡"
        "ac" -> "❄️"
        "garden" -> "🌿"
        "fan" -> "🌀"
        else -> "🔌" // "power" y desconocidos
    }

    private fun stateText(s: SwState?): String = when (s) {
        SwState.ON -> "Encendido"
        SwState.OFF -> "Apagado"
        SwState.UNAVAILABLE -> "No disponible"
        null -> "–"
    }

    private fun lucesPage(): String {
        val body = when {
            !HomeAssistant.configured() ->
                "<p class=note>Configura Home Assistant en Ajustes de la tablet (dirección y token) para controlar tus interruptores.</p>"
            else -> {
                val switches = Store.prefs.switches.filter { it.entityId.isNotBlank() }
                if (switches.isEmpty())
                    "<p class=note>Añade el entity_id de tus interruptores en Ajustes de la tablet para que aparezcan aquí.</p>"
                else {
                    val cards = switches.joinToString("") { s ->
                        val st = HomeAssistant.states[s.entityId]
                        val cls = when (st) {
                            SwState.ON -> "sw on"
                            SwState.UNAVAILABLE -> "sw off-air"
                            else -> "sw"
                        }
                        """<form method=post action=/toggle style=margin:0>
<input type=hidden name=entity value="${esc(s.entityId)}">
<button type=submit class="$cls">
<span class=name>${emoji(s.icon)} ${esc(s.name)}</span>
<span class=st>${stateText(st)}</span>
</button></form>"""
                    }
                    "<div class=grid>$cards</div>"
                }
            }
        }
        return head("Luces") + """
$body
<p class=hint>Agenda Milo · interruptores de Home Assistant, por tu red WiFi</p>
</body></html>"""
    }
}
