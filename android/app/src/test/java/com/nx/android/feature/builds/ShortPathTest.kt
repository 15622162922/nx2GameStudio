package com.nx.android.feature.builds

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortPathTest {

    @Test
    fun `a long path keeps its head and its last two parts`() {
        val path = "/storage/emulated/0/Download/QuarkCloudDrive/f91c610d/忍者神龟/[整合版v1.0.9].nsp"
        assertEquals("/storage/…/忍者神龟/[整合版v1.0.9].nsp", shortPath(path))
    }

    @Test
    fun `a short path is left alone`() {
        val path = "/storage/emulated/0/game.nsp"
        assertEquals(path, shortPath(path))
        assertEquals("/a/b/c/d", shortPath("/a/b/c/d"))
    }
}
