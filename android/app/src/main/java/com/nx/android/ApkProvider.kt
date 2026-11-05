package com.nx.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Serves the APK the tool made to the system installer: Android 7 and later do not allow an app to hand out a
 * file:// URI, so the installer reads it through this provider. The provider is exported (the installer is another
 * uid and reads the APK with the URI permission the intent grants it), but it hands out one file only: the APK the
 * app made. Nothing else of the app's folder is reachable through it.
 */
class ApkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        // The whole path is the file's: the URI's path is the absolute path, and its last segment alone would be
        // the file's name without its folders (the installer would then read nothing)
        val path = uri.path ?: return null
        if (path != apk) return null   // only the APK the app made
        val file = File(path)
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    companion object {
        /** The APK the app made: the one file the provider hands out */
        @Volatile var apk: String = ""

        /** The URI of an APK file, for the installer */
        fun uriFor(context: android.content.Context, file: File): Uri {
            apk = file.absolutePath
            return Uri.parse("content://com.nx.android.apk/" + file.absolutePath)
        }
    }
}
