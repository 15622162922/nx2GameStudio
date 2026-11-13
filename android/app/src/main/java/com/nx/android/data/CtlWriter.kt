package com.nx.android.data

import java.io.File

/**
 * 运行中游戏的控制文件（DM_CTL）：宿主每 100 ms 读一次这个文件，写进去的行当场生效。
 *
 * 文件名跟着游戏的 title ID（宿主那边 dm_java_start 起的：files/ctl-<title ID>.txt），
 * 所以一个游戏一个文件；没有正在跑的游戏时不用写。
 */
class CtlWriter(private val files: File) {

    /** 写几行 env 进去（"env KEY=VALUE"）：宿主的 ctl 线程 setenv，之后读这个开关的地方立刻看到 */
    fun env(titleId: String, rows: Array<String>) {
        if (titleId.isBlank() || rows.isEmpty()) return
        runCatching {
            val f = File(files, "ctl-$titleId.txt")
            f.appendText(rows.joinToString("\n") + "\n")
        }
    }
}
