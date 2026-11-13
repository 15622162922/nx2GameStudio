package com.nx.android.domain.drivers

import java.io.File

/** An installed Vulkan driver stored in the app's private files directory. */
data class Driver(
    val id: String,
    val name: String,
    val version: String,
    val vendor: String,
    val library: File,
    val shipped: Boolean = false,
) {
    val label: String get() = if (version.isBlank()) name else "$name · $version"
}

enum class DriverNote { GPU_8XX, GPU_7XX, GPU_7XX_ONE_UI, GPU_6XX_7XX_8XX, GPU_7XX_8XX_SWITCH }

data class DriverRelease(
    val file: String,
    val title: String,
    val metaName: String,
    val note: DriverNote,
    val url: String,
)
