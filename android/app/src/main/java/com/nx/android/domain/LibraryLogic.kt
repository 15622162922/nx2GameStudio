package com.nx.android.domain

import java.io.File

/**
 * The library card's status, as pure rules (free of Android, so the tests run on the JVM): the port that runs
 * now paints its game yellow, a finished port (pack and host together) makes the game green, everything else
 * is grey — and a pack whose host went away falls back to grey at the next look.
 */
object LibraryLogic {

    fun statusOf(file: File, ports: Map<String, Ported>, portRunning: Boolean, portGameFile: String): GameStatus = when {
        portRunning && portGameFile == file.absolutePath -> GameStatus.PORTING
        ports[file.absolutePath]?.host?.isFile == true -> GameStatus.DONE
        else -> GameStatus.NOT_PORTED
    }
}
