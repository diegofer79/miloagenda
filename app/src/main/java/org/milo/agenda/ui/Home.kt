package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.milo.agenda.data.Alarms
import org.milo.agenda.data.CheckItem
import org.milo.agenda.data.Event
import org.milo.agenda.data.Note
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Temporizador con pausa real: en reposo cuenta [minutes]; corriendo usa [endAt] (epoch ms);
 * en pausa guarda los ms restantes en [pausedMs]. El diseño 1a permite tocar el tiempo para +5 min.
 */
object TimerState {
    var minutes by mutableIntStateOf(5)
    var endAt by mutableLongStateOf(0L)
    var pausedMs by mutableLongStateOf(0L)
}

@Composable
private fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000) }
    }
    return now
}

@Composable
fun HomeScreen(
    nav: (Screen) -> Unit,
    onNewEvent: () -> Unit,
    onEditEvent: (Event) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenAgendaOn: (LocalDate) -> Unit,
) {
    val now = rememberNow()
    val w = Store.prefs.widgets
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
        if (wide) {
            // Diseño 1a: tablero en 3 columnas. El riel lo pone App() para que quede fijo en todas las pantallas.
            Row(
                Modifier.fillMaxSize().padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Columna izquierda: reloj, clima, temporizador y agenda de mañana.
                Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if ("clock" in w) ClockCard(now)
                    if ("weather" in w) WeatherCard(Modifier.weight(1f)) { nav(Screen.Weather) }
                    if ("timer" in w) TimerCard(now, Modifier.weight(1f))
                    TomorrowCard(Modifier.weight(2f), onOpenAgendaOn)
                }
                // Columna central: agenda de hoy.
                if ("today" in w) TodayColumn(Modifier.weight(1f).fillMaxHeight(), onEditEvent) { onOpenAgendaOn(LocalDate.now()) }
                else Spacer(Modifier.weight(1f))
                // Columna derecha: lista de compras.
                if ("note" in w) ComprasColumn(Modifier.width(340.dp).fillMaxHeight(), scrollable = true, onOpenNote)
            }
        } else {
            // Teléfono / pantallas estrechas: columna con scroll + barra inferior.
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if ("clock" in w) ClockCard(now)
                    if ("weather" in w) WeatherCard(Modifier.height(150.dp)) { nav(Screen.Weather) }
                    if ("timer" in w) TimerCard(now, Modifier.height(140.dp))
                    TomorrowCard(Modifier.heightIn(min = 160.dp), onOpenAgendaOn)
                    if ("today" in w) TodayColumn(Modifier.height(320.dp), onEditEvent) { onOpenAgendaOn(LocalDate.now()) }
                    if ("note" in w) ComprasColumn(Modifier.heightIn(min = 200.dp), scrollable = false, onOpenNote)
                    if (w.isEmpty()) Text("Activa widgets en Ajustes", color = C.InkVar)
                }
                Spacer(Modifier.height(12.dp))
                Toolbar(nav, onNewEvent)
            }
        }
    }
}

// ---------- Riel de navegación (diseño 1a) ----------

@Composable
fun NavRail(current: Screen, nav: (Screen) -> Unit, onNew: () -> Unit) {
    Column(
        Modifier.width(88.dp).fillMaxHeight().padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(44.dp)).background(C.Ink),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(C.PrimaryContainer).clickable { onNew() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "Nuevo", tint = C.OnPrimaryContainer) }
        Spacer(Modifier.height(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RailItem(Icons.Rounded.Dashboard, "Inicio", current == Screen.Home) { nav(Screen.Home) }
            RailItem(Icons.Rounded.DateRange, "Agenda", current == Screen.Agenda) { nav(Screen.Agenda) }
            RailItem(Icons.Rounded.FilterDrama, "Clima", current == Screen.Weather) { nav(Screen.Weather) }
            RailItem(Icons.Rounded.Description, "Notas", current == Screen.Notes) { nav(Screen.Notes) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (current == Screen.Settings) C.PrimaryContainer else Color.Transparent)
                .clickable { nav(Screen.Settings) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Settings, "Ajustes", tint = if (current == Screen.Settings) C.OnPrimaryContainer else C.Surface) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clickable { onClick() }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                .background(if (selected) C.PrimaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (selected) C.OnPrimaryContainer else C.Surface, modifier = Modifier.size(24.dp)) }
        Text(label, fontSize = 12.sp, color = if (selected) C.Surface else C.OutlineVar,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// Barra inferior para pantallas estrechas (se conserva del diseño anterior).
@Composable
private fun Toolbar(nav: (Screen) -> Unit, onNew: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(CircleShape).background(C.Ink).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(52.dp).clip(CircleShape).background(C.PrimaryContainer).clickable { onNew() }.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Add, null, tint = C.OnPrimaryContainer)
                Text("Nuevo", color = C.OnPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            RoundIcon(Icons.Rounded.DateRange, "Agenda", Color.Transparent, C.Surface) { nav(Screen.Agenda) }
            RoundIcon(Icons.Rounded.FilterDrama, "Clima", Color.Transparent, C.Surface) { nav(Screen.Weather) }
            RoundIcon(Icons.Rounded.Description, "Notas", Color.Transparent, C.Surface) { nav(Screen.Notes) }
            RoundIcon(Icons.Rounded.Settings, "Ajustes", Color.Transparent, C.Surface) { nav(Screen.Settings) }
        }
    }
}

