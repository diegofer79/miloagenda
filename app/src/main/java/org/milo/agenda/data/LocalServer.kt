package org.milo.agenda.data

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Servidor HTTP mínimo (sin dependencias) para añadir artículos a la lista "Compras" desde otro
 * dispositivo de la misma red WiFi. La tablet escucha en el puerto [PORT]; el teléfono abre
 * http://<ip-tablet>:<puerto> en el navegador, escribe y el ítem se agrega a la nota fijada.
 *
 * Sin nube ni cuentas: solo red local. Cualquiera en la misma WiFi puede añadir (uso doméstico).
 */
object LocalServer {
    const val PORT = 8080

    @Volatile
    var running = false
        private set

    private var serverSocket: ServerSocket? = null

    fun start(onAdd: (String) -> Unit) {
        if (running) return
        running = true
        Thread {
            try {
                ServerSocket(PORT).use { ss ->
                    serverSocket = ss
                    while (running) {
                        val client = try { ss.accept() } catch (e: Exception) { break }
                        try { handle(client, onAdd) } catch (_: Exception) {}
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

    private fun handle(client: Socket, onAdd: (String) -> Unit) {
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

            var added: String? = null
            if (method.equals("POST", true) && path.startsWith("/add")) {
                val buf = CharArray(contentLength)
                var n = 0
                while (n < contentLength) {
                    val r = input.read(buf, n, contentLength - n)
                    if (r < 0) break
                    n += r
                }
                val text = formValue(String(buf, 0, n), "text")?.trim()
                if (!text.isNullOrBlank()) {
                    added = text
                    onAdd(text) // estado de Compose: seguro desde otro hilo (snapshots)
                }
            }

            val bytes = page(added).toByteArray(Charsets.UTF_8)
            sock.getOutputStream().apply {
                write(
                    ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
                )
                write(bytes)
                flush()
            }
        }
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

    private fun page(added: String?): String {
        val items = Store.notes.firstOrNull { it.title == "Compras" }?.items ?: emptyList()
        val list = items.joinToString("") { "<li class=\"${if (it.done) "done" else ""}\">${esc(it.text)}</li>" }
        val confirm = if (added != null) "<div class=ok>✓ Añadido: ${esc(added)}</div>" else ""
        val copyBtn = if (items.isEmpty()) "" else
            "<button type=button class=copy id=copy onclick=copyList()>Copiar lista</button>"
        return """<!doctype html><html lang=es><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<title>Compras · Agenda Milo</title>
<style>
*{box-sizing:border-box}
body{margin:0 auto;max-width:560px;padding:20px;font-family:system-ui,-apple-system,sans-serif;background:#ECEAE3;color:#1B1C18}
h1{font-size:24px;margin:0 0 4px}
form{display:flex;gap:8px;margin:16px 0}
input[type=text]{flex:1;font-size:17px;padding:14px 16px;border:1px solid #C5C8BB;border-radius:14px;background:#FBF9F3;color:#1B1C18}
input[type=text]:focus{outline:2px solid #4C6A3D;border-color:#4C6A3D}
button{font-size:17px;font-weight:600;padding:0 22px;border:none;border-radius:14px;background:#4C6A3D;color:#fff}
.ok{background:#CDEDB7;color:#0B2000;padding:12px 16px;border-radius:12px;margin-bottom:4px}
ul{list-style:none;padding:0;margin:8px 0}
li{background:#FBF9F3;padding:13px 16px;border-radius:12px;margin-bottom:8px;font-size:16px;border:1px solid #E3E3DA}
li.done{color:#75796E;text-decoration:line-through}
.hint{color:#44483F;font-size:13px;margin-top:20px}
.copy{width:100%;padding:14px 16px;background:#FBF9F3;color:#1B1C18;border:1px solid #C5C8BB;margin-top:4px}
.copy:active{background:#E3E3DA}
</style></head><body>
<h1>🛒 Compras</h1>
$confirm
<form method=post action=/add autocomplete=off>
<input type=text name=text placeholder="Añadir artículo" autofocus autocomplete=off>
<button type=submit>Añadir</button>
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
}
