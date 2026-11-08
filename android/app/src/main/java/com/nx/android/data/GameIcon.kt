package com.nx.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * The icon of a ported game: the porter packed it into the game's APK (res/drawable-nodpi-v4/icon.png), so the
 * launcher can show the real cover without touching the data pack. Decoded off the main thread by the caller,
 * downsampled to roughly [maxSize] pixels.
 */
object GameIcon {

    fun decode(apkPath: String, maxSize: Int): Bitmap? = try {
        ZipFile(File(apkPath)).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("icon.png") } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
            zip.getInputStream(entry).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (e: Exception) {
        null
    }
}
