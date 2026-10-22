package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object C {
    val Primary = Color(0xFF4C6A3D)
    val PrimaryContainer = Color(0xFFCDEDB7)
    val OnPrimaryContainer = Color(0xFF0B2000)
    val Secondary = Color(0xFFD9E7CB)
    val OnSecondary = Color(0xFF131F0D)
    val Tertiary = Color(0xFFBCEBEB)
    val OnTertiary = Color(0xFF002020)
    val TertiaryInk = Color(0xFF386666)
    val Peach = Color(0xFFFFDCC4)
    val OnPeach = Color(0xFF2F1500)
    val PeachInk = Color(0xFF8B5000)
    val Surface = Color(0xFFFBF9F3)
    val SurfaceC = Color(0xFFF0EEE6)
    val SurfaceHigh = Color(0xFFEAE8E0)
    val SurfaceHighest = Color(0xFFE3E3DA)
    val Ink = Color(0xFF1B1C18)
    val InkVar = Color(0xFF44483F)
    val Outline = Color(0xFF75796E)
    val OutlineVar = Color(0xFFC5C8BB)
    val Error = Color(0xFFBA1A1A)

    // Diseño 2b "Bloques vivos": reloj, clima y temporizador como bloques de color intenso.
    val ClockBg = Color(0xFF2F4A22)
    val ClockColon = Color(0xFF9ED67E)
    val ClockSub = Color(0xFFCDEDB7)
    val WeatherBg = Color(0xFF2E6A9E)
    val TimerBg = Color(0xFFC25E00)
    val TimerSub = Color(0xFFFFDCBE)
    // Tintes de los iconos del riel oscuro (2b).
    val RailGreen = Color(0xFFCDEDB7)
    val RailBlue = Color(0xFFCFE5FF)
    val RailPink = Color(0xFFFFDADA)
}

/** Fondo, texto, acento — paleta de eventos del diseño 2b (bloques sólidos). */
val eventColors = listOf(
    Triple(Color(0xFFFFDADA), Color(0xFF40000A), Color(0xFFB3404A)),
    Triple(Color(0xFFFFDCBE), Color(0xFF2C1600), Color(0xFFC25E00)),
    Triple(Color(0xFFE9DDFF), Color(0xFF21124A), Color(0xFF66558E)),
)
/** Notas tipo pósit del diseño 2b. */
val noteColors = listOf(
    C.SurfaceC to C.Ink,
    Color(0xFFB5EBC4) to Color(0xFF0B2000),
    Color(0xFFFFE07A) to Color(0xFF2C1600),
    Color(0xFFFFB4A8) to Color(0xFF40000A),
    Color(0xFFE9DDFF) to Color(0xFF21124A),
)

@Composable
fun MiloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Primary, onPrimary = Color.White,
            primaryContainer = C.PrimaryContainer, onPrimaryContainer = C.OnPrimaryContainer,
            secondaryContainer = C.Secondary, onSecondaryContainer = C.OnSecondary,
            tertiaryContainer = C.Tertiary, onTertiaryContainer = C.OnTertiary,
            background = C.Surface, surface = C.Surface, onSurface = C.Ink, onSurfaceVariant = C.InkVar,
            surfaceVariant = C.SurfaceHigh, surfaceContainer = C.SurfaceC, surfaceContainerHigh = C.SurfaceHigh,
            surfaceContainerHighest = C.SurfaceHighest, surfaceContainerLow = C.SurfaceC,
            outline = C.Outline, outlineVariant = C.OutlineVar, error = C.Error,
        ),
        content = content,
    )
}

val ES: Locale = Locale("es", "ES")
val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val longDay = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val shortDay = DateTimeFormatter.ofPattern("EEE", ES)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", ES)

fun String.cap() = replaceFirstChar { it.titlecase(ES) }
fun fmtLongDay(d: LocalDate) = d.format(longDay).cap()
fun fmtShortDay(d: LocalDate) = d.format(shortDay).trimEnd('.').cap()
fun fmtMonth(d: LocalDate) = d.format(monthYear).cap()
fun deg(v: Double) = if (v.isNaN()) "–" else "${v.roundToInt()}°"

fun weatherIcon(code: Int, day: Boolean = true): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> Icons.Rounded.FilterDrama
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Dehaze
    in 51..57 -> Icons.Rounded.Grain
    in 61..67, in 80..82 -> Icons.Rounded.BeachAccess
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.FlashOn
    else -> Icons.Rounded.Cloud
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Despejado"
    1 -> "Mayormente despejado"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    in 51..57 -> "Llovizna"
    in 61..67 -> "Lluvia"
    in 71..77 -> "Nieve"
    in 80..82 -> "Chubascos"
    85, 86 -> "Chubascos de nieve"
    in 95..99 -> "Tormenta"
    else -> "—"
}

@Composable
fun Tile(
    bg: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    padding: PaddingValues = PaddingValues(22.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Atrás") }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

@Composable
fun Pill(text: String, icon: ImageVector?, bg: Color, fg: Color, onClick: () -> Unit, border: Color? = null) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (border != null) Modifier.background(Color.Transparent) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, desc: String, bg: Color, fg: Color, size: Int = 52, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = fg, modifier = Modifier.size(24.dp)) }
}
