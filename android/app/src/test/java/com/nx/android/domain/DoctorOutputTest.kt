package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The doctor's rows out of the runner's stream: the runner's own lines are around them, and only the
 * doctor's own output is JSON.
 */
class DoctorOutputTest {

    private val doctorJson = """
        [
          {
            "name": "Python",
            "ok": true,
            "detail": "3.13.5",
            "required": true
          },
          {
            "name": "prod.keys",
            "ok": true,
            "detail": "/sdcard/switch/prod.keys"
          }
        ]
    """.trimIndent()

    @Test
    fun `the rows are found behind the runner's lines`() {
        val stream = "检查工具…\n改写解释器路径完成\n$doctorJson\n"
        val rows = DoctorOutput.rows(stream)
        assertNotNull(rows)
        assertEquals(2, rows!!.length())
    }

    @Test
    fun `a red line in front does not hide the rows`() {
        val stream = "检查工具…\n[RED] 改写解释器路径失败：boom\n$doctorJson"
        val rows = DoctorOutput.rows(stream)
        assertNotNull(rows)
        assertEquals(2, rows!!.length())
        assertEquals("Python", DoctorOutput.row(rows, "Python")?.optString("name"))
    }

    @Test
    fun `the exit code behind the rows does not break them`() {
        val stream = "$doctorJson\n[RED] 退出 1\n"
        val rows = DoctorOutput.rows(stream)
        assertNotNull(rows)
        assertEquals(2, rows!!.length())
    }

    @Test
    fun `a stream without json has no rows`() {
        assertNull(DoctorOutput.rows("检查工具…\n改写解释器路径完成\n"))
        assertNull(DoctorOutput.rows(""))
    }

    @Test
    fun `the row of a tool is found, and a tool the doctor did not report is not`() {
        val rows = DoctorOutput.rows(doctorJson)!!
        val keys = DoctorOutput.row(rows, "prod.keys")
        assertNotNull(keys)
        assertTrue(keys!!.optBoolean("ok"))
        assertEquals("/sdcard/switch/prod.keys", keys.optString("detail"))
        assertNull(DoctorOutput.row(rows, "Nintend Switch Firmware"))
    }

    @Test
    fun `a tool that is not there is not ok`() {
        val rows = DoctorOutput.rows("""[{"name": "prod.keys", "ok": false, "detail": "no such file: /x"}]""")!!
        val keys = DoctorOutput.row(rows, "prod.keys")!!
        assertFalse(keys.optBoolean("ok"))
        assertEquals("no such file: /x", keys.optString("detail"))
    }
}
