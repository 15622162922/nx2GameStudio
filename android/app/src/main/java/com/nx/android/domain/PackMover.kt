package com.nx.android.domain

import java.io.File

/** Value types returned while a data pack is moved by the data layer. */
object PackMover {
    data class Result(
        val ok: Boolean,
        val failure: String? = null,
        val files: Int = 0,
        val bytes: Long = 0L,
    ) {
        companion object {
            fun ok() = Result(true)
            fun fail(why: String) = Result(false, why)
        }
    }

    data class Progress(val files: Int, val totalFiles: Int, val bytes: Long, val totalBytes: Long) {
        val fraction: Float
            get() = if (totalBytes <= 0L) 0f else (bytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
    }
}
