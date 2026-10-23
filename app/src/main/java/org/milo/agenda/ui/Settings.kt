package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.milo.agenda.data.ALL_WIDGETS
import org.milo.agenda.data.DeviceCalendar
import org.milo.agenda.data.HomeAssistant
import org.milo.agenda.data.HomeSwitch
import org.milo.agenda.data.LocalServer
import org.milo.agenda.data.Photos
import org.milo.agenda.data.Place
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = Store.prefs
    var showCity by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Ajustes", onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Ciudad / clima ----
            Section("Ubicación del clima") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { showCity = true }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.Place, null, tint = C.Primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prefs.city, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text("%.3f, %.3f".format(prefs.lat, prefs.lon), fontSize = 13.sp, color = C.InkVar)
                    }
                    TextButton(onClick = { showCity = true }) { Text("Cambiar") }
                }
                Text("El clima viene de Open-Meteo.com, sin clave ni cuenta.", fontSize = 13.sp, color = C.InkVar)
            }

            // ---- Calendario del sistema (Google, etc.) ----
            Section("Calendario del sistema") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mostrar eventos de Google Calendar", fontSize = 16.sp)
                        Text("Lee los calendarios que ya sincroniza la tablet (Google u otros). Solo lectura.",
                            fontSize = 13.sp, color = C.InkVar)
                    }
                    Switch(
                        checked = prefs.showDeviceCalendar,
                        onCheckedChange = { Store.updatePrefs(prefs.copy(showDeviceCalendar = it)) },
                    )
                }
                if (prefs.showDeviceCalendar && DeviceCalendar.hasPermission(ctx)) {
                    val cals = remember { DeviceCalendar.calendars(ctx) }
                    if (cals.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Calendarios a mostrar", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Primary)
                        cals.forEach { cal ->
                            val on = cal.name in prefs.calendars
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { Store.updatePrefs(prefs.copy(calendars = prefs.calendars.toggle(cal.name))) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cal.name, fontSize = 16.sp)
                                    if (cal.account.isNotBlank() && cal.account != cal.name)
                                        Text(cal.account, fontSize = 12.sp, color = C.InkVar)
                                }
                                Checkbox(checked = on, onCheckedChange = { Store.updatePrefs(prefs.copy(calendars = prefs.calendars.toggle(cal.name))) })
                            }
                        }
                        Text("Si no marcas ninguno, se muestran todos.", fontSize = 13.sp, color = C.InkVar)
                    }
                }
            }

            // ---- Alarma por defecto ----
            Section("Alarma por defecto en eventos nuevos") {
                val options = listOf(0 to "A la hora", 5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 h")
                FlowChips(options.map { it.first to it.second }, prefs.defaultAlarm) { v ->
                    Store.updatePrefs(prefs.copy(defaultAlarm = v))
                }
            }

            // ---- Widgets del inicio ----
            Section("Widgets del inicio") {
                ALL_WIDGETS.forEach { (key, label) ->
                    val on = key in prefs.widgets
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { Store.updatePrefs(prefs.copy(widgets = prefs.widgets.toggle(key))) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { Store.updatePrefs(prefs.copy(widgets = prefs.widgets.toggle(key))) })
                    }
                }
                Text("Reordénalos y ajústalos desde el inicio; aquí decides cuáles aparecen.", fontSize = 13.sp, color = C.InkVar)
            }

            // ---- Protector de pantalla ----
            Section("Protector de pantalla") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pasar fotos tras inactividad", fontSize = 16.sp)
                        Text("Muestra las fotos de ${Photos.FOLDER}. Toca la pantalla para volver.",
                            fontSize = 13.sp, color = C.InkVar)
                    }
                    Switch(
                        checked = prefs.screensaver,
                        onCheckedChange = { Store.updatePrefs(prefs.copy(screensaver = it)) },
                    )
                }
                if (prefs.screensaver) {
                    Spacer(Modifier.height(12.dp))
                    Stepper("Inactividad", "${prefs.screensaverIdleMin} min",
                        onMinus = { Store.updatePrefs(prefs.copy(screensaverIdleMin = (prefs.screensaverIdleMin - 5).coerceIn(5, 120))) },
                        onPlus = { Store.updatePrefs(prefs.copy(screensaverIdleMin = (prefs.screensaverIdleMin + 5).coerceIn(5, 120))) })
                    Spacer(Modifier.height(8.dp))
                    Stepper("Por foto", "${prefs.screensaverPhotoSec} s",
                        onMinus = { Store.updatePrefs(prefs.copy(screensaverPhotoSec = (prefs.screensaverPhotoSec - 10).coerceIn(10, 300))) },
                        onPlus = { Store.updatePrefs(prefs.copy(screensaverPhotoSec = (prefs.screensaverPhotoSec + 10).coerceIn(10, 300))) })
                    if (!Photos.hasPermission(ctx)) {
                        Spacer(Modifier.height(10.dp))
                        Text("Falta el permiso de fotos: la app lo pedirá, o actívalo en los ajustes del sistema.",
                            fontSize = 13.sp, color = C.Error)
                    }
                }
            }

            // ---- Añadir desde el teléfono ----
            Section("Añadir a Compras desde el teléfono") {
                val ip = remember { LocalServer.ipAddress() }
                if (ip != null) {
                    Text("En un teléfono de la MISMA red WiFi, abre esta dirección en el navegador:",
                        fontSize = 14.sp, color = C.InkVar)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.PrimaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Smartphone, null, tint = C.OnPrimaryContainer, modifier = Modifier.size(22.dp))
                        Text("http://$ip:${LocalServer.PORT}", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = C.OnPrimaryContainer)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Lo que escribas se añade a la lista de Compras del inicio. Todo por tu red, sin internet ni cuentas.",
                        fontSize = 13.sp, color = C.InkVar)
                    if (!LocalServer.running) Text("El servidor no está activo (reinicia la app).", fontSize = 13.sp, color = C.Error)
                } else {
                    Text("Conéctate a una red WiFi para activar esta función.", fontSize = 14.sp, color = C.InkVar)
                }
            }

            // ---- Casa (Home Assistant) ----
            Section("Casa (Home Assistant)") {
                Text("Controla interruptores de tu Home Assistant por la red local. El token se guarda solo en esta tablet.",
                    fontSize = 13.sp, color = C.InkVar)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = prefs.haUrl,
                    onValueChange = { Store.updatePrefs(prefs.copy(haUrl = it.filterNot(Char::isWhitespace))) },
                    label = { Text("Dirección") },
                    placeholder = { Text("http://192.168.1.50:8123") },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prefs.haToken,
                    onValueChange = { Store.updatePrefs(prefs.copy(haToken = it.filterNot(Char::isWhitespace))) },
                    label = { Text("Token de acceso de larga duración") },
                    // Multilínea a propósito: el token de HA suele copiarse con saltos de línea;
                    // así el pegado llega completo y luego se le quitan los espacios. singleLine lo truncaría.
                    singleLine = false, maxLines = 4, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                Text("En Home Assistant: tu perfil → Tokens de acceso de larga duración → Crear token.",
                    fontSize = 12.sp, color = C.InkVar, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(16.dp))
                Text("Interruptores", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Primary)
                Text("Toca el icono para cambiarlo. Escribe en la fila vacía para agregar otro.",
                    fontSize = 12.sp, color = C.InkVar)
                val sws = prefs.switches
                // Las guardadas + una fila vacía al final: al escribir en ella se agrega y aparece otra.
                (sws + HomeSwitch("", "", "power")).forEachIndexed { i, sw ->
                    val isNew = i >= sws.size
                    fun put(updated: HomeSwitch) =
                        Store.updatePrefs(prefs.copy(switches = if (isNew) sws + updated else sws.set(i, updated)))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Icono: tocar para ciclar entre los disponibles (solo en filas ya creadas).
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(C.SurfaceHigh)
                                .then(if (isNew) Modifier else Modifier.clickable {
                                    val next = homeIconKeys[(homeIconKeys.indexOf(sw.icon).coerceAtLeast(0) + 1) % homeIconKeys.size]
                                    put(sw.copy(icon = next))
                                }),
                            contentAlignment = Alignment.Center,
                        ) { HomeSwitchIcon(if (isNew) "power" else sw.icon, if (isNew) C.OutlineVar else C.Primary, 24) }
                        OutlinedTextField(
                            value = sw.name,
                            onValueChange = { put(sw.copy(name = it)) },
                            label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = sw.entityId,
                            onValueChange = { put(sw.copy(entityId = it.trim())) },
                            label = { Text("entity_id") }, placeholder = { Text("switch.algo") },
                            singleLine = true, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1.4f),
                        )
                        if (isNew) Spacer(Modifier.width(48.dp))
                        else IconButton(onClick = { Store.updatePrefs(prefs.copy(switches = sws.filterIndexed { idx, _ -> idx != i })) }) {
                            Icon(Icons.Rounded.Delete, "Borrar", tint = C.Error)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { scope.launch { HomeAssistant.refresh() } },
                        enabled = HomeAssistant.configured() && !HomeAssistant.loading,
                    ) { Text("Probar conexión") }
                    if (HomeAssistant.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else HomeAssistant.error?.let { Text(it, fontSize = 13.sp, color = C.Error) }
                        ?: run {
                            val ok = prefs.switches.count { it.entityId.isNotBlank() && HomeAssistant.states[it.entityId] != null }
                            if (ok > 0) Text("Conectado ($ok)", fontSize = 13.sp, color = C.Primary)
                        }
                }
            }

            // ---- Acerca de ----
            Section("Acerca de") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.Info, null, tint = C.Primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Agenda Milo 0.1.0", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Todo se guarda en esta tablet. Sin cuentas ni nube.", fontSize = 13.sp, color = C.InkVar)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCity) CityDialog(
        onDismiss = { showCity = false },
        onPick = { p ->
            Store.updatePrefs(prefs.copy(city = p.name, lat = p.lat, lon = p.lon))
            showCity = false
            scope.launch { WeatherRepo.refresh(ctx) }
        },
    )
}

