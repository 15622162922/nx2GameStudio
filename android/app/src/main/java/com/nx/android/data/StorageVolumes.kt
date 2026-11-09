package com.nx.android.data

import android.content.Context
import android.os.Environment
import com.nx.android.R
import java.io.File

/**
 * 可浏览的存储卷：内部存储在前，SD 卡卷按 `/storage` 下的卷 ID 在后。
 *
 * 枚举 SD 卡卷的通用手法：`getExternalFilesDirs(null)` 的第一个元素是内部卷
 * （`/storage/emulated/0/Android/data/<pkg>/files`），其余元素裁掉 `/Android/data/<pkg>/files`
 * 前缀就是各卷的根。全文件访问在手，java.io 直读畅通。
 */
object StorageVolumes {

    /** 卷名 → 卷根目录（内部存储在前） */
    fun list(ctx: Context, locale: AppLocale): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()
        val internal = Environment.getExternalStorageDirectory()
        if (internal.isDirectory) out += locale.str(R.string.storage_internal) to internal
        for (dir in ctx.getExternalFilesDirs(null)) {
            val path = dir?.path ?: continue
            if (!path.startsWith("/storage/")) continue
            val root = path.substringBefore("/Android/data")
            if (root == internal.path || out.any { it.second.path == root }) continue
            if (File(root).isDirectory) out += locale.str(R.string.storage_sd_card, root.substringAfterLast('/')) to File(root)
        }
        return out
    }
}
