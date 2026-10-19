package org.milo.agenda.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.milo.agenda.data.Photos

/** Inactividad para el protector de pantalla. [lastTouch] se refresca en cada toque (Activity). */
object Idle {
    const val IDLE_MS = 10 * 60_000L   // 10 minutos sin tocar -> protector
    const val PHOTO_MS = 30_000L       // 30 segundos por foto

    var lastTouch by mutableLongStateOf(System.currentTimeMillis())
    var active by mutableStateOf(false)

    /** Cualquier toque: reinicia el contador y cierra el protector. */
    fun touched() {
        lastTouch = System.currentTimeMillis()
        active = false
    }
}

/**
 * Protector de pantalla: pasa las fotos de DCIM/milo en orden aleatorio, 30 s cada una.
 * Al terminar la vuelta completa, apaga la pantalla (brillo 0) para ahorrar — sigue respondiendo
 * al toque, así cualquier toque la "enciende" y vuelve a la app (sin usar el botón de power).
 */
@Composable
fun Screensaver(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val window = (ctx as? Activity)?.window
    val photos = remember { Photos.shuffled(ctx) }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var index by remember { mutableIntStateOf(0) }
    var off by remember { mutableStateOf(false) }   // tras pasar todas: pantalla "apagada"
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    // Brillo de la ventana: 0 cuando está "apagada", normal cuando muestra fotos.
    DisposableEffect(off) {
        window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = if (off) 0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        onDispose {}
    }
    // Al salir del protector, restaurar siempre el brillo del sistema.
    DisposableEffect(Unit) {
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // Carga la foto actual (solo mientras no está apagada).
    LaunchedEffect(index, off) {
        if (!off) {
            image = withContext(Dispatchers.IO) {
                Photos.loadScaled(photos[index % photos.size], 1920, 1200)?.asImageBitmap()
            }
        }
    }
    // Avanza cada 30 s; al completar la vuelta, apaga.
    LaunchedEffect(Unit) {
        while (!off) {
            delay(Idle.PHOTO_MS)
            if (index >= photos.size - 1) off = true else index++
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!off) {
            Crossfade(targetState = image, animationSpec = tween(600), label = "foto") { img ->
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        // off == true -> solo negro (brillo a 0); un toque despierta y cierra.
    }
}