private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key
private fun <T> List<T>.set(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RoundIcon(Icons.Rounded.Remove, "Menos", C.SurfaceHigh, C.Ink, 44) { onMinus() }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        RoundIcon(Icons.Rounded.Add, "Más", C.SurfaceHigh, C.Ink, 44) { onPlus() }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Tile(C.SurfaceC, Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), PaddingValues(20.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Primary)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FlowChips(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    // Dos filas para que quepa en tablet y teléfono sin desbordar.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (v, label) ->
                    FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun CityDialog(onDismiss: () -> Unit, onPick: (Place) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(300) // pequeño retardo para no buscar en cada tecla
        results = runCatching { WeatherRepo.search(ctx, query.trim()) }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        title = { Text("Elegir ciudad") },
        text = {
            Column(Modifier.widthIn(min = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar ciudad") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                )
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    query.trim().length < 2 -> Text("Escribe al menos dos letras.", fontSize = 13.sp, color = C.InkVar)
                    results.isEmpty() -> Text("Sin resultados.", fontSize = 13.sp, color = C.InkVar)
                    else -> LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(results) { p ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(p) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = C.Outline, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(p.name, fontSize = 16.sp)
                                    if (p.detail.isNotBlank()) Text(p.detail, fontSize = 13.sp, color = C.InkVar)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
