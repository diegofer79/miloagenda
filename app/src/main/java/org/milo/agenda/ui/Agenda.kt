package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.milo.agenda.data.Event
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun AgendaScreen(onBack: () -> Unit, onNew: (LocalDate) -> Unit, onEdit: (Event) -> Unit) {
    var selEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val sel = LocalDate.ofEpochDay(selEpoch)
    var month by remember { mutableStateOf(sel.withDayOfMonth(1)) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Agenda", onBack) {
            TextButton(onClick = { selEpoch = LocalDate.now().toEpochDay(); month = LocalDate.now().withDayOfMonth(1) }) {
                Icon(Icons.Rounded.Today, null); Spacer(Modifier.width(6.dp)); Text("Hoy")
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > 760.dp
            if (wide) Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MonthPanel(month, sel, { month = it }, { selEpoch = it.toEpochDay() }, Modifier.width(340.dp).fillMaxHeight())
                DayList(sel, Modifier.weight(1f).fillMaxHeight(), onNew, onEdit)
            } else Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                MonthPanel(month, sel, { month = it }, { selEpoch = it.toEpochDay() }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                DayList(sel, Modifier.weight(1f), onNew, onEdit)
            }
        }
    }
}

@Composable
private fun MonthPanel(month: LocalDate, sel: LocalDate, setMonth: (LocalDate) -> Unit, select: (LocalDate) -> Unit, modifier: Modifier) {
    val today = LocalDate.now()
    val withEvents = Store.allEvents.map { it.date }.toSet()
    Tile(C.SurfaceC, modifier, RoundedCornerShape(28.dp), PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtMonth(month), fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = { setMonth(month.minusMonths(1)) }) { Icon(Icons.Rounded.ChevronLeft, "Mes anterior") }
            IconButton(onClick = { setMonth(month.plusMonths(1)) }) { Icon(Icons.Rounded.ChevronRight, "Mes siguiente") }
        }
        Spacer(Modifier.height(8.dp))
        Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = C.Outline)
        } }
        val offset = month.dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) Row(Modifier.padding(vertical = 2.dp)) {
            for (c in 0 until 7) {
                val n = r * 7 + c - offset + 1
                Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                    if (n in 1..days) {
                        val d = month.withDayOfMonth(n)
                        val isSel = d == sel
                        Column(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (isSel) C.Primary else Color.Transparent)
                                .clickable { select(d) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$n", fontSize = 15.sp,
                                color = if (isSel) Color.White else if (d == today) C.Primary else C.Ink,
                                fontWeight = if (isSel || d == today) FontWeight.SemiBold else FontWeight.Normal)
                            Box(Modifier.size(4.dp).clip(CircleShape).background(
                                if (d in withEvents) (if (isSel) Color.White else C.Primary) else Color.Transparent))
                        }
                    }
                }
            }
        }
        val day = WeatherRepo.day(sel)
        if (day != null) {
            Spacer(Modifier.height(12.dp))
            val rainHour = WeatherRepo.data?.hours?.firstOrNull { it.time.toLocalDate() == sel && it.pop >= 50 }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.PrimaryContainer).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(weatherIcon(day.code), null, tint = C.OnPrimaryContainer, modifier = Modifier.size(32.dp))
                Column {
                    Text("${deg(day.max)} / ${deg(day.min)} · ${weatherText(day.code)}", color = C.OnPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(rainHour?.let { "Lluvia a partir de las ${it.time.hour} h" } ?: "Sin lluvia prevista", color = C.OnPrimaryContainer, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DayList(sel: LocalDate, modifier: Modifier, onNew: (LocalDate) -> Unit, onEdit: (Event) -> Unit) {
    val list = Store.eventsOn(sel)
    val isToday = sel == LocalDate.now()
    val now = LocalTime.now()
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 16.dp)) {
                Text(fmtLongDay(sel), fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                val alarms = list.count { it.alarmMinutes >= 0 }
                Text("${list.size} eventos · $alarms alarmas", fontSize = 15.sp, color = C.InkVar, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nada este día", color = C.InkVar, fontSize = 16.sp)
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                var nowShown = !isToday
                list.forEach { e ->
                    if (!nowShown && e.start > now) {
                        nowShown = true
                        item("now") { NowLine() }
                    }
                    item(e.id) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val h = WeatherRepo.hourAt(e.startAt)
                            Column(Modifier.width(64.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
                                Text(e.start.format(HM), fontSize = 14.sp, color = C.InkVar)
                                if (h != null) Text(deg(h.temp), fontSize = 12.sp, color = if (h.pop >= 50) C.TertiaryInk else C.Outline)
                            }
                            EventCard(e, Modifier.weight(1f), showWeather = false) { onEdit(e) }
                        }
                    }
                }
                if (!nowShown) item("now") { NowLine() }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onNew(sel) },
            containerColor = C.PrimaryContainer, contentColor = C.OnPrimaryContainer,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Evento") },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun NowLine() {
    Row(Modifier.padding(start = 72.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(C.Error))
        Box(Modifier.weight(1f).height(2.dp).background(C.Error))
        Spacer(Modifier.width(8.dp))
        Text(LocalTime.now().format(HM), fontSize = 12.sp, color = C.Error, fontWeight = FontWeight.SemiBold)
    }
}
