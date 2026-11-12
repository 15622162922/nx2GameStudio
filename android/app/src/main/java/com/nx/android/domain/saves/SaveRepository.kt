package com.nx.android.domain.saves

import java.io.File

interface SaveRepository {
    fun titleIdInPackage(name: String): String?
    fun packagesDirectory(): File
    suspend fun listPackages(): List<File>
    suspend fun listBackups(packSaves: File): List<File>
    suspend fun treeSize(file: File): Long
    suspend fun export(titleId: String, gameName: String, packSaves: File): Result<File>
    suspend fun import(titleId: String, packageFile: File, packSaves: File): Result<String?>
    suspend fun restore(titleId: String, backup: File, packSaves: File): Result<String?>
    suspend fun prepareBackupShare(titleId: String, gameName: String, backup: File, packSaves: File): Result<File>
    suspend fun delete(file: File, packSaves: File): Boolean
}

const val SAVE_PACKAGE_EXTENSION = "zip"

class SaveOperationException(val reason: Reason, detail: String? = null) :
    java.io.IOException(detail ?: reason.name) {
    enum class Reason { GAME_RUNNING, INVALID_TITLE_ID, TITLE_MISMATCH, INVALID_BACKUP, NO_SAVES }
}
