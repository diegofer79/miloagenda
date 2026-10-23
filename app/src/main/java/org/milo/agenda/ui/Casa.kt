package org.milo.agenda.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.milo.agenda.R
import org.milo.agenda.data.HomeAssistant
import org.milo.agenda.data.HomeSwitch
import org.milo.agenda.data.Store
import org.milo.agenda.data.SwState
import java.time.Instant
import java.time.ZoneId

/** Interruptores de Home Assistant. Tarjetas de tamaño fijo que se acomodan en una grilla. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CasaScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Al abrir, lee el estado actual de los interruptores.
    LaunchedEffect(Store.prefs.haUrl, Store.prefs.haToken) { HomeAssistant.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Casa", onBack) {
            if (HomeAssistant.loading) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            IconButton(onClick = { scope.launch { HomeAssistant.refresh() } }, enabled = HomeAssistant.configured()) {
                Icon(Icons.Rounded.Refresh, "Actualizar")
            }
        }
        val switches = Store.prefs.switches.filter { it.entityId.isNotBlank() }
        when {
            !HomeAssistant.configured() -> CasaHint(
                "Configura Home Assistant en Ajustes (dirección y token) para controlar tus interruptores.",
                onSettings,
            )
            switches.isEmpty() -> CasaHint(
                "Añade el entity_id de tus interruptores en Ajustes para que aparezcan aquí.",
                onSettings,
            )
            else -> {
                HomeAssistant.error?.let {
                    Text(it, color = C.Error, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                }
                FlowRow(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    maxItemsInEachRow = 4,
                ) {
                    switches.forEach { s ->
                        SwitchCard(
                            s = s,
                            state = HomeAssistant.states[s.entityId],
                            offAt = Store.prefs.haAutoOff[s.entityId],
                            onPress = { scope.launch { HomeAssistant.press(ctx, s.entityId) } },
                            onHours = { newH -> setSwitchHours(ctx, s.entityId, newH) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CasaHint(text: String, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 360.dp).padding(24.dp)) {
            Icon(Icons.Rounded.Home, null, tint = C.Outline, modifier = Modifier.size(48.dp))
            Text(text, color = C.InkVar, fontSize = 16.sp, textAlign = TextAlign.Center)
            Button(onClick = onSettings) { Text("Abrir Ajustes") }
        }
    }
}

@Composable
private fun SwitchCard(
    s: HomeSwitch,
    state: SwState?,
    offAt: Long?,
    onPress: () -> Unit,
    onHours: (Int) -> Unit,
) {
    val isOn = state == SwState.ON
    val unavailable = state == SwState.UNAVAILABLE
    // Encendido = bloque verde pleno con texto blanco (se lee a distancia); apagado = tarjeta clara;
    // no disponible = tarjeta atenuada.
    val bg by animateColorAsState(if (isOn) C.Primary else C.SurfaceHigh, label = "switchBg")
    val fg = if (isOn) Color.White else C.InkVar
    Column(
        Modifier.size(width = 208.dp, height = 182.dp)
            .then(if (unavailable) Modifier.alpha(0.55f) else Modifier)
            .clip(RoundedCornerShape(28.dp)).background(bg).clickable { onPress() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            HomeSwitchIcon(s.icon, if (isOn) Color.White else C.Outline, 40)
            Icon(
                if (isOn) Icons.Rounded.ToggleOn else Icons.Rounded.ToggleOff, null,
                tint = if (isOn) Color.White else C.OutlineVar, modifier = Modifier.size(34.dp),
            )
        }
        Column {
            Text(s.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusText(state, offAt), fontSize = 13.sp,
                color = if (isOn) Color.White.copy(alpha = 0.85f) else C.Outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Horas encendido antes de apagarse solo (0 = siempre). Tocar −/+ no alterna el interruptor.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            StepBtn("−", fg) { onHours((s.hours - 1).coerceIn(0, s.maxHours)) }
            Text(if (s.hours == 0) "Siempre" else "${s.hours} h", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
            StepBtn("+", fg) { onHours((s.hours + 1).coerceIn(0, s.maxHours)) }
        }
    }
}

private fun statusText(state: SwState?, offAt: Long?): String = when (state) {
    SwState.ON -> if (offAt != null)
        "Apaga " + Instant.ofEpochMilli(offAt).atZone(ZoneId.systemDefault()).toLocalTime().format(HM)
    else "Encendido"
    SwState.OFF -> "Apagado"
    SwState.UNAVAILABLE -> "No disponible"
    null -> "–"
}

@Composable
private fun StepBtn(label: String, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(fg.copy(alpha = 0.14f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = fg) }
}

/** Cambia las horas de un interruptor; si está encendido, reinicia el temporizador desde ahora. */
private fun setSwitchHours(ctx: Context, entity: String, newHours: Int) {
    val updated = Store.prefs.switches.map {
        if (it.entityId == entity) it.copy(hours = newHours.coerceIn(0, it.maxHours)) else it
    }
    Store.updatePrefs(Store.prefs.copy(switches = updated))
    if (HomeAssistant.states[entity] == SwState.ON) {
        if (newHours > 0) Store.setAutoOff(ctx, entity, System.currentTimeMillis() + newHours * 3_600_000L)
        else Store.clearAutoOff(ctx, entity)
    }
}
