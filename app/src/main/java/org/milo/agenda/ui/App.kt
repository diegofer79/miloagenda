package org.milo.agenda.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.milo.agenda.data.DeviceCalendar
import org.milo.agenda.data.Event
import org.milo.agenda.data.Photos
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo
import java.time.LocalDate
import java.time.LocalTime

enum class Screen { Home, Agenda, Weather, Notes, Settings }

@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var editing by remember { mutableStateOf<Event?>(null) }
    var openNoteId by remember { mutableStateOf<Long?>(null) }
    var agendaDate by remember { mutableStateOf(LocalDate.now()) }
    val prefs = Store.prefs

    LaunchedEffect(prefs.lat, prefs.lon) {
        if (WeatherRepo.isStale()) WeatherRepo.refresh(ctx)
    }

    // Calendario del sistema (Google, etc.): pedir permiso o limpiar según el ajuste.
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Store.refreshDeviceEvents(ctx)
    }
    LaunchedEffect(prefs.showDeviceCalendar) {
        if (!prefs.showDeviceCalendar) Store.clearDeviceEvents()
        else if (!DeviceCalendar.hasPermission(ctx)) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }
    // Recargar al cambiar de pantalla (sin volver a pedir permiso).
    LaunchedEffect(screen, prefs.showDeviceCalendar) {
        if (prefs.showDeviceCalendar && DeviceCalendar.hasPermission(ctx)) Store.refreshDeviceEvents(ctx)
    }
    // Refresco periódico cada 5 min, solo con la app visible (no en protector ni brillo apagado).
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            if (Idle.resumed && !Idle.active && Store.prefs.showDeviceCalendar && DeviceCalendar.hasPermission(ctx)) {
                Store.refreshDeviceEvents(ctx)
            }
        }
    }

    // Protector de pantalla: tras 10 min sin tocar, si hay fotos, se activa.
    LaunchedEffect(prefs.screensaver) {
        if (!prefs.screensaver) { Idle.active = false; return@LaunchedEffect }
        while (true) {
            delay(5_000)
            val idleMs = prefs.screensaverIdleMin.coerceAtLeast(1) * 60_000L
            if (!Idle.active && System.currentTimeMillis() - Idle.lastTouch > idleMs && Photos.count(ctx) > 0) {
                Idle.active = true
            }
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    fun newEvent(date: LocalDate = LocalDate.now()) {
        val start = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        editing = Event(Store.newId(), "", date, start, start.plusHours(1), alarmMinutes = prefs.defaultAlarm)
    }

    // Navegación: al ir a Agenda desde el riel, se abre en el día de hoy.
    val go: (Screen) -> Unit = { s -> if (s == Screen.Agenda) agendaDate = LocalDate.now(); screen = s }

    // Contenido de cada pantalla. En tablet (ancho) va a la derecha del riel fijo.
    val content: @Composable () -> Unit = {
        when (screen) {
            Screen.Home -> HomeScreen(
                nav = go,
                onNewEvent = { newEvent() },
                onEditEvent = { if (!it.fromDevice) editing = it },
                onOpenNote = { openNoteId = it; screen = Screen.Notes },
                onOpenAgendaOn = { d -> agendaDate = d; screen = Screen.Agenda },
            )
            Screen.Agenda -> AgendaScreen(initialDate = agendaDate, onBack = { screen = Screen.Home }, onNew = { newEvent(it) }, onEdit = { if (!it.fromDevice) editing = it })
            Screen.Weather -> WeatherScreen(onBack = { screen = Screen.Home })
            Screen.Notes -> NotesScreen(onBack = { screen = Screen.Home }, initialId = openNoteId)
            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(color = C.Surface, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints {
                if (maxWidth > 760.dp) {
                    // Riel de navegación fijo en todas las pantallas (tablet).
                    Row(Modifier.fillMaxSize()) {
                        NavRail(current = screen, nav = go, onNew = { newEvent() })
                        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                    }
                } else {
                    content()
                }
            }
        }
        if (Idle.active) Screensaver(onDismiss = { Idle.touched(); screen = Screen.Home })
    }

    editing?.let { e -> EventDialog(e, isNew = Store.event(e.id) == null, onDismiss = { editing = null }) }
}

@Composable
fun EventDialog(initial: Event, isNew: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var e by remember(initial.id) { mutableStateOf(initial) }
    val alarmOptions = listOf(-1 to "Sin alarma", 0 to "A la hora", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 h")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        title = { Text(if (isNew) "Nuevo evento" else "Editar evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(min = 420.dp)) {
                OutlinedTextField(e.title, { e = e.copy(title = it) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(e.place, { e = e.copy(place = it) }, label = { Text("Lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(fmtLongDay(e.date), Icons.Rounded.Event, C.SurfaceHigh, C.Ink, onClick = {
                        DatePickerDialog(ctx, { _, y, m, d -> e = e.copy(date = LocalDate.of(y, m + 1, d)) },
                            e.date.year, e.date.monthValue - 1, e.date.dayOfMonth).show()
                    })
                    Pill(e.start.format(HM), Icons.Rounded.Schedule, C.SurfaceHigh, C.Ink, onClick = {
                        TimePickerDialog(ctx, { _, h, m ->
                            val s = LocalTime.of(h, m)
                            val dur = java.time.Duration.between(e.start, e.end).let { if (it.isNegative) java.time.Duration.ofHours(1) else it }
                            e = e.copy(start = s, end = s.plus(dur))
                        }, e.start.hour, e.start.minute, true).show()
                    })
                    Pill("hasta " + e.end.format(HM), null, C.SurfaceHigh, C.Ink, onClick = {
                        TimePickerDialog(ctx, { _, h, m -> e = e.copy(end = LocalTime.of(h, m)) }, e.end.hour, e.end.minute, true).show()
                    })
                }
                Text("Alarma", fontSize = 13.sp, color = C.Primary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    alarmOptions.forEach { (v, label) ->
                        FilterChip(selected = e.alarmMinutes == v, onClick = { e = e.copy(alarmMinutes = v) }, label = { Text(label) })
                    }
                }
                Text("Color", fontSize = 13.sp, color = C.Primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    eventColors.forEachIndexed { i, (bg, _, ink) ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(bg)
                                .border(if (e.color == i) 3.dp else 0.dp, ink, CircleShape)
                                .clickable { e = e.copy(color = i) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Store.upsertEvent(ctx, e.copy(title = e.title.ifBlank { "Evento" })); onDismiss() }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { Store.deleteEvent(ctx, initial); onDismiss() }) { Text("Eliminar", color = C.Error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Activa/desactiva la alarma de un evento con un toque. */
fun toggleAlarm(ctx: android.content.Context, e: Event) {
    val next = if (e.alarmMinutes >= 0) -1 else Store.prefs.defaultAlarm
    Store.upsertEvent(ctx, e.copy(alarmMinutes = next))
}

@Composable
fun EventCard(e: Event, modifier: Modifier = Modifier, showWeather: Boolean = true, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val (bg, fg, ink) = eventColors[e.color.coerceIn(0, eventColors.lastIndex)]
    val past = e.date == LocalDate.now() && e.end < LocalTime.now() || e.date < LocalDate.now()
    val hour = if (showWeather) WeatherRepo.hourAt(e.startAt) else null
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (past) C.SurfaceC else bg).clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 17.sp, color = if (past) C.Outline else fg,
                textDecoration = if (past) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            val info = buildList {
                add("${e.start.format(HM)} – ${e.end.format(HM)}")
                if (e.place.isNotBlank()) add(e.place)
                if (hour != null) add("${deg(hour.temp)}" + if (hour.pop >= 50) " · lluvia ${hour.pop}%" else "")
            }.joinToString(" · ")
            Text(info, fontSize = 14.sp, color = if (past) C.Outline else fg)
        }
        if (hour != null) Icon(weatherIcon(hour.code), null, tint = if (past) C.Outline else fg, modifier = Modifier.size(22.dp))
        if (e.fromDevice) {
            Icon(Icons.Rounded.Event, "Del calendario del sistema", tint = if (past) C.Outline else fg, modifier = Modifier.size(20.dp))
        } else if (!past) {
            val on = e.alarmMinutes >= 0
            Row(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) ink else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, ink, RoundedCornerShape(12.dp))
                    .clickable { toggleAlarm(ctx, e) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (on) Icons.Rounded.NotificationsActive else Icons.Rounded.AlarmAdd, "Alarma",
                    tint = if (on) androidx.compose.ui.graphics.Color.White else ink, modifier = Modifier.size(18.dp))
                Text(if (on) e.startAt.minusMinutes(e.alarmMinutes.toLong()).format(HM) else "Alarma",
                    fontSize = 14.sp, color = if (on) androidx.compose.ui.graphics.Color.White else ink)
            }
        }
    }
}
