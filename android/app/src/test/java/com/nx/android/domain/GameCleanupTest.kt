package com.nx.android.domain

import com.nx.android.data.library.GameCleanupFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 删除一个游戏的构建数据：数据包、工作区、半成品包；游戏源文件是玩家的，不碰 */
class GameCleanupTest {

    private fun tmpDir(): File = File.createTempFile("nx2a-clean", "").let { f -> f.delete(); f.mkdirs(); f }

    /** 一棵假的私有目录：ports/ 与 work/ */
    private class Tree {
        val root = File.createTempFile("nx2a-clean", "").let { f -> f.delete(); f.mkdirs(); f }
        val ports = File(root, "ports").apply { mkdirs() }
        val work = File(root, "work").apply { mkdirs() }

        fun pack(name: String): File = File(ports, name).apply { mkdirs() }
        fun workspace(titleId: String): File = File(work, "${titleId}_build").apply { mkdirs() }
        fun file(dir: File, name: String, bytes: Int): File = File(dir, name).apply { writeBytes(ByteArray(bytes)) }
    }

    @Test
    fun `the plan holds the pack, the workspace and the half-made packs`() {
        val t = Tree()
        val pack = t.pack("Game-data")
        val ws = t.workspace("0100000000010000")
        val partial = t.pack("Other-data.partial")

        val plan = GameCleanup.plan("0100000000010000", listOf(pack), ws, listOf(partial))

        assertEquals(3, plan.targets.size)
        assertTrue(plan.workspaceKnown)
        assertTrue(plan.targets.any { it.kind == GameCleanup.Kind.PACK })
        assertTrue(plan.targets.any { it.kind == GameCleanup.Kind.WORKSPACE })
        assertTrue(plan.targets.any { it.kind == GameCleanup.Kind.PARTIAL })
        assertTrue(ws.exists() && partial.exists())   // 计划只是计划，还没删
    }

    @Test
    fun `deleting takes the three away and leaves the game's own file alone`() {
        val t = Tree()
        val game = File(t.root, "Game [0100000000010000].nsp").apply { writeText("NSP") }
        val pack = t.pack("Game-data")
        val ws = t.workspace("0100000000010000")
        val partial = t.pack("Game-data.partial")
        t.file(pack, "romfs.bin", 1024)

        val plan = GameCleanup.plan("0100000000010000", listOf(pack), ws, listOf(partial))
        val result = GameCleanupFiles.delete(plan)

        assertTrue(result.failed.isEmpty())
        assertFalse(pack.exists())
        assertFalse(ws.exists())
        assertFalse(partial.exists())
        assertTrue(game.exists())        // 源文件是玩家的，不碰
    }

    @Test
    fun `a build in flight keeps its half-made pack`() {
        val t = Tree()
        val partial = t.pack("Game-data.partial")
        val pack = t.pack("Game-data")

        val plan = GameCleanup.plan("0100000000010000", listOf(pack), null, emptyList())
        GameCleanupFiles.delete(plan)

        assertTrue(partial.exists())    // 另一个构建正在写它，不能删
        assertFalse(pack.exists())
    }

    /** 清构建缓存：数据包不在清单里（不碰它），游戏照常能玩 */
    @Test
    fun `clearing the cache leaves the data pack alone`() {
        val t = Tree()
        val pack = t.pack("Game-data")
        val ws = t.workspace("0100000000010000")
        val partial = t.pack("Game-data.partial")

        val plan = GameCleanup.plan("0100000000010000", emptyList(), ws, listOf(partial))
        assertFalse(plan.targets.any { it.kind == GameCleanup.Kind.PACK })
        GameCleanupFiles.delete(plan)

        assertTrue(pack.exists())       // 数据包与存档不动
        assertFalse(ws.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun `a file name without a title ID leaves the workspace unknown`() {
        val t = Tree()
        val plan = GameCleanup.plan(null, emptyList(), null, emptyList())
        assertFalse(plan.workspaceKnown)
        assertTrue(plan.targets.isEmpty())
    }

    @Test
    fun `the workspace is the folder of the title ID`() {
        val t = Tree()
        assertEquals(File(t.work, "0100000000010000_build"),
                     GameCleanup.workspaceOf("0100000000010000", t.work))
        assertEquals(null, GameCleanup.workspaceOf(null, t.work))
    }

    /** 多个卷：私有的与卡上的包一起删，卡上搬了一半留下的半成品也清 */
    @Test
    fun `the packs on every volume go with the game`() {
        val t = Tree()
        val card = File(t.root, "card/ports").apply { mkdirs() }
        val private = t.pack("Game-data")
        val onCard = File(card, "Game-data").apply { mkdirs() }
        val cardPartial = File(card, "Game-data.partial").apply { mkdirs() }

        val plan = GameCleanup.plan("0100000000010000", listOf(private, onCard), null, listOf(cardPartial))
        assertEquals(3, plan.targets.size)      // 两个包 + 卡上的半成品
        GameCleanupFiles.delete(plan)

        assertFalse(private.exists())
        assertFalse(onCard.exists())
        assertFalse(cardPartial.exists())
    }
}
