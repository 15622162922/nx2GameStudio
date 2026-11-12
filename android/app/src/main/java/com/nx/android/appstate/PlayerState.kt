package com.nx.android.appstate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import com.nx.android.data.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The game that plays now (one at a time): the library watches this to grey out the other games' run buttons,
 * and to offer "back to the game". [alive] is the watch: the ":play" process's presence, polled on a thread
 * of its own. The process going away (the game quit itself, or it crashed; the app cannot tell, and it does
 * not matter) only clears the state — the library says nothing about it.
 */
@Singleton
class PlayerState private constructor(private val alive: () -> Boolean, private val pollMs: Long, marker: Unit) {

    @Inject
    constructor(@ApplicationContext context: Context) : this({ Player.playProcessAlive(context) }, 2000, Unit)

    constructor(alive: () -> Boolean, pollMs: Long = 2000) : this(alive, pollMs, Unit)

    var running by mutableStateOf(false); private set

    var titleId by mutableStateOf(""); private set
    var name by mutableStateOf(""); private set

    fun start(id: String, gameName: String) {
        titleId = id
        name = gameName
        if (!running) {
            running = true
            watch()
        }
    }

    /** the app's own exit action (the activity ends the process): the game is gone, nothing to say */
    fun stop() {
        running = false
        titleId = ""
        name = ""
    }

    private fun watch() {
        Thread {
            while (running) {
                Thread.sleep(pollMs)
                if (running && !alive()) {
                    running = false
                    titleId = ""
                    name = ""
                }
            }
        }.start()
    }
}
