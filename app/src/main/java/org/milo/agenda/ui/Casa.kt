package org.milo.agenda.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Interruptores de Home Assistant. Tarjetas de tamaño fijo que se acomodan en una grilla. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CasaScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
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
                        SwitchCard(s, HomeAssistant.states[s.entityId]) { scope.launch { HomeAssistant.toggle(s.entityId) } }
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
private fun SwitchCard(s: HomeSwitch, on: Boolean?, onToggle: () -> Unit) {
    val isOn = on == true
    // Encendido = bloque verde pleno con texto blanco (se lee a distancia); apagado = tarjeta clara y apagada.
    val bg by animateColorAsState(if (isOn) C.Primary else C.SurfaceHigh, label = "switchBg")
    val fg = if (isOn) Color.White else C.InkVar
    Column(
        Modifier.size(width = 208.dp, height = 150.dp)
            .clip(RoundedCornerShape(28.dp)).background(bg).clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            HomeSwitchIcon(s.icon, if (isOn) Color.White else C.Outline, 44)
            Icon(
                if (isOn) Icons.Rounded.ToggleOn else Icons.Rounded.ToggleOff, null,
                tint = if (isOn) Color.White else C.OutlineVar, modifier = Modifier.size(36.dp),
            )
        }
        Column {
            Text(s.name, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (on) { true -> "Encendido"; false -> "Apagado"; null -> "–" },
                fontSize = 14.sp, color = if (isOn) Color.White.copy(alpha = 0.85f) else C.Outline,
            )
        }
    }
}
