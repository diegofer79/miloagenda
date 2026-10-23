package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val wx = WeatherRepo.data
    Column(Modifier.fillMaxSize()) {
        TopBar(Store.prefs.city, onBack) {
            if (WeatherRepo.loading) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            IconButton(onClick = { scope.launch { WeatherRepo.refresh(ctx) } }) { Icon(Icons.Rounded.Refresh, "Actualizar") }
        }
        if (wx == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(WeatherRepo.error ?: "Cargando clima…", color = C.InkVar, fontSize = 16.sp)
                    if (WeatherRepo.error != null) Button(onClick = { scope.launch { WeatherRepo.refresh(ctx) } }) { Text("Reintentar") }
                }
            }
            return@Column
        }
        val today = WeatherRepo.day(LocalDate.now())
        val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val hours = wx.hours.filter { !it.time.isBefore(now) }.take(10)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > 760.dp
            val main: @Composable (Modifier) -> Unit = { m ->
                Tile(C.WeatherBg, m, RoundedCornerShape(32.dp), PaddingValues(28.dp)) {
                    Icon(weatherIcon(wx.code, wx.isDay), null, tint = Color.White, modifier = Modifier.size(if (wide) 130.dp else 88.dp))
                    Text(deg(wx.temp), fontSize = if (wide) 120.sp else 84.sp, fontWeight = FontWeight.Light, color = Color.White, lineHeight = if (wide) 124.sp else 88.sp)
                    Text(weatherText(wx.code), fontSize = 20.sp, color = Color.White)
                    Text(
                        listOfNotNull(today?.let { "Máx ${deg(it.max)} · Mín ${deg(it.min)}" }, "Sensación ${deg(wx.feels)}").joinToString(" · "),
                        fontSize = 15.sp, color = Color.White,
                    )
                    if (wide) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(12.dp))
                    val ago = ((System.currentTimeMillis() - wx.fetched) / 60_000).coerceAtLeast(0)
                    Text("Datos de Open-Meteo.com · actualizado hace $ago min", fontSize = 12.sp, color = Color.White)
                    WeatherRepo.error?.let { Text(it, fontSize = 12.sp, color = C.Error) }
                }
            }
            val detail: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Tile(Color(0xFFEAF0F9), Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), PaddingValues(vertical = 18.dp, horizontal = 12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            hours.forEachIndexed { i, h ->
                                val wet = h.pop >= 50
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (i == 0) "Ahora" else "%02d".format(h.time.hour), fontSize = 13.sp,
                                        color = if (wet) C.TertiaryInk else C.InkVar, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
                                    Icon(weatherIcon(h.code, h.time.hour in 7..20), null, tint = weatherTint(h.code, h.time.hour in 7..20), modifier = Modifier.size(26.dp))
                                    Text(deg(h.temp), fontSize = 16.sp, color = if (wet) C.TertiaryInk else C.Ink)
                                    Text("${h.pop}%", fontSize = 13.sp, color = if (wet) C.TertiaryInk else C.InkVar,
                                        fontWeight = if (wet) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().then(if (wide) Modifier.weight(1f) else Modifier), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Stat(Icons.Rounded.Navigation, "Viento", "${wx.wind.roundToInt()} km/h", windName(wx.windDir),
                                    Color(0xFFCFE5FF), Color(0xFF164463), Modifier.weight(1f), rotate = wx.windDir + 180f)
                                Stat(Icons.Rounded.Opacity, "Humedad", "${wx.humidity}%", "",
                                    C.Tertiary, C.TertiaryInk, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Stat(Icons.Rounded.WbSunny, "Índice UV", today?.uv?.roundToInt()?.toString() ?: "–", uvName(today?.uv ?: 0.0),
                                    C.Peach, C.PeachInk, Modifier.weight(1f))
                                Stat(Icons.Rounded.Speed, "Presión", "${wx.pressure.roundToInt()}", "hPa",
                                    Color(0xFFE9DDFF), Color(0xFF4A3A6E), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Stat(Icons.Rounded.WbSunny, "Amanecer", today?.sunrise?.format(HM) ?: "–", "",
                                    Color(0xFFFFE7A8), Color(0xFF6B4E00), Modifier.weight(1f))
                                Stat(Icons.Rounded.NightsStay, "Atardecer", today?.sunset?.format(HM) ?: "–", "",
                                    Color(0xFFD7DBF5), Color(0xFF363B66), Modifier.weight(1f))
                            }
                        }
                        Tile(C.SurfaceC, Modifier.weight(1f), RoundedCornerShape(28.dp), PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                            val lo = wx.days.minOfOrNull { it.min } ?: 0.0
                            val hi = wx.days.maxOfOrNull { it.max } ?: 1.0
                            val span = (hi - lo).coerceAtLeast(1.0)
                            wx.days.forEach { d ->
                                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(if (d.date == LocalDate.now()) "Hoy" else fmtShortDay(d.date), Modifier.width(48.dp), fontSize = 15.sp)
                                    Icon(weatherIcon(d.code), null, tint = weatherTint(d.code), modifier = Modifier.size(22.dp))
                                    Text(deg(d.min), Modifier.width(36.dp), fontSize = 15.sp, color = C.InkVar)
                                    BoxWithConstraints(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.SurfaceHighest)) {
                                        val start = ((d.min - lo) / span).toFloat()
                                        val len = ((d.max - d.min) / span).toFloat().coerceAtLeast(0.05f)
                                        val wet = d.code in 51..67 || d.code in 80..82 || d.code >= 95
                                        Box(Modifier.offset(x = maxWidth * start).width(maxWidth * len).fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp)).background(if (wet) C.TertiaryInk else C.Primary))
                                    }
                                    Text(deg(d.max), Modifier.width(36.dp), fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                main(Modifier.width(420.dp).fillMaxHeight())
                detail(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                main(Modifier.fillMaxWidth())
                detail(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, unit: String, bg: Color, ink: Color, modifier: Modifier, rotate: Float = 0f) {
    Tile(bg, modifier, RoundedCornerShape(24.dp), PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp).rotate(rotate))
            Text(label, fontSize = 13.sp, color = ink.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = ink)
            if (unit.isNotEmpty()) Text(unit, fontSize = 14.sp, color = ink.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

private fun windName(d: Int) = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[(((d % 360) + 22) / 45) % 8]
private fun uvName(uv: Double) = when {
    uv < 3 -> "bajo"
    uv < 6 -> "moderado"
    uv < 8 -> "alto"
    uv < 11 -> "muy alto"
    else -> "extremo"
}
