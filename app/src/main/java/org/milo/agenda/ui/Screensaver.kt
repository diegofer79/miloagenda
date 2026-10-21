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
import org.milo.agenda.data.Store

/** Inactividad para el protector de pantalla. [lastTouch] se refresca en cada toque (Activity). */
object Idle {
    var lastTouch by mutableLongStateOf(System.currentTimeMillis())
    var active by mutableStateOf(false)      // protector de fotos a la vista (requiere fotos)
    var allowSleep by mutableStateOf(false)  // permitir que el sistema apague la pantalla (haya o no fotos)
    var resumed by mutableStateOf(true)      // la Activity está en primer plano (visible)

    /** Cualquier toque: reinicia el contador, cierra el protector y vuelve a bloquear la pantalla. */
    fun touched() {
        lastTouch = System.currentTimeMillis()
        active = false
        allowSleep = false
    }
}

/**
 * Protector de pantalla: pasa las fotos de DCIM/milo en orden aleatorio, en bucle, con el intervalo
 * configurado. Cualquier toque cierra y vuelve a la app (vía [onDismiss]).
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

    LaunchedEffect(index) {
        image = withContext(Dispatchers.IO) {
            Photos.loadScaled(photos[index % photos.size], 1920, 1200)?.asImageBitmap()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Store.prefs.screensaverPhotoSec.coerceAtLeast(5) * 1000L)
            if (Idle.resumed) index++ // bucle infinito; no avanzar con la pantalla apagada
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
