package com.nx.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's own name: one place says it, everyone else reads it from there.
 *
 * The name is a resource, so reading it needs a Context - the files behind it do not. The test reads the
 * resources and the two screens that show the name, so a name written down a second time is caught here.
 */
class AppNameTest {

    /** The app's module folder, wherever Gradle ran the test from */
    private fun module(): File {
        var d = File(".").absoluteFile
        while (true) {
            if (File(d, "src/main/res/values/strings.xml").isFile) return d
            if (File(d, "app/src/main/res/values/strings.xml").isFile) return File(d, "app")
            d = d.parentFile ?: throw IllegalStateException("the app's resources are not found from ${File(".").absolutePath}")
        }
    }

    private fun res(path: String): String = File(module(), "src/main/res/$path").readText()
    private fun src(path: String): String = File(module(), "src/main/java/com/nx/android/$path").readText()

    /** The text of one string in one resource file */
    private fun string(body: String, name: String): String {
        val m = Regex("<string name=\"$name\"[^>]*>(.*?)</string>").find(body)
        assertTrue("the string $name is there", m != null)
        return m!!.groupValues[1]
    }

    @Test
    fun theAppHasOneName() {
        assertEquals("nx2androidX", string(res("values/strings.xml"), "app_name"))
    }

    @Test
    fun theWelcomePageTakesTheNameAsAnArgument() {
        for (f in listOf("values/strings.xml", "values-zh/strings.xml")) {
            val welcome = string(res(f), "onb_welcome")
            assertTrue("the welcome page of $f takes the name", welcome.contains("%1\$s"))
            assertFalse("the welcome page of $f does not write the name down", welcome.contains("nx2android"))
        }
    }

    @Test
    fun noScreenWritesTheNameDown() {
        assertFalse("the header does not write the name down", src("ui/App.kt").contains("append(\"nx2\""))
        assertFalse("the header does not write the name down", src("ui/App.kt").contains("append(\"android\""))
        assertFalse("the notification does not write the name down", src("PortService.kt").contains("\"nx2android\""))
    }
}
