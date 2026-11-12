package com.nx.android.data

import com.nx.android.domain.environment.GuestProcessLauncher
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RuntimeCompilerTest {
    private fun compile(output: String, code: Int): Boolean {
        val launcher = object : GuestProcessLauncher {
            override fun guestEnv() = mutableMapOf<String, String>()
            override fun guest(args: List<String>, extra: Map<String, String>): Process {
                assertEquals(listOf("-m", "porter", "rebuild-runtime", "/packs/Game-data", "--progress", "json", "--no-lsfg"), args)
                assertTrue(extra.isEmpty())
                return object : Process() {
                    override fun getOutputStream() = ByteArrayOutputStream()
                    override fun getInputStream() = ByteArrayInputStream(output.toByteArray())
                    override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
                    override fun waitFor() = code
                    override fun exitValue() = code
                    override fun destroy() = Unit
                }
            }
        }
        return RuntimeCompiler(launcher).rebuild("/packs/Game-data", true) {}
    }

    @Test fun `runtime update needs both successful exit and the matching commit result`() {
        assertTrue(compile("""{"ev":"result","data":"/packs/Game-data"}""", 0))
        assertFalse(compile("""{"ev":"result","data":"/packs/Game-data"}""", 1))
        assertFalse(compile("compiler exited without a result", 0))
        assertFalse(compile("""{"ev":"result","data":"/packs/Other-data"}""", 0))
    }

    @Test fun `runtime command follows current frame generation policy without source keys`() {
        assertFalse(RuntimeCompiler.args("/packs/Game-data", false).contains("--no-lsfg"))
        assertFalse(RuntimeCompiler.args("/packs/Game-data", false).contains("-k"))
    }
}
