package org.milo.agenda.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Fotos del protector de pantalla: lee las imágenes de DCIM/Camera/milo.
 * Solo lectura; escala y rota (EXIF) para mostrarlas sin agotar memoria en tablets viejas.
 */
object Photos {
    const val FOLDER = "DCIM/milo"
    private val EXT = setOf("jpg", "jpeg", "png", "webp")

    private fun dir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "milo")

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission()) == PackageManager.PERMISSION_GRANTED

    private fun files(ctx: Context): List<File> {
        if (!hasPermission(ctx)) return emptyList()
        return dir().listFiles { f -> f.isFile && f.extension.lowercase() in EXT }?.toList() ?: emptyList()
    }

    fun count(ctx: Context): Int = files(ctx).size

    /** Lista de fotos en orden aleatorio. */
    fun shuffled(ctx: Context): List<File> = files(ctx).shuffled()

    /**
     * Decodifica la foto escalada a ~[reqW]x[reqH] y la rota según EXIF.
     * A prueba de fallos: runCatching atrapa incluso OutOfMemoryError (una foto muy grande no
     * tumba el protector; simplemente se omite devolviendo null).
     */
    fun loadScaled(file: File, reqW: Int, reqH: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / sample > reqW || bounds.outHeight / sample > reqH) sample *= 2

        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // la mitad de memoria (fotos sin transparencia)
        }) ?: return@runCatching null

        val rotation = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (rotation == 0f) bmp
        else try {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        } catch (e: Throwable) {
            bmp // si la rotación se queda sin memoria, mostrar la foto sin rotar
        }
    }.getOrNull()
}
