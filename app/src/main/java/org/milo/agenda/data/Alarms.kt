package org.milo.agenda.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.milo.agenda.AlarmReceiver
import org.milo.agenda.MainActivity
import org.milo.agenda.SwitchOffReceiver

object Alarms {
    const val TIMER_ID = 1L

    private fun flags() =
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun code(id: Long) = (id % Int.MAX_VALUE).toInt()

    private fun pending(ctx: Context, id: Long, title: String, text: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("id", id).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(ctx, code(id), i, flags())
    }

    fun at(ctx: Context, id: Long, atMillis: Long, title: String, text: String) {
        if (atMillis <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, id, title, text)
        val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), flags())
        try {
            // setAlarmClock: exacta, funciona en reposo y muestra el icono de alarma (API 21+)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, id, "", ""))
    }

    fun scheduleEvent(ctx: Context, e: Event) {
        cancel(ctx, e.id)
        val at = e.alarmAtMillis() ?: return
        val txt = buildString {
            append(e.start.toString())
            if (e.place.isNotBlank()) append(" · ").append(e.place)
        }
        at(ctx, e.id, at, e.title.ifBlank { "Evento" }, txt)
    }

    fun rescheduleAll(ctx: Context) = Store.events.forEach { scheduleEvent(ctx, it) }

    // ---------- auto-apagado de interruptores de Home Assistant ----------
    private fun switchIntent(ctx: Context, entity: String) =
        Intent(ctx, SwitchOffReceiver::class.java).putExtra("entity", entity)

    private fun switchPending(ctx: Context, entity: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, "haoff:$entity".hashCode(), switchIntent(ctx, entity), flags())

    /** Programa (o dispara, si ya pasó) el apagado de [entity] en [atMillis]. Exacta, sobrevive reposo. */
    fun scheduleSwitchOff(ctx: Context, entity: String, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) { ctx.sendBroadcast(switchIntent(ctx, entity)); return }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = switchPending(ctx, entity)
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else am.setExact(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancelSwitchOff(ctx: Context, entity: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(switchPending(ctx, entity))
    }

    /** Reprograma los apagados pendientes (al arrancar / al reiniciar el dispositivo). */
    fun rescheduleSwitchOffs(ctx: Context) {
        val now = System.currentTimeMillis()
        Store.prefs.haAutoOff.forEach { (entity, at) ->
            if (at > now) scheduleSwitchOff(ctx, entity, at) else ctx.sendBroadcast(switchIntent(ctx, entity))
        }
    }
}
