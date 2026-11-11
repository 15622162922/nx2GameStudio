package com.nx.android.ui.components

/**
 * 一个人看得懂的大小：字节数 → 「3.2 GB」这样的写法。
 *
 * 库页与设置页都要说「多大」，放在这里只有一份说法。
 */
fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
