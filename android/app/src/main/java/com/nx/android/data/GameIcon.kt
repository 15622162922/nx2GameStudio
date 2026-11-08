package com.nx.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The icon of a ported game: the porter put it in the data pack (icon.png, 256 px), and the library shows the
 * real cover from there. Decoded off the main thread by the caller, downsampled to roughly [maxSize] pixels.
 */
object GameIcon {

    fun decode(path: String, maxSize: Int): Bitmap? = try {
        val f = File(path)
        if (!f.isFile) null
        else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) {
        null
    }
}
