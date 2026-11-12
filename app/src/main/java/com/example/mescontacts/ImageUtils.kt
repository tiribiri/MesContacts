package com.example.mescontacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ImageUtils {

    /** Dossier des photos de contacts (déclaré dans res/xml/file_paths.xml). */
    fun photosDir(context: Context): File =
        File(context.filesDir, "photos").also { it.mkdirs() }

    /**
     * Décode un fichier image en le réduisant (inSampleSize) pour limiter la mémoire.
     * Retourne null si le fichier est absent ou illisible.
     */
    fun decodeScaled(path: String, maxSide: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copie l'image [source] (galerie ou photo de la caméra) vers [dest] :
     * réduite à [maxSide] pixels, orientation EXIF appliquée, enregistrée en JPEG.
     * Retourne false si l'image est illisible.
     */
    fun importPhoto(context: Context, source: Uri, dest: File, maxSide: Int = 1024): Boolean {
        return try {
            val resolver = context.contentResolver

            // 1) Dimensions de l'image.
            // Avec inJustDecodeBounds, decodeStream renvoie toujours null : on ne teste pas son retour.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(source) ?: return false
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

            // 2) Décodage réduit
            val bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false

            // 3) Orientation EXIF
            val orientation = try {
                resolver.openInputStream(source)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
            }
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // 4) Enregistrement en JPEG dans le stockage interne
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    fun deleteQuietly(path: String?) {
        if (path != null) File(path).delete()
    }
}