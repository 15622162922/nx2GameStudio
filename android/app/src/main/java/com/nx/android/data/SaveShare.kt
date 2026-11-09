package com.nx.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where the save packages go and how one is sent to another app.
 *
 * The packages live in the download folder: a place the player can open in any file manager, that a chat app
 * can attach a file from, and that survives the app being uninstalled. When that folder cannot be written, the
 * app's own external files folder takes the packages.
 */
object SaveShare {

    /** the folder the packages go to, inside the download folder */
    const val DIR = "nx2android"

    /** where the packages go */
    fun packagesDir(context: Context): File {
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(dl, DIR)
        if (dir.isDirectory || dir.mkdirs()) return dir
        return File(context.getExternalFilesDir(null), DIR)
    }

    /** the URI a share hands out (a file:// URI is refused by Android since 7, so it goes through the provider) */
    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** the share of one package */
    fun intent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** the share of one text file (a game's log): a file as well, so a long log arrives whole */
    fun textIntent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
