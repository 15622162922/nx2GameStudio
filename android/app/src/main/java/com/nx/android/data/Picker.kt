package com.nx.android.data

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * The system's own folder picker: the app asks for a tree and Android's file manager shows it (the phone's
 * storages, the SD card among them). What comes back is a document URI, and the tool needs a path it can open, so
 * the URI is turned into one: `primary:Game` is /storage/emulated/0/Game, and a card with the id `XXXX-XXXX` is
 * /storage/XXXX-XXXX.
 */
class Picker(private val activity: Activity) {

    companion object {
        const val REQUEST_TREE = 1001
        const val REQUEST_FILE = 1002
    }

    /** Asks for a folder (the game folder, the output folder) */
    fun tree(start: String, onPick: (String) -> Unit) {
        pending = onPick
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (start.isNotEmpty()) {
                val uri = pathToUri(start)
                if (uri != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }
        }
        try {
            activity.startActivityForResult(intent, REQUEST_TREE)
        } catch (e: Exception) {
            throw e   // no app handles it: the caller falls back to the app's own list
        }
    }

    /** Asks for a file (the prod.keys): it is copied into the app's own folder, so that the tool has a path */
    fun file(onPick: (String) -> Unit) {
        pending = onPick
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, REQUEST_FILE)   // the caller catches the missing handler
    }

    private var pending: ((String) -> Unit)? = null

    /** The answer of the picker */
    fun onResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data?.data == null) return false
        val uri = data.data!!
        if (requestCode == REQUEST_TREE) {
            val path = toPath(uri) ?: return false
            pending?.invoke(path)
            return true
        }
        if (requestCode == REQUEST_FILE) {
            // a document URI cannot be opened as a path: the file is copied into the app's own folder
            val name = DocumentsContract.getDocumentId(uri).substringAfterLast(':')
            val target = File(activity.filesDir, "nx2a/$name")
            target.parentFile?.mkdirs()
            try {
                activity.contentResolver.openInputStream(uri)?.use { src -> target.outputStream().use { src.copyTo(it) } }
                pending?.invoke(target.absolutePath)
                return true
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    /** A document URI as a path: `primary:Game` is /storage/emulated/0/Game, a card is /storage/<its id> */
    fun toPath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            return null
        }
        val volume = docId.substringBefore(':')
        val sub = docId.substringAfter(':', "")
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return if (sub.isEmpty()) root else "$root/$sub"
    }

    /** A path as a tree URI, so that the picker opens where the user was */
    private fun pathToUri(path: String): Uri? {
        val root = if (path.startsWith("/storage/emulated/0")) "primary" else path.removePrefix("/storage/").substringBefore('/')
        val sub = if (path.startsWith("/storage/emulated/0")) path.removePrefix("/storage/emulated/0").trimStart('/') else path.removePrefix("/storage/$root/").trimStart('/')
        return try {
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "$root:$sub")
        } catch (e: Exception) {
            null
        }
    }
}
