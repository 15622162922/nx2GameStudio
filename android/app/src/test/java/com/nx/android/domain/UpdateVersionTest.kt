package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fork's versions: the tag of a release and the app's own versionName are the same thing written two
 * ways, and only the newer of the two counts as an update.
 */
class UpdateVersionTest {

    @Test
    fun `the fork's own tags are read`() {
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 2), UpdateVersion.parse("v0.3.0-alpha02"))
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 2), UpdateVersion.parse("0.3.0-alpha02"))
        assertEquals(ForkVersion(0, 3, 0, Stage.FINAL, 0), UpdateVersion.parse("v0.3.0"))
        assertEquals(ForkVersion(1, 0, 0, Stage.ALPHA, 1), UpdateVersion.parse("v1.0.0-alpha01"))
        assertEquals(ForkVersion(0, 3, 1, Stage.BETA, 1), UpdateVersion.parse("v0.3.1-beta01"))
        assertEquals(ForkVersion(0, 3, 0, Stage.RC, 2), UpdateVersion.parse("v0.3.0-rc02"))
    }

    @Test
    fun `the first release's own writing is the base's first alpha`() {
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 1), UpdateVersion.parse("v0.3-android"))
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 1), UpdateVersion.parse("0.3-android"))
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 1), UpdateVersion.parse("v0.3-android.1"))
    }

    @Test
    fun `what is not the fork's shape is not read`() {
        assertNull(UpdateVersion.parse("latest"))
        assertNull(UpdateVersion.parse(""))
        assertNull(UpdateVersion.parse("v0.3"))
        assertNull(UpdateVersion.parse("v0.3-android-2"))
        assertNull(UpdateVersion.parse("v0.3.0-gamma01"))   // not a stage
        assertNull(UpdateVersion.parse("v0.3.0-alpha"))     // a stage without its number
    }

    @Test
    fun `a newer release is an update`() {
        assertTrue(UpdateVersion.isNewer("v0.3.0-alpha03", "0.3.0-alpha02"))
        assertTrue(UpdateVersion.isNewer("v0.3.0", "0.3.0-alpha02"))
        assertTrue(UpdateVersion.isNewer("v0.3.0-alpha02", "0.3.0-alpha01"))
        assertTrue(UpdateVersion.isNewer("v0.4.0-alpha01", "0.3.0-alpha02"))
    }

    @Test
    fun `the same release is not an update`() {
        assertFalse(UpdateVersion.isNewer("v0.3.0-alpha02", "0.3.0-alpha02"))
        assertFalse(UpdateVersion.isNewer("0.3.0-alpha02", "v0.3.0-alpha02"))
    }

    @Test
    fun `an older release is not an update`() {
        assertFalse(UpdateVersion.isNewer("v0.3.0-alpha01", "0.3.0-alpha02"))
        assertFalse(UpdateVersion.isNewer("v0.3.0-alpha02", "v0.3.0"))
        assertFalse(UpdateVersion.isNewer("v0.2.0-alpha01", "0.3.0-alpha02"))
    }

    @Test
    fun `the stages are ordered`() {
        // an alpha is older than the beta on it, and the beta older than the released version
        assertTrue(UpdateVersion.isNewer("v0.3.0-beta01", "v0.3.0-alpha09"))
        assertTrue(UpdateVersion.isNewer("v0.3.0-rc01", "v0.3.0-beta09"))
        assertTrue(UpdateVersion.isNewer("v0.3.0", "v0.3.0-rc09"))
        // and a build ahead of the releases is not an update either
        assertFalse(UpdateVersion.isNewer("v0.3.0-alpha02", "v0.3.0-beta01"))
    }

    @Test
    fun `the numbers are compared, not the strings`() {
        assertTrue(UpdateVersion.isNewer("v0.10.0-alpha01", "0.9.0-alpha09"))
        assertFalse(UpdateVersion.isNewer("v0.9.0-alpha01", "0.10.0-alpha09"))
    }

    @Test
    fun `what cannot be read is not an update`() {
        assertFalse(UpdateVersion.isNewer("v0.3.0-gamma01", "0.3.0-alpha02"))
        assertFalse(UpdateVersion.isNewer("", ""))
    }

    @Test
    fun `the app's own version is read in either shape`() {
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 2), UpdateVersion.parseOwn("0.3.0-alpha02"))
        assertEquals(ForkVersion(0, 3, 0, Stage.ALPHA, 1), UpdateVersion.parseOwn("0.3-android"))
        assertEquals(ForkVersion(0, 1, 0, Stage.FINAL, 0), UpdateVersion.parseOwn("0.1.0"))
        assertNull(UpdateVersion.parseOwn("latest"))
    }

    @Test
    fun `an app on an older build is told about the release`() {
        // the phone carries the older build (0.1.0): the fork's release is still newer than it
        assertTrue(UpdateVersion.isNewer("v0.3.0-alpha02", "0.1.0"))
        // the app of the first release knows the second alpha is newer
        assertTrue(UpdateVersion.isNewer("v0.3.0-alpha02", "0.3-android"))
        assertFalse(UpdateVersion.isNewer("v0.3.0-alpha02", "0.3.0-alpha02"))
    }

    @Test
    fun `the tag is the last piece of the address the release page ended at`() {
        assertEquals("0.3.0-alpha02", UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/releases/tag/v0.3.0-alpha02"))
        assertEquals("0.3.0-alpha02", UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/releases/tag/v0.3.0-alpha02/"))
        assertEquals("0.3-android", UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/releases/tag/v0.3-android"))
    }

    @Test
    fun `an address without a tag holds none`() {
        assertNull(UpdateVersion.tagFromUrl(""))
        assertNull(UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/"))
    }

    @Test
    fun `the canonical writing is what the app remembers`() {
        assertEquals("0.3.0-alpha02", ForkVersion(0, 3, 0, Stage.ALPHA, 2).toString())
        assertEquals("0.3.0-alpha01", ForkVersion(0, 3, 0, Stage.ALPHA, 1).toString())
        assertEquals("0.3.0", ForkVersion(0, 3, 0, Stage.FINAL, 0).toString())
        assertEquals("1.0.0-beta03", ForkVersion(1, 0, 0, Stage.BETA, 3).toString())
    }
}
