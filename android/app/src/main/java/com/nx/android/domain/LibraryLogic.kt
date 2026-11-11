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

    /**
     * 点「运行」时的两种走法：那个游戏还活着就回到它（不重启），没在跑就全新启动。
     *
     * 三个条件缺一不可：状态在不在（App 的进程被回收重启后会丢）、是不是同一个游戏（一次只跑一个，
     * 活着的那个未必是玩家点的这个）、进程是不是真的在（状态比进程慢半拍：游戏刚结束、进程已经没了而
     * 状态还没清的那一两秒里，「回到前台」会回到一个已经不在的页面）。
     */
    fun runAction(running: Boolean, runningTitleId: String, portTitleId: String, processAlive: Boolean): RunAction =
        if (running && runningTitleId == portTitleId && processAlive) RunAction.RESUME else RunAction.START
}

/** 点「运行」的走法：回到前台，还是全新启动 */
enum class RunAction { RESUME, START }
