package com.nx.android
import com.nx.android.data.Tar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/** The real rootfs tarball: the reader has to bring out the files with their sizes (a file of 0 bytes is a broken one) */
class RootfsTest {

    @Test
    fun theRealRootfs() {
        val tar = File("../packaging/android-apk/.cache/assets/rootfs.pkg")
        if (!tar.exists()) return
        val dir = Files.createTempDirectory("nx2a-rootfs").toFile()
        GZIPInputStream(tar.inputStream()).use { gz -> Tar.unpack(gz, dir) }
        val python = File(dir, "usr/bin/python3.13")
        println("python3.13: exists=${python.exists()} size=${python.length()}")
        assertTrue("python3.13 is there", python.exists())
        assertEquals("python3.13's size", 6804784L, python.length())
    }
}
