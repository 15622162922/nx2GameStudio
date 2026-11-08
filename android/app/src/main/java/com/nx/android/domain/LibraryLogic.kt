package com.nx.android.domain

import java.io.File

/**
 * The library card's status, as pure rules (free of Android, so the tests run on the JVM): the port that runs
 * now paints its game yellow, a remembered APK makes the game green, everything else is grey — and an APK that
 * was deleted from the output folder falls back to grey at the next look.
 */
object LibraryLogic {

    fun statusOf(file: File, apks: Map<String, File>, portRunning: Boolean, portGameFile: String): GameStatus = when {
        portRunning && portGameFile == file.absolutePath -> GameStatus.PORTING
        apks[file.absolutePath]?.isFile == true -> GameStatus.DONE
        else -> GameStatus.NOT_PORTED
    }
}
