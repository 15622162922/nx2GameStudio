package com.nx.android.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** 占用统计的引擎：量一棵目录树、认内容变没变、决定要不要量、把结果存成 JSON */
class StorageUsageTest {

    private fun tmpDir(): File = File.createTempFile("nx2a-usage", "").let { f -> f.delete(); f.mkdirs(); f }

    /** 一棵假的目录树：几个文件、几个子目录 */
    private class Tree {
        val root = File.createTempFile("nx2a-usage", "").let { f -> f.delete(); f.mkdirs(); f }
        fun file(name: String, bytes: Int): File = File(root, name).apply { writeBytes(ByteArray(bytes)) }
        fun dir(name: String): File = File(root, name).apply { mkdirs() }
        fun fileIn(dir: File, name: String, bytes: Int): File = File(dir, name).apply { writeBytes(ByteArray(bytes)) }
    }

    // ---- 量一棵目录树 --------------------------------------------------------------------------------------

    @Test
    fun `the bytes and the file count add up`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        t.file("b.bin", 250)
        val sub = t.dir("sub")
        t.fileIn(sub, "c.bin", 50)

        val acc = StorageUsage.Acc()
        val state = StorageUsage.measure(t.root, acc)

        assertEquals(StorageUsage.State.DONE, state)
        assertEquals(400L, acc.bytes)
        assertEquals(3, acc.files)
        t.root.deleteRecursively()
    }

    @Test
    fun `a directory that is not there is empty, not a failure`() = runBlocking<Unit> {
        val acc = StorageUsage.Acc()
        val state = StorageUsage.measure(File(tmpDir(), "gone"), acc)
        assertEquals(StorageUsage.State.DONE, state)
        assertEquals(0L, acc.bytes)
    }

    @Test
    fun `a symbolic link counts as itself, not as what it points at`() = runBlocking<Unit> {
        val t = Tree()
        val target = t.file("target.bin", 4096)
        val link = File(t.root, "link.bin")
        val made = try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        } catch (e: Exception) {
            false   // the phone does not allow it: the test skips itself
        }
        if (!made) {
            t.root.deleteRecursively()
            return@runBlocking
        }

        val acc = StorageUsage.Acc()
        StorageUsage.measure(t.root, acc)

        // the link itself (its own length) plus the target's bytes: the link does not bring the target in twice
        assertEquals(2, acc.files)
        assertTrue(acc.bytes < 4096L * 2)
        t.root.deleteRecursively()
    }

    @Test
    fun `the same content behind hard links is counted once per copy`() = runBlocking<Unit> {
        val t = Tree()
        val a = t.file("a.bin", 1000)
        val b = File(t.root, "b.bin")
        val made = try {
            java.nio.file.Files.createLink(b.toPath(), a.toPath())
            true
        } catch (e: Exception) {
            false
        }
        if (!made) {
            t.root.deleteRecursively()
            return@runBlocking
        }

        val acc = StorageUsage.Acc()
        StorageUsage.measure(t.root, acc)

        assertEquals(2, acc.files)
        assertEquals(2000L, acc.bytes)
        t.root.deleteRecursively()
    }

    @Test
    fun `the file budget stops the walk and leaves a lower bound`() = runBlocking<Unit> {
        val t = Tree()
        repeat(10) { t.file("f$it.bin", 100) }

        val acc = StorageUsage.Acc()
        val state = StorageUsage.measure(t.root, acc, maxFiles = 3)

        assertEquals(StorageUsage.State.PARTIAL, state)
        assertEquals(3, acc.files)
        assertTrue(acc.bytes < 1000L)   // a lower bound, not the whole thing
        t.root.deleteRecursively()
    }

    @Test
    fun `the time budget stops the walk and leaves a lower bound`() = runBlocking<Unit> {
        val t = Tree()
        repeat(10) { t.file("f$it.bin", 100) }

        val acc = StorageUsage.Acc()
        val state = StorageUsage.measure(t.root, acc, maxMillis = 0)

        assertEquals(StorageUsage.State.PARTIAL, state)
        assertTrue(acc.bytes <= 1000L)
        t.root.deleteRecursively()
    }

    @Test
    fun `a root that cannot be read is a failure`() = runBlocking<Unit> {
        val t = Tree()
        val notADir = t.file("plain.bin", 10)

        val acc = StorageUsage.Acc()
        val state = StorageUsage.measure(notADir, acc)

        assertEquals(StorageUsage.State.FAILED, state)
        t.root.deleteRecursively()
    }

    @Test
    fun `cancelling the walk stops it`() = runBlocking<Unit> {
        val t = Tree()
        repeat(5000) { t.file("f$it.bin", 100) }

        val acc = StorageUsage.Acc()
        val job: Job = launch(Dispatchers.Default) { StorageUsage.measure(t.root, acc) }
        while (acc.files == 0) delay(1)   // the walk is under way
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)        // the walk stopped at its next cancellation point
        assertTrue(acc.files <= 5000)
        t.root.deleteRecursively()
    }

    // ---- 内容变没变 ------------------------------------------------------------------------------------------

    @Test
    fun `the fingerprint follows the top of the tree`() {
        val t = Tree()
        val before = StorageUsage.fingerprintOf(t.root)
        assertEquals("", before)

        t.dir("Game-data")
        val withPack = StorageUsage.fingerprintOf(t.root)
        assertTrue(withPack.isNotEmpty())

        // a new pack under the ports folder: the fingerprint changes, so the numbers are stale
        t.dir("Other-data")
        assertTrue(StorageUsage.fingerprintOf(t.root) != withPack)

        // the same content: the fingerprint stays
        assertEquals(StorageUsage.fingerprintOf(t.root), StorageUsage.fingerprintOf(t.root))
        t.root.deleteRecursively()
    }

    @Test
    fun `the fingerprint does not move when the files inside change`() {
        val t = Tree()
        val sub = t.dir("Game-data")
        val before = StorageUsage.fingerprintOf(t.root)

        // the pack grew (a rebuild wrote into it): the top did not change
        t.fileIn(sub, "data.cpk", 1000)
        assertEquals(before, StorageUsage.fingerprintOf(t.root))
        t.root.deleteRecursively()
    }

    // ---- 当天复用 -------------------------------------------------------------------------------------------

    /** 一个已经量过的清单：今天量的，指纹对得上 */
    private suspend fun measured(dir: File, bytes: Long): StorageUsage.Snapshot {
        val item = StorageUsage.Item(
            id = "0100000000010000",
            name = "Game",
            parts = listOf(
                StorageUsage.Part(
                    role = StorageUsage.Role.PACK,
                    path = dir.absolutePath,
                    bytes = bytes,
                    files = 1,
                    state = StorageUsage.State.DONE,
                )
            ),
        )
        val fp = StorageUsage.itemFingerprint(item) { StorageUsage.fingerprintOf(it) }
        return StorageUsage.Snapshot(
            items = listOf(item.copy(fingerprint = fp)),
            scannedAt = System.currentTimeMillis(),
        )
    }

    @Test
    fun `the same day and the same content - the tree is not walked again`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)
        val calls = AtomicInteger()

        val result = StorageUsage.scanAndSave(
            snapshot = snapshot,
            target = File(t.root, "usage.json"),
            measureItem = { _, acc ->
                calls.incrementAndGet()
                acc.bytes = 1
                StorageUsage.State.DONE
            },
        )

        assertEquals(0, calls.get())          // nothing was measured
        assertEquals(snapshot.scannedAt, result.scannedAt)   // and the time of the old measurement stayed
        assertEquals(100L, result.items[0].bytes)
        t.root.deleteRecursively()
    }

    @Test
    fun `another day - the tree is walked again`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val yesterday = measured(t.root, 100L).copy(scannedAt = System.currentTimeMillis() - 24 * 3600 * 1000)
        val calls = AtomicInteger()

        StorageUsage.scanAndSave(
            snapshot = yesterday,
            target = File(t.root, "usage.json"),
            measureItem = { _, acc ->
                calls.incrementAndGet()
                acc.bytes = 100
                StorageUsage.State.DONE
            },
        )

        assertEquals(1, calls.get())
        t.root.deleteRecursively()
    }

    @Test
    fun `new content under the folder - the tree is walked again`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)
        t.dir("NewPack-data")   // a new data pack: the old numbers do not cover it
        val calls = AtomicInteger()

        StorageUsage.scanAndSave(
            snapshot = snapshot,
            target = File(t.root, "usage.json"),
            measureItem = { _, acc ->
                calls.incrementAndGet()
                acc.bytes = 200
                StorageUsage.State.DONE
            },
        )

        assertEquals(1, calls.get())
        t.root.deleteRecursively()
    }

    @Test
    fun `the player asks for it - the tree is walked again`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)
        val calls = AtomicInteger()

        StorageUsage.scanAndSave(
            snapshot = snapshot,
            target = File(t.root, "usage.json"),
            force = true,
            measureItem = { _, acc ->
                calls.incrementAndGet()
                acc.bytes = 200
                StorageUsage.State.DONE
            },
        )

        assertEquals(1, calls.get())
        t.root.deleteRecursively()
    }

    @Test
    fun `an interrupted measurement is not written down`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)
        val target = File(t.root, "usage.json")

        var failed = false
        try {
            StorageUsage.scanAndSave(
                snapshot = snapshot,
                target = target,
                force = true,
                measureItem = { _, _ -> throw CancellationException("the panel closed") },
            )
        } catch (e: CancellationException) {
            failed = true
        }

        assertTrue(failed)
        assertFalse(target.exists())   // half a measurement is not "measured today"
        t.root.deleteRecursively()
    }

    @Test
    fun `a cancelled measurement keeps what it had measured as a lower bound`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)

        var kept: StorageUsage.Item? = null
        try {
            StorageUsage.scanAndSave(
                snapshot = snapshot,
                target = File(t.root, "usage.json"),
                force = true,
                measureItem = { _, acc ->
                    acc.bytes = 42
                    acc.files = 1
                    throw CancellationException("the panel closed")
                },
                publish = { s -> kept = s.items[0] },
            )
        } catch (e: CancellationException) {
        }

        assertNotNull(kept)
        assertEquals(StorageUsage.State.PARTIAL, kept!!.parts[0].state)
        assertEquals(42L, kept!!.parts[0].bytes)
        t.root.deleteRecursively()
    }

    @Test
    fun `a part that is out of reach is not measured and not counted as zero`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val item = StorageUsage.Item(
            id = "0100000000010000",
            name = "Game",
            parts = listOf(
                StorageUsage.Part(StorageUsage.Role.PACK, "/storage/FB9A-DC19/Android/data/x/files/ports/Game-data",
                    state = StorageUsage.State.UNKNOWN),
            ),
        )
        val calls = AtomicInteger()

        val result = StorageUsage.scanAndSave(
            snapshot = StorageUsage.Snapshot(listOf(item)),
            target = File(t.root, "usage.json"),
            measureItem = { _, _ ->
                calls.incrementAndGet()
                StorageUsage.State.DONE
            },
        )

        assertEquals(0, calls.get())
        assertEquals(0L, result.items[0].bytes)      // unknown, not zero: the state says so
        assertEquals(StorageUsage.State.UNKNOWN, result.items[0].parts[0].state)
        assertTrue(result.items[0].partial)
        t.root.deleteRecursively()
    }

    // ---- 逐段发布：面板上的合计与各行必须永远对得上 ------------------------------------------------------

    @Test
    fun `the measurement is published step by step and every version is a new value`() = runBlocking<Unit> {
        val t = Tree()
        val work = t.dir("work")
        t.fileIn(work, "a.bin", 100)
        val pack = t.dir("pack")
        t.fileIn(pack, "b.bin", 200)
        val item = StorageUsage.Item(
            id = "0100000000010000",
            name = "Game",
            parts = listOf(
                StorageUsage.Part(StorageUsage.Role.WORKSPACE, work.absolutePath),
                StorageUsage.Part(StorageUsage.Role.PACK, pack.absolutePath),
            ),
        )
        val published = mutableListOf<StorageUsage.Snapshot>()

        StorageUsage.scanAndSave(
            snapshot = StorageUsage.Snapshot(listOf(item)),
            target = File(t.root, "usage.json"),
            publish = { published += it },
            measureItem = { dir, acc ->
                acc.bytes = if (dir.name == "work") 100L else 200L
                acc.files = 1
                StorageUsage.State.DONE
            },
        )

        // 两段始终都在清单上：没量的那段是「未统计」，不是从清单里消失
        for (s in published) assertEquals(2, s.items[0].parts.size)

        // 每一版都与上一版不同：界面才分得出「又进了一步」。曾经两版长得一模一样
        // （发布出去的快照里挂着还在被改的列表），于是合计停在旧数上、各行却是新数。
        for (i in 1 until published.size) {
            assertNotEquals("version $i looks like the one before it", published[i - 1], published[i])
        }

        // 合计随已量完的段累加：一段一段加上去，不是一次性跳到最终值
        val totals = published.map { s -> s.items.sumOf { item -> item.bytes } }
        assertEquals(0L, totals.first())
        assertEquals(300L, totals.last())
        for (i in 1 until totals.size) assertTrue("the total went down at $i", totals[i] >= totals[i - 1])

        // 量完之后：两段都数到了
        val last = published.last()
        assertEquals(300L, last.items[0].bytes)
        assertEquals(StorageUsage.State.DONE, last.items[0].parts[0].state)
        assertEquals(StorageUsage.State.DONE, last.items[0].parts[1].state)
        t.root.deleteRecursively()
    }

    @Test
    fun `a re-measure does not pass the old numbers off as the current ones`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)     // 上一轮量到的：100 字节
        val published = mutableListOf<StorageUsage.Snapshot>()

        StorageUsage.scanAndSave(
            snapshot = snapshot,
            target = File(t.root, "usage.json"),
            force = true,
            publish = { published += it },
            measureItem = { _, acc ->
                acc.bytes = 42
                StorageUsage.State.DONE
            },
        )

        // 重新统计期间，旧数字不作数：合计从 0 起步，量出来多少是多少
        assertEquals(0L, published.first().items[0].bytes)
        assertEquals(42L, published.last().items[0].bytes)
        t.root.deleteRecursively()
    }

    @Test
    fun `a part that cannot be reached does not force the whole item to be measured again`() = runBlocking<Unit> {
        val t = Tree()
        val work = File(t.root, "work").apply { mkdirs() }
        t.fileIn(work, "a.bin", 50)
        val cardPack = "/storage/FB9A-DC19/Android/data/x/files/ports/Game-data"
        // 上一轮量过：那时卡在，两段都有数字
        val item = StorageUsage.Item(
            id = "0100000000010000",
            name = "Game",
            parts = listOf(
                StorageUsage.Part(StorageUsage.Role.WORKSPACE, work.absolutePath, 50L, 1, StorageUsage.State.DONE),
                StorageUsage.Part(StorageUsage.Role.PACK, cardPack, 100L, 1, StorageUsage.State.DONE),
            ),
        )
        val fp = StorageUsage.itemFingerprint(item) { StorageUsage.fingerprintOf(it) }
        val snapshot = StorageUsage.Snapshot(
            items = listOf(item.copy(fingerprint = fp)),
            scannedAt = System.currentTimeMillis(),
        )
        // 现在卡不在了：卡上那段够不着（没有数字，也不是 0）
        val now = item.copy(
            fingerprint = fp,
            parts = listOf(
                item.parts[0],
                item.parts[1].copy(bytes = 0L, files = 0, state = StorageUsage.State.UNKNOWN),
            ),
        )
        val calls = AtomicInteger()

        StorageUsage.scanAndSave(
            snapshot = snapshot.copy(items = listOf(now)),
            target = File(t.root, "usage.json"),
            measureItem = { _, acc ->
                calls.incrementAndGet()
                acc.bytes = 1
                StorageUsage.State.DONE
            },
        )

        // 够不着的那段无从比起：不该因此把工作区重量一遍（玩家每次进分区都要等它）
        assertEquals(0, calls.get())
        t.root.deleteRecursively()
    }

    @Test
    fun `an interrupted measurement is measured again the next time`() = runBlocking<Unit> {
        val t = Tree()
        t.file("a.bin", 100)
        val snapshot = measured(t.root, 100L)
        var kept: StorageUsage.Snapshot? = null

        try {
            StorageUsage.scanAndSave(
                snapshot = snapshot,
                target = File(t.root, "usage.json"),
                force = true,
                publish = { kept = it },
                measureItem = { _, _ -> throw CancellationException("the panel closed") },
            )
        } catch (e: CancellationException) {
        }

        assertNotNull(kept)
        assertTrue("half a measurement is not 'measured today'", kept!!.interrupted)

        // 下次进分区：重新量
        val calls = AtomicInteger()
        StorageUsage.scanAndSave(
            snapshot = kept!!,
            target = File(t.root, "usage.json"),
            measureItem = { _, acc ->
                calls.incrementAndGet()
                acc.bytes = 100
                StorageUsage.State.DONE
            },
        )
        assertEquals(1, calls.get())
        t.root.deleteRecursively()
    }

    // ---- 落盘与读回 -----------------------------------------------------------------------------------------

    @Test
    fun `the snapshot survives the round trip`() {
        val t = Tree()
        val part = StorageUsage.Part(StorageUsage.Role.WORKSPACE, t.root.absolutePath, 1234L, 7,
            StorageUsage.State.PARTIAL)
        val snapshot = StorageUsage.Snapshot(
            items = listOf(StorageUsage.Item(id = "0100", name = "Game", parts = listOf(part), fingerprint = "fp")),
            scannedAt = 1700000000000L,
        )

        val text = StorageUsage.render(snapshot)
        val back = StorageUsage.parse(text)

        assertNotNull(back)
        assertEquals(snapshot.scannedAt, back!!.scannedAt)
        assertEquals(1, back.items.size)
        assertEquals("0100", back.items[0].id)
        assertEquals("Game", back.items[0].name)
        assertEquals(1234L, back.items[0].parts[0].bytes)
        assertEquals(7, back.items[0].parts[0].files)
        assertEquals(StorageUsage.State.PARTIAL, back.items[0].parts[0].state)
        assertEquals(StorageUsage.Role.WORKSPACE, back.items[0].parts[0].role)
        assertEquals("fp", back.items[0].fingerprint)
        t.root.deleteRecursively()
    }

    @Test
    fun `a snapshot of another schema is not read`() {
        assertNull(StorageUsage.parse("""{"schema":"something.else","measuredAt":1}"""))
        assertNull(StorageUsage.parse("not json at all"))
    }

    @Test
    fun `a written snapshot is read back`() {
        val dir = tmpDir()
        val target = File(dir, "usage.json")
        val part = StorageUsage.Part(StorageUsage.Role.PACK, "/data/ports", 99L, 2, StorageUsage.State.DONE)
        val snapshot = StorageUsage.Snapshot(
            items = listOf(StorageUsage.Item(id = "0100", name = "Game", parts = listOf(part))),
            scannedAt = 42L,
        )

        StorageUsage.write(target, snapshot)
        val back = StorageUsage.read(target)

        assertNotNull(back)
        assertEquals(99L, back!!.items[0].parts[0].bytes)
        dir.deleteRecursively()
    }
}
