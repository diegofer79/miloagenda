package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.milo.agenda.data.Alarms
import org.milo.agenda.data.Event
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

object TimerState {
    var minutes by mutableIntStateOf(5)
    var endAt by mutableLongStateOf(0L)
    val presets = listOf(1, 3, 5, 10, 15, 25, 45, 60)
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
) {
    val now = rememberNow()
    val w = Store.prefs.widgets
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)) {
            if (wide) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (w.any { it in setOf("clock", "weather", "next") })
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if ("clock" in w) ClockTile(now, 150, Modifier.weight(2f).fillMaxHeight())
                            if ("weather" in w) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                WeatherCircle(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)) { nav(Screen.Weather) }
                            }
                            if ("next" in w) NextTile(now, Modifier.weight(1f).fillMaxHeight(), onEditEvent)
                        }
                    if (w.any { it in setOf("note", "today", "timer", "rain") })
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if ("note" in w) NoteTile(Modifier.weight(1f).fillMaxHeight(), onOpenNote) { nav(Screen.Notes) }
                            if ("today" in w) TodayTile(Modifier.weight(2f).fillMaxHeight(), onEditEvent) { nav(Screen.Agenda) }
                            if ("timer" in w || "rain" in w)
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if ("timer" in w) TimerTile(now, Modifier.weight(1f).fillMaxWidth())
                                    if ("rain" in w) RainTile(now, Modifier.weight(1f).fillMaxWidth()) { nav(Screen.Weather) }
                                }
                        }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val m = Modifier.fillMaxWidth()
                    if ("clock" in w) ClockTile(now, 96, m.height(220.dp))
                    if ("weather" in w) Box(m, contentAlignment = Alignment.Center) { WeatherCircle(Modifier.size(220.dp)) { nav(Screen.Weather) } }
                    if ("next" in w) NextTile(now, m.height(180.dp), onEditEvent)
                    if ("today" in w) TodayTile(m.height(220.dp), onEditEvent) { nav(Screen.Agenda) }
                    if ("note" in w) NoteTile(m.heightIn(min = 160.dp), onOpenNote) { nav(Screen.Notes) }
                    if ("timer" in w) TimerTile(now, m.height(96.dp))
                    if ("rain" in w) RainTile(now, m.height(96.dp)) { nav(Screen.Weather) }
                    if (w.isEmpty()) Text("Activa widgets en Ajustes", color = C.InkVar)
                }
            }
            Spacer(Modifier.height(14.dp))
            Toolbar(nav, onNewEvent)
        }
    }
}

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

