package org.milo.agenda.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class Event(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val place: String = "",
    val alarmMinutes: Int = -1, // -1 = sin alarma
    val color: Int = 0,
    val fromDevice: Boolean = false, // evento del calendario del sistema (Google, etc.), solo lectura
) {
    val startAt: LocalDateTime get() = LocalDateTime.of(date, start)
    fun alarmAtMillis(): Long? =
        if (alarmMinutes < 0) null
        else startAt.minusMinutes(alarmMinutes.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

data class CheckItem(val text: String, val done: Boolean = false)

data class Note(
    val id: Long,
    val title: String = "",
    val body: String = "",
    val items: List<CheckItem> = emptyList(),
    val color: Int = 0,
    val pinned: Boolean = false,
    val eventId: Long? = null,
    val updated: Long = System.currentTimeMillis(),
)

val ALL_WIDGETS = listOf(
    "clock" to "Reloj",
    "weather" to "Clima",
    "next" to "Siguiente evento",
    "note" to "Nota fijada",
    "today" to "Agenda de hoy",
    "timer" to "Temporizador",
    "rain" to "Aviso de lluvia",
)

data class Prefs(
    val city: String = "Valencia",
    val lat: Double = 39.47,
    val lon: Double = -0.376,
    val defaultAlarm: Int = 15,
    val widgets: Set<String> = ALL_WIDGETS.map { it.first }.toSet(),
    val showDeviceCalendar: Boolean = true, // mostrar eventos del calendario del sistema (Google, etc.)
    val calendars: Set<String> = emptySet(), // nombres de calendarios a mostrar; vacío = todos
    val screensaver: Boolean = true,        // protector de pantalla con fotos
    val screensaverIdleMin: Int = 10,       // minutos de inactividad antes del protector
    val screensaverPhotoSec: Int = 30,      // segundos por foto
)

object Store {
    val events = mutableStateListOf<Event>()           // eventos locales (editables, con alarma, persistidos)
    val deviceEvents = mutableStateListOf<Event>()      // eventos del calendario del sistema (solo lectura)
    val notes = mutableStateListOf<Note>()
    var prefs by mutableStateOf(Prefs())
        private set

    private var dir: File? = null
    private var seq = 0L

    fun newId(): Long = System.currentTimeMillis() * 10 + (seq++ % 10)

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        load()
    }

    // ---------- eventos ----------
    /** Locales + del sistema, para las vistas (agenda, inicio). */
    val allEvents: List<Event> get() = events + deviceEvents
    fun eventsOn(d: LocalDate) = allEvents.filter { it.date == d }.sortedBy { it.start }
    fun event(id: Long?) = events.firstOrNull { it.id == id }

    /** Recarga los eventos del calendario del sistema para una ventana amplia. */
    fun refreshDeviceEvents(ctx: Context) {
        val from = LocalDate.now().minusMonths(1)
        val to = LocalDate.now().plusMonths(6)
        val list = runCatching { DeviceCalendar.eventsBetween(ctx, from, to) }.getOrDefault(emptyList())
        deviceEvents.clear()
        deviceEvents.addAll(list)
    }

    fun clearDeviceEvents() = deviceEvents.clear()

    fun upsertEvent(ctx: Context, e: Event) {
        val i = events.indexOfFirst { it.id == e.id }
        if (i >= 0) events[i] = e else events.add(e)
        saveEvents()
        Alarms.scheduleEvent(ctx, e)
    }

    fun deleteEvent(ctx: Context, e: Event) {
        events.removeAll { it.id == e.id }
        saveEvents()
        Alarms.cancel(ctx, e.id)
    }

    // ---------- notas ----------
    fun note(id: Long?) = notes.firstOrNull { it.id == id }

    fun upsertNote(n: Note) {
        val i = notes.indexOfFirst { it.id == n.id }
        val stamped = n.copy(updated = System.currentTimeMillis())
        if (i >= 0) notes[i] = stamped else notes.add(0, stamped)
        saveNotes()
    }

    fun deleteNote(id: Long) {
        notes.removeAll { it.id == id }
        saveNotes()
    }

    /** Añade un artículo a la nota "Compras" (la crea si no existe). Usado por el servidor local. */
    fun addToCompras(text: String) {
        val base = notes.firstOrNull { it.title == "Compras" }
            ?: Note(newId(), title = "Compras", pinned = true, color = 1)
        upsertNote(base.copy(items = base.items + CheckItem(text)))
    }

    // ---------- ajustes ----------
    fun updatePrefs(p: Prefs) {
        prefs = p
        write("prefs.json", JSONObject().apply {
            put("city", p.city); put("lat", p.lat); put("lon", p.lon)
            put("defaultAlarm", p.defaultAlarm)
            put("widgets", JSONArray(p.widgets.toList()))
            put("showDeviceCalendar", p.showDeviceCalendar)
            put("calendars", JSONArray(p.calendars.toList()))
            put("screensaver", p.screensaver)
            put("screensaverIdleMin", p.screensaverIdleMin)
            put("screensaverPhotoSec", p.screensaverPhotoSec)
        }.toString())
    }

    // ---------- persistencia (JSON local, sin nube) ----------
    private fun write(name: String, text: String) {
        val d = dir ?: return
        val tmp = File(d, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(d, name))
    }

    private fun read(name: String): String? {
        val f = File(dir ?: return null, name)
        return if (f.exists()) f.readText() else null
    }

    private fun saveEvents() {
        val a = JSONArray()
        events.forEach { e ->
            a.put(JSONObject().apply {
                put("id", e.id); put("title", e.title); put("date", e.date.toString())
                put("start", e.start.toString()); put("end", e.end.toString())
                put("place", e.place); put("alarm", e.alarmMinutes); put("color", e.color)
            })
        }
        write("events.json", a.toString())
    }

    private fun saveNotes() {
        val a = JSONArray()
        notes.forEach { n ->
            a.put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("body", n.body)
                put("color", n.color); put("pinned", n.pinned); put("updated", n.updated)
                if (n.eventId != null) put("eventId", n.eventId)
                put("items", JSONArray().apply {
                    n.items.forEach { put(JSONObject().put("t", it.text).put("d", it.done)) }
                })
            })
        }
        write("notes.json", a.toString())
    }

    private fun load() {
        read("prefs.json")?.let { runCatching {
            val o = JSONObject(it)
            val w = o.optJSONArray("widgets")
            prefs = Prefs(
                city = o.optString("city", "Valencia"),
                lat = o.optDouble("lat", 39.47),
                lon = o.optDouble("lon", -0.376),
                defaultAlarm = o.optInt("defaultAlarm", 15),
                widgets = if (w == null) Prefs().widgets else (0 until w.length()).map { i -> w.getString(i) }.toSet(),
                showDeviceCalendar = o.optBoolean("showDeviceCalendar", true),
                calendars = o.optJSONArray("calendars")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet(),
                screensaver = o.optBoolean("screensaver", true),
                screensaverIdleMin = o.optInt("screensaverIdleMin", 10),
                screensaverPhotoSec = o.optInt("screensaverPhotoSec", 30),
            )
        } }

        read("events.json")?.let { runCatching {
            val a = JSONArray(it)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                events.add(Event(
                    id = o.getLong("id"), title = o.optString("title"),
                    date = LocalDate.parse(o.getString("date")),
                    start = LocalTime.parse(o.getString("start")),
                    end = LocalTime.parse(o.getString("end")),
                    place = o.optString("place"), alarmMinutes = o.optInt("alarm", -1),
                    color = o.optInt("color", 0),
                ))
            }
        } }

        val notesText = read("notes.json")
        if (notesText == null) {
            notes.add(Note(
                id = newId(), title = "Bienvenida", pinned = true, color = 1,
                body = "Todo lo que escribas se guarda solo en esta tablet. Sin cuentas ni nube.",
                items = listOf(CheckItem("Añadir un evento con el botón Nuevo"), CheckItem("Elegir tu ciudad en Ajustes")),
            ))
            saveNotes()
        } else runCatching {
            val a = JSONArray(notesText)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val arr = o.optJSONArray("items") ?: JSONArray()
                notes.add(Note(
                    id = o.getLong("id"), title = o.optString("title"), body = o.optString("body"),
                    color = o.optInt("color"), pinned = o.optBoolean("pinned"),
                    eventId = if (o.has("eventId")) o.getLong("eventId") else null,
                    updated = o.optLong("updated"),
                    items = (0 until arr.length()).map { j ->
                        val c = arr.getJSONObject(j); CheckItem(c.optString("t"), c.optBoolean("d"))
                    },
                ))
            }
        }
    }
}
