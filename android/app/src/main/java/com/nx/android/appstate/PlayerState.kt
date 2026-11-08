package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The game that plays now (one at a time): the library watches this to grey out the other games' run buttons,
 * to offer "back to the game", and to say that a game has ended (the process went away — it quit itself, or
 * it crashed; the app cannot tell, and it does not matter). [alive] is the watch: the ":play" process's
 * presence, polled on a thread of its own.
 */
class PlayerState(private val alive: () -> Boolean) {

    var running by mutableStateOf(false); private set

    /** the game that went away without the app's exit (the process died): the library says so once */
    var exited by mutableStateOf(false); private set

    var titleId by mutableStateOf(""); private set
    var name by mutableStateOf(""); private set

    fun start(id: String, gameName: String) {
        titleId = id
        name = gameName
        exited = false
        if (!running) {
            running = true
            watch()
        }
    }

    /** the app's own exit action (the activity ends the process): no "has exited" note, this was on purpose */
    fun stop() {
        running = false
        titleId = ""
        name = ""
        exited = false
    }

    private fun watch() {
        Thread {
            while (running) {
                Thread.sleep(2000)
                if (running && !alive()) {
                    exited = running
                    running = false
                    titleId = ""
                    name = ""
                }
            }
        }.start()
    }
}
