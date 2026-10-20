package org.milo.agenda.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Lee los eventos del calendario del sistema (CalendarProvider) mediante CalendarContract.
 *
 * Esto incluye los calendarios de Google que el dispositivo ya sincroniza, sin usar la API de
 * Google, sin Play Services y sin iniciar sesión: solo el permiso READ_CALENDAR. Es de solo
 * lectura; los eventos no se guardan ni se editan desde la app.
 */
object DeviceCalendar {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Un calendario del sistema: id interno, nombre visible y cuenta. */
    data class CalInfo(val id: Long, val name: String, val account: String)

    /** Lista los calendarios disponibles (para elegir cuáles mostrar). */
    fun calendars(ctx: Context): List<CalInfo> {
        if (!hasPermission(ctx)) return emptyList()
        val proj = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME)
        val out = ArrayList<CalInfo>()
        ctx.contentResolver.query(Calendars.CONTENT_URI, proj, null, null, "${Calendars.CALENDAR_DISPLAY_NAME} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Calendars._ID)
            val nameCol = c.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
            val accCol = c.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
            while (c.moveToNext()) {
                out.add(CalInfo(c.getLong(idCol), c.getString(nameCol) ?: "", c.getString(accCol) ?: ""))
            }
        }
        return out
    }

    private val PROJECTION = arrayOf(
        Instances.TITLE,
        Instances.BEGIN,
        Instances.END,
        Instances.EVENT_LOCATION,
        Instances.ALL_DAY,
    )

    fun eventsBetween(ctx: Context, from: LocalDate, to: LocalDate): List<Event> {
        if (!hasPermission(ctx)) return emptyList()
        val zone = ZoneId.systemDefault()
        val startMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()

        // Filtro por calendarios seleccionados (nombres). Vacío = todos.
        val wanted = Store.prefs.calendars
        var selection: String? = null
        if (wanted.isNotEmpty()) {
            val ids = calendars(ctx).filter { it.name in wanted }.map { it.id }
            if (ids.isEmpty()) return emptyList()
            selection = Instances.CALENDAR_ID + " IN (" + ids.joinToString(",") + ")"
        }

        val result = ArrayList<Event>()
        // Id sintético negativo y único por recarga: nunca se consulta (solo lectura), solo sirve
        // de clave de lista. Evita colisiones entre instancias de eventos recurrentes.
        var synthetic = -1L
        ctx.contentResolver.query(uri, PROJECTION, selection, null, Instances.BEGIN + " ASC")?.use { c ->
            val titleCol = c.getColumnIndexOrThrow(Instances.TITLE)
            val beginCol = c.getColumnIndexOrThrow(Instances.BEGIN)
            val endCol = c.getColumnIndexOrThrow(Instances.END)
            val locCol = c.getColumnIndexOrThrow(Instances.EVENT_LOCATION)
            val allDayCol = c.getColumnIndexOrThrow(Instances.ALL_DAY)
            while (c.moveToNext()) {
                val begin = c.getLong(beginCol)
                val end = c.getLong(endCol)
                val allDay = c.getInt(allDayCol) == 1
                // Los eventos de todo el día se guardan a medianoche UTC.
                val beginZone = if (allDay) ZoneOffset.UTC else zone
                val startDt = Instant.ofEpochMilli(begin).atZone(beginZone).toLocalDateTime()
                val endDt = Instant.ofEpochMilli(end).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDateTime()
                result.add(
                    Event(
                        id = synthetic--, // negativo y único por recarga; no choca con los locales
                        title = c.getString(titleCol)?.ifBlank { "(Sin título)" } ?: "(Sin título)",
                        date = startDt.toLocalDate(),
                        start = if (allDay) LocalTime.MIDNIGHT else startDt.toLocalTime(),
                        end = if (allDay) LocalTime.of(23, 59) else endDt.toLocalTime(),
                        place = c.getString(locCol) ?: "",
                        alarmMinutes = -1,
                        color = 1, // tono distinto (teal) para diferenciarlos de los locales
                        fromDevice = true,
                    ),
                )
            }
        }
        return result
    }
}