// ---------- Columna izquierda ----------

@Composable
private fun ClockCard(now: LocalDateTime) {
    val nextAlarm = Store.events.filter { it.alarmMinutes >= 0 }
        .mapNotNull { e -> e.alarmAtMillis()?.let { it to e } }
        .filter { it.first > System.currentTimeMillis() }
        .minByOrNull { it.first }
    Tile(C.SurfaceC, Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), PaddingValues(horizontal = 28.dp, vertical = 24.dp)) {
        Text(
            buildAnnotatedString {
                append("%02d".format(now.hour))
                withStyle(SpanStyle(color = C.Primary)) { append(":") }
                append("%02d".format(now.minute))
            },
            fontSize = 96.sp, fontWeight = FontWeight.Light, color = C.Ink, lineHeight = 96.sp, maxLines = 1, letterSpacing = (-3).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(fmtLongDay(now.toLocalDate()), fontSize = 18.sp, color = C.InkVar)
        if (nextAlarm != null) {
            Spacer(Modifier.height(18.dp))
            val (millis, e) = nextAlarm
            val at = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(C.Secondary).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Alarm, null, tint = C.OnSecondary, modifier = Modifier.size(18.dp))
                Text("${at.format(HM)} · ${e.title}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.OnSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeatherCard(modifier: Modifier, onClick: () -> Unit) {
    val wx = WeatherRepo.data
    Tile(C.PrimaryContainer, modifier.fillMaxWidth(), RoundedCornerShape(28.dp), PaddingValues(horizontal = 22.dp, vertical = 14.dp), onClick = onClick) {
        if (wx == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (WeatherRepo.loading) CircularProgressIndicator(color = C.OnPrimaryContainer)
                else Text("Sin datos de clima", color = C.OnPrimaryContainer)
            }
            return@Tile
        }
        val today = WeatherRepo.day(LocalDate.now())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(weatherIcon(wx.code, wx.isDay), null, tint = C.OnPrimaryContainer, modifier = Modifier.size(42.dp))
            Text(deg(wx.temp), fontSize = 40.sp, color = C.OnPrimaryContainer, lineHeight = 40.sp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(Store.prefs.city, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.OnPrimaryContainer)
                Text(weatherText(wx.code), fontSize = 12.sp, color = C.OnPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (today != null) Text("${deg(today.max)} / ${deg(today.min)}", fontSize = 12.sp, color = C.OnPrimaryContainer)
            }
        }
        Spacer(Modifier.weight(1f))
        val now = LocalDateTime.now().withMinute(0)
        val hours = wx.hours.filter { !it.time.isBefore(now) }.take(6)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("%02d".format(h.time.hour), fontSize = 12.sp, color = C.OnPrimaryContainer)
                    Icon(weatherIcon(h.code, h.time.hour in 7..20), null, tint = C.OnPrimaryContainer, modifier = Modifier.size(18.dp))
                    Text(deg(h.temp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.OnPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun TimerCard(now: LocalDateTime, modifier: Modifier) {
    val ctx = LocalContext.current
    now.second // recompone cada segundo
    val running = TimerState.endAt > System.currentTimeMillis()
    val paused = !running && TimerState.pausedMs > 0L
    val remainingMs = when {
        running -> TimerState.endAt - System.currentTimeMillis()
        paused -> TimerState.pausedMs
        else -> TimerState.minutes * 60_000L
    }
    val remaining = (remainingMs / 1000).coerceAtLeast(0)

    fun schedule() = Alarms.at(ctx, Alarms.TIMER_ID, TimerState.endAt, "Temporizador", "Tiempo cumplido")

    Tile(C.SurfaceHigh, modifier.fillMaxWidth(), RoundedCornerShape(28.dp), PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Timer, null, tint = C.Primary, modifier = Modifier.size(20.dp))
            Text("Temporizador", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(if (running) "En marcha" else if (paused) "En pausa" else "Listo", fontSize = 13.sp, color = C.InkVar)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Tocar el tiempo = +5 min.
            Text(
                "%02d:%02d".format(remaining / 60, remaining % 60),
                fontSize = 44.sp, fontWeight = FontWeight.Light, color = C.Ink, letterSpacing = (-1).sp,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                    when {
                        running -> { TimerState.endAt += 5 * 60_000L; schedule() }
                        paused -> TimerState.pausedMs += 5 * 60_000L
                        else -> TimerState.minutes = (TimerState.minutes + 5).coerceAtMost(180)
                    }
                }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            RoundIcon(Icons.Rounded.Refresh, "Reiniciar", C.Surface, C.InkVar, 44) {
                TimerState.endAt = 0L; TimerState.pausedMs = 0L; TimerState.minutes = 5
                Alarms.cancel(ctx, Alarms.TIMER_ID)
            }
            RoundIcon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (running) "Pausar" else "Iniciar", C.Primary, Color.White, 48) {
                val t = System.currentTimeMillis()
                when {
                    running -> { TimerState.pausedMs = TimerState.endAt - t; TimerState.endAt = 0L; Alarms.cancel(ctx, Alarms.TIMER_ID) }
                    paused -> { TimerState.endAt = t + TimerState.pausedMs; TimerState.pausedMs = 0L; schedule() }
                    else -> { TimerState.endAt = t + TimerState.minutes * 60_000L; schedule() }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Toca el tiempo para sumar 5 min", fontSize = 13.sp, color = C.InkVar)
    }
}

@Composable
private fun TomorrowCard(modifier: Modifier, onOpenAgenda: (LocalDate) -> Unit) {
    val tomorrow = LocalDate.now().plusDays(1)
    val list = Store.eventsOn(tomorrow)
    val shown = list.take(4)
    Tile(C.SurfaceC, modifier, RoundedCornerShape(28.dp), PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        onClick = { onOpenAgenda(tomorrow) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mañana", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${fmtShortDay(tomorrow)} ${tomorrow.dayOfMonth}", fontSize = 14.sp, color = C.InkVar)
        }
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin eventos", color = C.InkVar, fontSize = 15.sp) }
        } else {
            shown.forEach { e ->
                val (_, _, ink) = eventColors[e.color.coerceIn(0, eventColors.lastIndex)]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(e.start.format(HM), fontSize = 14.sp, color = C.InkVar, modifier = Modifier.width(48.dp))
                    Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(ink))
                    Text(e.title, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.fromDevice) Icon(Icons.Rounded.Event, null, tint = C.Outline, modifier = Modifier.size(16.dp))
                }
            }
            if (list.size > shown.size) {
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenAgenda(tomorrow) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Ver más (${list.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.Primary)
                    Icon(Icons.Rounded.ChevronRight, null, tint = C.Primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------- Columna central: agenda de hoy ----------

@Composable
private fun TodayColumn(modifier: Modifier, onEdit: (Event) -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val list = Store.eventsOn(LocalDate.now())
    val now = LocalTime.now()
    Tile(C.SurfaceC, modifier, RoundedCornerShape(28.dp), PaddingValues(horizontal = 16.dp, vertical = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hoy", fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("Abrir agenda", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.Primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen() }.padding(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin eventos hoy", color = C.InkVar) }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            var nowShown = false
            list.forEach { e ->
                if (!nowShown && e.start > now) { nowShown = true; item("now") { NowRow() } }
                item(e.id) { HomeEventRow(e, ctx, onEdit) }
            }
            if (!nowShown) item("now") { NowRow() }
        }
    }
}

@Composable
private fun HomeEventRow(e: Event, ctx: android.content.Context, onEdit: (Event) -> Unit) {
    val (_, _, ink) = eventColors[e.color.coerceIn(0, eventColors.lastIndex)]
    val past = e.date == LocalDate.now() && e.end < LocalTime.now()
    val hour = WeatherRepo.hourAt(e.startAt)
    val on = e.alarmMinutes >= 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onEdit(e) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(e.start.format(HM), fontSize = 14.sp, color = if (past) C.Outline else C.InkVar, modifier = Modifier.width(52.dp))
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(if (past) C.OutlineVar else ink))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (past) C.Outline else C.Ink,
                textDecoration = if (past) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (e.place.isNotBlank()) add(e.place)
                if (hour != null) add(deg(hour.temp) + if (hour.pop >= 50) " · lluvia ${hour.pop}%" else "")
            }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, fontSize = 14.sp, color = if (hour != null && hour.pop >= 50) C.TertiaryInk else C.InkVar, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            e.fromDevice -> Icon(Icons.Rounded.Event, "Del calendario", tint = C.Outline, modifier = Modifier.size(20.dp))
            on -> Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(C.Primary).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(e.startAt.minusMinutes(e.alarmMinutes.toLong()).format(HM), fontSize = 13.sp, color = Color.White)
            }
            !past -> IconButton(onClick = { toggleAlarm(ctx, e) }) { Icon(Icons.Rounded.AlarmAdd, "Crear alarma", tint = C.Outline) }
        }
    }
}

@Composable
private fun NowRow() {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(C.Error))
        Box(Modifier.weight(1f).height(2.dp).background(C.Error))
    }
}

