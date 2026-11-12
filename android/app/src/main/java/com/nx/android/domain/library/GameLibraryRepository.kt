package com.nx.android.domain.library

import com.nx.android.domain.GameEntry
import com.nx.android.domain.Ported
import com.nx.android.domain.StorageUsage
import java.io.File

interface GameLibraryRepository {
    fun scan(): List<GameEntry>
    fun portsDirs(): List<File>
    fun internalPortsDir(): File
    fun workRoot(): File
    fun existingWorkspace(titleId: String): File?
    fun partialPacks(): List<File>
    fun usageItems(games: List<GameEntry>): List<StorageUsage.Item>
    fun workFolderIn(from: Long, to: Long): String?
    fun portFromPack(pack: File): Ported?
    fun packDirOf(entry: GameEntry): File?
    fun packDirOf(source: File, games: List<GameEntry>): File?
    fun packsOf(entry: GameEntry): List<File>
    fun unreachablePacks(entry: GameEntry): List<File>
    fun rememberPackPath(titleId: String, path: String)
    fun rememberSource(titleId: String, source: String)
    fun noteDone(source: String, pack: String): String?
    fun forget(titleId: String)
}
