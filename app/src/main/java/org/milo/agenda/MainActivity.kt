package org.milo.agenda

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.milo.agenda.data.LocalServer
import org.milo.agenda.data.Photos
import org.milo.agenda.data.Store
import org.milo.agenda.data.WeatherRepo
import org.milo.agenda.ui.App
import org.milo.agenda.ui.Idle
import org.milo.agenda.ui.MiloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantener la pantalla encendida mientras la app está en primer plano (sin permisos).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Store.init(this)
        WeatherRepo.init(this)
        LocalServer.start { Store.addToCompras(it) } // añadir a Compras desde el teléfono (WiFi)
        requestStartupPermissions()
        setContent { MiloTheme { App() } }
    }

    private fun requestStartupPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                perms += Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                perms += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            if (checkSelfPermission(Photos.permission()) != PackageManager.PERMISSION_GRANTED)
                perms += Photos.permission()
        }
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1)
    }

    /** Cualquier interacción reinicia el contador de inactividad y cierra el protector. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        Idle.touched()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalServer.stop()
    }
}
