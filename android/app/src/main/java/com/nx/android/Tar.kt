package com.nx.android

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

/**
 * A tar reader: the files, the folders, the links (symbolic and hard), with the long names and the pax headers
 * the archives use. The rootfs tarball and the .NET SDK's are read with it on the first start.
 *
 * It is its own class (no Android in it) so that it can be tested on the JVM: TarTest.
 */
object Tar {

    /** Unpacks an archive into [dest] */
    fun unpack(input: InputStream, dest: File) {
        val header = ByteArray(512)
        var longName: String? = null
        var paxPath: String? = null
        var paxSize: Long? = null
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) continue
            var name = cstring(header, 0, 100)
            val mode = octal(header, 100, 8)          // the permissions the archive gives
            var size = octal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val link = cstring(header, 157, 100)
            longName?.let { name = it; longName = null }
            paxPath?.let { name = it; paxPath = null }
            paxSize?.let { size = it }
            paxSize = null
            when (type) {
                'L' -> { longName = cstring(readBlock(input, size), 0, size.toInt()); skip(input, padding(size)); continue }
                'x', 'g' -> {
                    // a pax header carries the path (and the size) of the entry that follows
                    val body = String(readBlock(input, size), Charsets.UTF_8)
                    for (m in Regex("(\\d+) ([a-z]+)=([^\\n]*)").findAll(body)) {
                        when (m.groupValues[2]) {
                            "path" -> paxPath = m.groupValues[3]
                            "size" -> paxSize = m.groupValues[3].toLongOrNull() ?: paxSize
                        }
                    }
                    skip(input, padding(size)); continue
                }
            }
            val target = File(dest, name)
            when (type) {
                '5' -> { target.mkdirs(); skip(input, padding(size)) }
                '2' -> {
                    target.parentFile?.mkdirs(); target.delete()
                    // the link is stored as it is: a relative one points into the link's own folder
                    Files.createSymbolicLink(target.toPath(), Paths.get(link))
                    skip(input, padding(size))
                }
                '1' -> {
                    target.parentFile?.mkdirs()
                    val from = File(dest, link)
                    if (from.exists()) from.copyTo(target, overwrite = true)
                    skip(input, padding(size))
                }
                '0', '\u0000' -> {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> copyBlock(input, out, size) }
                    // the permissions of the archive: the guest's programs have to be executable
                    applyMode(target, mode)
                    skip(input, padding(size))
                }
                else -> skip(input, padding(size))
            }
        }
    }

    /**
     * The permissions the archive gives a file. Android's File.setExecutable does nothing, so the mode is set
     * through POSIX: the guest's programs have to be executable, or the porter cannot start them.
     */
    fun applyMode(target: File, mode: Long) {
        val m = mode and 511L              // 0o777
        val perms = EnumSet.noneOf(PosixFilePermission::class.java)
        if (m and 256L != 0L) perms.add(PosixFilePermission.OWNER_READ)      // 0o400
        if (m and 128L != 0L) perms.add(PosixFilePermission.OWNER_WRITE)     // 0o200
        if (m and 64L != 0L) perms.add(PosixFilePermission.OWNER_EXECUTE)    // 0o100
        if (m and 32L != 0L) perms.add(PosixFilePermission.GROUP_READ)      // 0o040
        if (m and 16L != 0L) perms.add(PosixFilePermission.GROUP_WRITE)     // 0o020
        if (m and 8L != 0L) perms.add(PosixFilePermission.GROUP_EXECUTE)    // 0o010
        if (m and 4L != 0L) perms.add(PosixFilePermission.OTHERS_READ)      // 0o004
        if (m and 2L != 0L) perms.add(PosixFilePermission.OTHERS_WRITE)     // 0o002
        if (m and 1L != 0L) perms.add(PosixFilePermission.OTHERS_EXECUTE)   // 0o001
        try {
            Files.setPosixFilePermissions(target.toPath(), perms)
        } catch (e: Exception) {
            // a file that cannot get its permissions is not worth stopping for: the doctor says what is missing
        }
    }

    /** The bytes that pad a block of data to a multiple of 512 */
    private fun padding(size: Long): Long = (512 - (size % 512)) % 512

    private fun readFully(input: InputStream, into: ByteArray): Boolean {
        var read = 0
        while (read < into.size) {
            val n = input.read(into, read, into.size - read)
            if (n < 0) return read == into.size
            read += n
        }
        return true
    }

    private fun readBlock(input: InputStream, size: Long): ByteArray {
        val buf = ByteArray(size.toInt())
        readFully(input, buf)
        return buf
    }

    /** Copies a file's bytes; a file that gets fewer than the archive says was cut short (the app was killed) */
    private fun copyBlock(input: InputStream, out: OutputStream, size: Long) {
        val buf = ByteArray(1 shl 16)
        var left = size
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buf, 0, n); left -= n
        }
        if (left > 0) throw java.io.IOException("the archive ended $left bytes early: a file was cut short")
    }

    private fun skip(input: InputStream, size: Long) {
        var left = size
        while (left > 0) {
            val n = input.skip(left)
            if (n <= 0) { input.read(); left-- } else left -= n
        }
    }

    private fun cstring(buf: ByteArray, at: Int, len: Int): String {
        var end = at
        while (end < at + len && buf[end] != 0.toByte()) end++
        return String(buf, at, end - at, Charsets.UTF_8)
    }

    private fun octal(buf: ByteArray, at: Int, len: Int): Long {
        val s = cstring(buf, at, len).trim()
        if (s.isEmpty()) return 0
        return try { s.toLong(8) } catch (e: NumberFormatException) { 0 }
    }
}
