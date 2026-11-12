package com.nx.android.domain

import org.json.JSONObject
import com.nx.android.data.library.PackMoverFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 把数据包搬到另一个卷：先写半成品、核对了才落位，落位成功后才删源包 */
class PackMoverTest {

    private fun tmpDir(name: String): File =
        File.createTempFile("nx2a-move", "").let { f -> f.delete(); f.mkdirs(); f }

    /** 一个假的数据包：清单 + 几个文件 */
    private fun fakePack(dir: File, name: String = "Game-data"): File {
        val pack = File(dir, name).apply { mkdirs() }
        File(pack, "RomFS").mkdirs()
        File(pack, "RomFS/a.bin").writeBytes(ByteArray(1024))
        File(pack, "RomFS/b.bin").writeBytes(ByteArray(2048))
        File(pack, "host").mkdirs()
        File(pack, "host/libdmplay.so").writeBytes(ByteArray(512))
        val man = JSONObject()
            .put("title_id", "0100000000010000")
            .put("files", 3)
            .put("bytes", 1024L + 2048 + 512)
        File(pack, "nx2a-data.json").writeText(man.toString())
        return pack
    }

    @Test
    fun `the pack lands in the target folder and the source is gone`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")
        val pack = fakePack(src)
        val manifestBytes = File(pack, "nx2a-data.json").length()

        val r = PackMoverFiles.move(pack, File(dst, "ports"))

        assertTrue(r.failure ?: "", r.ok)
        val moved = File(dst, "ports/Game-data")
        assertTrue(moved.isDirectory)
        assertTrue(File(moved, "RomFS/a.bin").isFile)
        assertTrue(File(moved, "nx2a-data.json").isFile)
        assertFalse(pack.exists())          // 落位之后才删源包
        assertEquals(4, r.files)            // 三个内容文件 + 清单
        assertEquals(1024L + 2048 + 512 + manifestBytes, r.bytes)
    }

    /** 玩过的包：宿主写的日志、Vulkan 管线缓存、玩家改过的设置都在包里（清单里没有它们）——照样搬得走 */
    @Test
    fun `a pack that has been played moves as it is`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")
        val pack = fakePack(src)
        File(pack, "log.txt").writeText("the host's runtime log")
        File(pack, "vk_pipeline_cache.bin").writeBytes(ByteArray(4096))
        File(pack, "settings.txt").writeText("DM_X=1")
        File(pack, "saves").mkdirs()
        File(pack, "saves/backup.zip").writeText("the player's backup")

        val r = PackMoverFiles.move(pack, File(dst, "ports"))

        assertTrue(r.failure ?: "", r.ok)
        val moved = File(dst, "ports/Game-data")
        assertTrue(File(moved, "log.txt").isFile)
        assertTrue(File(moved, "vk_pipeline_cache.bin").isFile)
        assertTrue(File(moved, "saves/backup.zip").isFile)
    }

    @Test
    fun `a half-made pack is never left behind`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")
        val pack = fakePack(src)

        PackMoverFiles.move(pack, File(dst, "ports"))

        assertFalse(File(dst, "ports/Game-data.partial").exists())
        assertFalse(File(dst, "ports/Game-data.saves-keep").exists())
    }

    @Test
    fun `the old pack on the card is replaced and its exported saves are kept`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")
        val pack = fakePack(src)
        // 卡上已经有一份旧包，里面带着玩家导出过的存档备份
        val target = File(dst, "ports/Game-data").apply { mkdirs() }
        File(target, "saves").mkdirs()
        File(target, "saves/backup.zip").writeText("the player's backup")

        val r = PackMoverFiles.move(pack, File(dst, "ports"))

        assertTrue(r.failure ?: "", r.ok)
        assertTrue(File(target, "saves/backup.zip").isFile)   // 导出存档留着
        assertFalse(File(dst, "ports/Game-data.saves-keep").exists())
    }

    @Test
    fun `a missing pack leaves everything alone`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")

        val r = PackMoverFiles.move(File(src, "nothing-data"), File(dst, "ports"))

        assertFalse(r.ok)
        assertTrue(r.failure != null)
        assertFalse(File(dst, "ports").exists())
    }

    @Test
    fun `a pack that cannot be copied leaves the source alone`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")
        val pack = fakePack(src)
        // 一个读不了的文件：搬不成，源包原地不动
        val locked = File(pack, "RomFS/locked.bin")
        locked.writeBytes(ByteArray(16))
        locked.setReadable(false, false)

        val r = PackMoverFiles.move(pack, File(dst, "ports"))

        assertFalse(r.ok)
        assertFalse(File(dst, "ports/Game-data").exists())     // 没有半成品冒充正名
        assertFalse(File(dst, "ports/Game-data.partial").exists())
        assertTrue(pack.exists())                              // 源包原地不动
    }

    @Test
    fun `moving a pack onto itself does nothing`() {
        val src = tmpDir("src")
        val pack = fakePack(src)

        val r = PackMoverFiles.move(pack, src)

        assertTrue(r.ok)
        assertTrue(pack.exists())
    }

    /** 进度：每抄完一个文件报一次，最后一条是搬完了（卡上写入很慢，玩家要看得见还在动） */
    @Test
    fun `the move reports its progress as it goes`() {
        val src = tmpDir("src"); val dst = tmpDir("dst")
        val pack = fakePack(src)
        val total = 1024L + 2048 + 512 + File(pack, "nx2a-data.json").length()

        val seen = ArrayList<PackMover.Progress>()
        val r = PackMoverFiles.move(pack, File(dst, "ports")) { seen.add(it) }

        assertTrue(r.failure ?: "", r.ok)
        assertTrue("the progress should be reported per file", seen.size >= 4)
        val first = seen.first()
        assertEquals(0, first.bytes)               // 还没开始抄
        assertEquals(total, first.totalBytes)      // 分母是源树的大小
        val last = seen.last()
        assertEquals(total, last.bytes)             // 最后一条：搬完了
        assertEquals(last.totalFiles, last.files)
        assertEquals(1f, last.fraction, 0.001f)
        // 进度只增不减
        var lastBytes = -1L
        seen.forEach { assertTrue(it.bytes >= lastBytes); lastBytes = it.bytes }
    }
}
