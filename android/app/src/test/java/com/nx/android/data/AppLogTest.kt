package com.nx.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * App 自己那份日志文件：写进去的话读得回来，清掉之后读不到。
 *
 * 「清空日志」清的就是它（屏幕上的那份是另一处记忆，见 PortState.clearLog）。
 */
class AppLogTest {

    @Test
    fun theLogIsReadBackAndCleared() {
        val dir = Files.createTempDirectory("nx2a-applog").toFile()
        val file = File(dir, "log.txt")
        AppLog.init(file)

        AppLog.line("first")
        AppLog.line("second")
        assertEquals("first\nsecond", AppLog.text())
        assertTrue("the log has the lines in it", AppLog.text().contains("second"))

        AppLog.clear()
        assertEquals("nothing is left after the clear", "", AppLog.text())

        file.delete()
        dir.delete()
    }
}