// ---------- Columna derecha: lista de compras ----------

/** Título de la única nota fijada que se muestra en Inicio. */
private const val COMPRAS = "Compras"

@Composable
private fun ComprasColumn(modifier: Modifier, scrollable: Boolean, onOpen: (Long) -> Unit) {
    // Garantiza que exista la nota "Compras" (una sola, fijada).
    LaunchedEffect(Unit) {
        if (Store.notes.none { it.title == COMPRAS }) {
            Store.upsertNote(Note(Store.newId(), title = COMPRAS, pinned = true, color = 1))
        }
    }
    val nota = Store.notes.firstOrNull { it.title == COMPRAS } ?: return
    var draft by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun addItem() {
        val t = draft.trim()
        if (t.isEmpty()) return
        Store.upsertNote(nota.copy(items = nota.items + CheckItem(t)))
        draft = ""
        focus.requestFocus() // se queda en el mismo campo para seguir escribiendo
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ShoppingCart, null, tint = C.Primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(COMPRAS, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            val pend = nota.items.count { !it.done }
            if (nota.items.isNotEmpty()) Text("$pend pendientes", fontSize = 14.sp, color = C.InkVar)
            IconButton(onClick = { onOpen(nota.id) }) { Icon(Icons.Rounded.OpenInFull, "Abrir nota", tint = C.InkVar) }
        }
        // Textbox arriba de todo: Enter agrega y mantiene el foco.
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Añadir artículo") },
            leadingIcon = { Icon(Icons.Rounded.Add, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
            keyboardActions = KeyboardActions(onDone = { addItem() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        val items: @Composable ColumnScope.() -> Unit = {
            if (nota.items.isEmpty()) {
                Text("La lista está vacía. Escribe arriba y pulsa Enter.", color = C.InkVar, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            } else nota.items.forEachIndexed { i, it -> CompraItem(nota, i, it) }
        }
        if (scrollable) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = items)
        } else {
            Column(content = items)
        }
    }
}

@Composable
private fun CompraItem(nota: Note, index: Int, item: CheckItem) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
            Store.upsertNote(nota.copy(items = nota.items.toMutableList().also { it[index] = item.copy(done = !item.done) }))
        }.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(if (item.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
            tint = if (item.done) C.Primary else C.Outline, modifier = Modifier.size(24.dp))
        Text(item.text, fontSize = 16.sp, modifier = Modifier.weight(1f),
            color = if (item.done) C.Outline else C.Ink,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = {
            Store.upsertNote(nota.copy(items = nota.items.toMutableList().also { it.removeAt(index) }))
        }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "Quitar", tint = C.Outline, modifier = Modifier.size(18.dp))
        }
    }
}
