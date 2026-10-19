package org.milo.agenda.ui

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
 * Protector de pantalla: pasa fotos de DCIM/Camera/milo en orden aleatorio, 30 s cada una.
 * Cualquier toque cierra y vuelve a la app (vía [onDismiss]).
 */
@Composable
fun Screensaver(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val photos = remember { Photos.shuffled(ctx) }
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var index by remember { mutableIntStateOf(0) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    // Carga la foto actual escalada, fuera del hilo principal.
    LaunchedEffect(index) {
        image = withContext(Dispatchers.IO) {
            Photos.loadScaled(photos[index % photos.size], 1920, 1200)?.asImageBitmap()
        }
    }
    // Avanza cada 30 s.
    LaunchedEffect(Unit) {
        while (true) {
            delay(Idle.PHOTO_MS)
            index++
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                // Cualquier toque (down) cierra el protector.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
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
}
