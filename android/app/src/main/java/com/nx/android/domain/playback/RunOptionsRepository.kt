package com.nx.android.domain.playback

import java.io.File

data class RunOptionsSnapshot(
    val languages: List<Pair<String, String>> = emptyList(),
    val packDrivers: List<String> = emptyList(),
    val language: String? = null,
    val driver: String? = null,
) {
    fun driverChoices(installed: List<Pair<String, String>>): List<Pair<String, String?>> =
        listOf("" to null) + installed + packDrivers.map { it to it }
}

interface RunOptionsRepository {
    suspend fun read(pack: File): RunOptionsSnapshot
    suspend fun update(pack: File, language: String?, driver: String?): RunOptionsSnapshot
}
