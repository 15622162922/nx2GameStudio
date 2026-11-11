package com.nx.android.domain

import java.io.File
import java.io.IOException

/**
 * 把一个数据包搬到另一个卷（可移动卡上 App 自己的目录），或者搬回来。
 *
 * 搬移的规矩（和移植器建包时一样，datapack.build）：
 * - 先写半成品 `<名字>-data.partial`，写完再改名成正名——同卷改名是原子的，别人看不到半成品
 * - 写的时候逐个文件核对长度，累计文件数与字节数，跟源树对上了才算数（清单只数构建产物，
 *   玩过的包里还有日志、管线缓存这些运行时写的东西，所以核对的是源树本身）
 * - 落位成功之后才删源包；任何一步失败，源包原地不动
 * - 目标上已经有同名包时替换它，但把它里面的导出存档备份（saves/）留着
 *
 * 纯文件操作，不依赖 Android，测试跑在 JVM 上。
 */
object PackMover {

    /** 进度多久报一次（每抄满这么多字节报一回）：太少界面刷不动，太多没意义 */
    private const val CHUNK_REPORT = 4L * 1024 * 1024

    /** 搬移的结果：成了、还是没成（没成时带一句原因） */
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

    /** 抄过去的文件数与字节数（两边各一份：副本与源树） */
    private data class Copied(val target: Count, val source: Count)

    /** 文件数与字节数 */
    private data class Count(val files: Int, val bytes: Long)

    /**
     * 搬移的进度：搬了多少、总共多少。
     *
     * 卡上写入很慢（三十多 MB 每秒），玩家要看得见「还在动」——所以每抄完一个文件就报一次。
     */
    data class Progress(val files: Int, val totalFiles: Int, val bytes: Long, val totalBytes: Long) {
        /** 搬完了多少（0..1） */
        val fraction: Float
            get() = if (totalBytes <= 0L) 0f else (bytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 把 [pack] 搬到 [targetDir]（那个目录会新建）。
     *
     * 源包不在、目标卷不在、空间不够、复制中断，都不成：源包留着不动。
     *
     * @param onProgress 每抄完一个文件报一次进度（默认不报）
     */
    fun move(pack: File, targetDir: File, onProgress: (Progress) -> Unit = {}): Result {
        if (!pack.isDirectory) return Result.fail("the pack is not there")
        val target = File(targetDir, pack.name)
        if (target.absolutePath == pack.absolutePath) return Result.ok()
        val tmp = File(targetDir, pack.name + ".partial")
        val keep = File(targetDir, pack.name + ".saves-keep")   // 旧包里的导出存档备份，替换时先挪出来
        try {
            targetDir.mkdirs()
            if (!targetDir.isDirectory) return Result.fail("the target folder cannot be created")
            // 旧包里的导出存档备份先挪出来（它不是构建的产物，是玩家的）
            if (target.isDirectory) {
                val exported = File(target, "saves")
                if (exported.isDirectory) {
                    keep.deleteRecursively()
                    if (!exported.renameTo(keep)) return Result.fail("the old pack's saves cannot be put aside")
                }
            }
            tmp.deleteRecursively()
            // 预扫：源树有多大（纯 stat，不读内容）——进度条要有分母
            val total = measure(pack)
            onProgress(Progress(0, total.files, 0L, total.bytes))
            val copied = copyTree(pack, tmp, total, onProgress)
            if (copied == null) {
                tmp.deleteRecursively()          // 搬了一半：半成品不留
                return Result.fail("a file cannot be copied")
            }
            // 核对：搬过去的树和源树一样（文件数与字节数）
            if (copied.target.files != copied.source.files || copied.target.bytes != copied.source.bytes) {
                tmp.deleteRecursively()
                return Result.fail("the copy does not match the pack")
            }
            // 落位：旧的删掉，半成品改名成正名
            target.deleteRecursively()
            if (!tmp.renameTo(target)) {
                tmp.deleteRecursively()
                return Result.fail("the pack cannot be put in place")
            }
            if (keep.isDirectory) {
                val exported = File(target, "saves")
                exported.parentFile?.mkdirs()
                exported.deleteRecursively()
                keep.renameTo(exported)
                keep.deleteRecursively()
            }
            pack.deleteRecursively()
            return Result(true, null, copied.target.files, copied.target.bytes)
        } catch (e: Exception) {
            tmp.deleteRecursively()
            keep.deleteRecursively()
            return Result.fail(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 一棵树有多大（纯 stat：只数文件与字节，不读内容） */
    private fun measure(src: File): Count {
        var files = 0
        var bytes = 0L
        val stack = java.util.ArrayDeque<File>()
        stack.addLast(src)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            for (child in dir.listFiles() ?: emptyArray()) {
                if (child.isDirectory) stack.addLast(child)
                else {
                    files++
                    bytes += child.length()
                }
            }
        }
        return Count(files, bytes)
    }

    /**
     * 把一棵树抄过去：逐个文件核对长度。
     * 返回 null 是成功；一句话是失败的原因。
     *
     * 数的是两边：源树与副本。清单（nx2a-data.json）算在里面——它是包里的一份文件，
     * 而玩过的包里还有宿主写的日志、Vulkan 管线缓存、玩家改过的设置（清单里没有它们）。
     *
     * 进度按**块**报，不只按文件：一个包可能就是一个 6 GB 的单文件（data.cpk），
     * 按文件报的话进度条会一直卡在 0%，看着像死了。
     */
    private fun copyTree(src: File, dst: File, total: Count, onProgress: (Progress) -> Unit): Copied? {
        dst.mkdirs()
        if (!dst.isDirectory) return null
        var files = 0
        var bytes = 0L
        val stack = java.util.ArrayDeque<File>()
        stack.addLast(src)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                val out = File(dst, child.relativeTo(src).path)
                if (child.isDirectory) {
                    out.mkdirs()
                    stack.addLast(child)
                } else {
                    out.parentFile?.mkdirs()
                    // 这个文件已经抄了多少（每 4 MB 报一次，太多的话界面刷不动）
                    var inFile = 0L
                    val ok = copyFile(child, out) { copied ->
                        inFile = copied
                        onProgress(Progress(files, total.files, bytes + inFile, total.bytes))
                    }
                    if (!ok) return null
                    files++
                    bytes += out.length()
                    onProgress(Progress(files, total.files, bytes, total.bytes))
                }
            }
        }
        // 源树是预扫时数的：副本要与它一样
        return Copied(Count(files, bytes), total)
    }

    /**
     * 抄一个文件，抄完核对长度。
     *
     * @param onChunk 每抄满一段就报这个文件已经抄了多少字节（用来显示进度）
     */
    private fun copyFile(src: File, dst: File, onChunk: (Long) -> Unit = {}): Boolean {
        try {
            var copied = 0L
            var reported = 0L
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        if (copied - reported >= CHUNK_REPORT) {
                            reported = copied
                            onChunk(copied)
                        }
                    }
                }
            }
            onChunk(copied)
            return src.length() == dst.length()
        } catch (e: IOException) {
            return false
        }
    }
}