@Composable
private fun ClockTile(now: LocalDateTime, size: Int, modifier: Modifier) {
    val next = Store.events.filter { it.alarmMinutes >= 0 }
        .mapNotNull { e -> e.alarmAtMillis()?.let { it to e } }
        .filter { it.first > System.currentTimeMillis() }
        .minByOrNull { it.first }
    Tile(C.Primary, modifier, RoundedCornerShape(40.dp), PaddingValues(horizontal = 32.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(fmtLongDay(now.toLocalDate()), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (next != null) {
                Icon(Icons.Rounded.Alarm, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(next.second.startAt.minusMinutes(next.second.alarmMinutes.toLong()).format(HM), color = Color.White, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                append("%02d".format(now.hour))
                withStyle(SpanStyle(color = C.PrimaryContainer)) { append(":") }
                append("%02d".format(now.minute))
            },
            color = Color.White, fontSize = size.sp, fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-size / 25).sp, lineHeight = size.sp, maxLines = 1,
        )
    }
}

@Composable
private fun WeatherCircle(modifier: Modifier, onClick: () -> Unit) {
    val wx = WeatherRepo.data
    Column(
        modifier.clip(CircleShape).background(C.Tertiary).clickable { onClick() }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (wx == null) {
            if (WeatherRepo.loading) CircularProgressIndicator(color = C.OnTertiary)
            else Text("Sin datos\nde clima", color = C.OnTertiary, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else {
            val today = WeatherRepo.day(LocalDate.now())
            Icon(weatherIcon(wx.code, wx.isDay), null, tint = C.OnTertiary, modifier = Modifier.size(56.dp))
            Text(deg(wx.temp), color = C.OnTertiary, fontSize = 60.sp, lineHeight = 64.sp)
            Text(
                Store.prefs.city + (today?.let { " · ${deg(it.max)} / ${deg(it.min)}" } ?: ""),
                color = C.OnTertiary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NextTile(now: LocalDateTime, modifier: Modifier, onEdit: (Event) -> Unit) {
    val next = Store.allEvents.filter { it.startAt > now }.minByOrNull { it.startAt }
    Tile(
        C.Secondary, modifier, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomEnd = 40.dp, bottomStart = 12.dp),
        PaddingValues(24.dp), onClick = if (next != null) ({ onEdit(next) }) else null,
    ) {
        Text("SIGUIENTE", color = C.OnSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        Spacer(Modifier.weight(1f))
        if (next == null) {
            Text("Nada pendiente", color = C.OnSecondary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(next.title, color = C.OnSecondary, fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${next.start.format(HM)} · ${untilText(now, next.startAt)}", color = C.OnSecondary, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            if (next.alarmMinutes >= 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = C.OnSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Alarma " + next.startAt.minusMinutes(next.alarmMinutes.toLong()).format(HM), color = C.OnSecondary, fontSize = 14.sp)
            }
        }
    }
}

private fun untilText(now: LocalDateTime, t: LocalDateTime): String {
    val m = Duration.between(now, t).toMinutes()
    return when {
        m < 60 -> "en $m min"
        m < 24 * 60 -> "en ${m / 60} h ${m % 60} min"
        else -> fmtShortDay(t.toLocalDate()) + " " + t.dayOfMonth
    }
}

@Composable
private fun NoteTile(modifier: Modifier, onOpen: (Long) -> Unit, onAll: () -> Unit) {
    val n = Store.notes.filter { it.pinned }.maxByOrNull { it.updated }
    Tile(C.SurfaceC, modifier, onClick = { if (n != null) onOpen(n.id) else onAll() }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(n?.title?.ifBlank { "Nota" } ?: "Notas", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.PushPin, null, tint = C.InkVar, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (n == null) Text("Fija una nota para verla aquí.", color = C.InkVar, fontSize = 15.sp)
        else {
            if (n.body.isNotBlank()) Text(n.body, color = C.InkVar, fontSize = 15.sp, maxLines = if (n.items.isEmpty()) 8 else 2, overflow = TextOverflow.Ellipsis)
            n.items.take(6).forEachIndexed { i, it ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        Store.upsertNote(n.copy(items = n.items.toMutableList().also { l -> l[i] = it.copy(done = !it.done) }))
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (it.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                        tint = if (it.done) C.Primary else C.InkVar, modifier = Modifier.size(22.dp))
                    Text(it.text, fontSize = 15.sp, color = if (it.done) C.Outline else C.Ink,
                        textDecoration = if (it.done) TextDecoration.LineThrough else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TodayTile(modifier: Modifier, onEdit: (Event) -> Unit, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val list = Store.eventsOn(LocalDate.now())
    Tile(C.SurfaceC, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hoy", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(if (list.size == 1) "1 evento" else "${list.size} eventos", color = C.InkVar, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onOpen) { Text("Abrir agenda") }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin eventos hoy", color = C.InkVar) }
        } else LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { e ->
                val h = WeatherRepo.hourAt(e.startAt)
                val on = e.alarmMinutes >= 0
                Column(
                    Modifier.width(180.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(C.Surface)
                        .clickable { onEdit(e) }.padding(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.start.format(HM), fontSize = 13.sp, color = C.InkVar, modifier = Modifier.weight(1f))
                        if (h != null) {
                            Text(deg(h.temp), fontSize = 13.sp, color = if (h.pop >= 50) C.TertiaryInk else C.InkVar)
                            Icon(weatherIcon(h.code), null, tint = C.InkVar, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(e.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    if (e.fromDevice) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Event, null, tint = C.Outline, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Calendario", fontSize = 12.sp, color = C.Outline)
                        }
                    } else IconButton(onClick = { toggleAlarm(ctx, e) }, modifier = Modifier.offset(x = (-12).dp)) {
                        Icon(if (on) Icons.Rounded.NotificationsActive else Icons.Rounded.AlarmAdd,
                            if (on) "Quitar alarma" else "Crear alarma", tint = if (on) C.Primary else C.Outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerTile(now: LocalDateTime, modifier: Modifier) {
    val ctx = LocalContext.current
    val running = TimerState.endAt > System.currentTimeMillis()
    val remaining = if (running) (TimerState.endAt - System.currentTimeMillis()) / 1000 else TimerState.minutes * 60L
    now.second // recompone cada segundo
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(C.SurfaceHigh).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.Timer, null, tint = C.Primary, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = !running) {
            val p = TimerState.presets
            TimerState.minutes = p[(p.indexOf(TimerState.minutes) + 1) % p.size]
        }.padding(vertical = 6.dp)) {
            Text("%02d:%02d".format(remaining / 60, remaining % 60), fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text(if (running) "Temporizador" else "Toca para cambiar", fontSize = 13.sp, color = C.InkVar)
        }
        RoundIcon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (running) "Parar" else "Iniciar", C.Primary, Color.White, 48) {
            if (running) {
                TimerState.endAt = 0L
                Alarms.cancel(ctx, Alarms.TIMER_ID)
            } else {
                TimerState.endAt = System.currentTimeMillis() + TimerState.minutes * 60_000L
                Alarms.at(ctx, Alarms.TIMER_ID, TimerState.endAt, "Temporizador", "${TimerState.minutes} min terminados")
            }
        }
    }
}

@Composable
private fun RainTile(now: LocalDateTime, modifier: Modifier, onClick: () -> Unit) {
    val rain = WeatherRepo.data?.hours?.firstOrNull {
        it.time.toLocalDate() == now.toLocalDate() && it.time.hour >= now.hour && it.pop >= 50
    }
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(C.SurfaceHigh).clickable { onClick() }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(if (rain != null) Icons.Rounded.BeachAccess else Icons.Rounded.WbSunny, null, tint = C.TertiaryInk, modifier = Modifier.size(32.dp))
        Column {
            Text(if (rain != null) "${rain.pop}%" else "Sin lluvia", fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text(if (rain != null) "lluvia a las ${rain.time.hour} h" else "el resto del día", fontSize = 13.sp, color = C.InkVar)
        }
    }
}
