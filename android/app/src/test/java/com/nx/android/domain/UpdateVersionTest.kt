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
        assertEquals(ForkVersion(0, 3, 0), UpdateVersion.parse("v0.3-android"))
        assertEquals(ForkVersion(0, 3, 0), UpdateVersion.parse("0.3-android"))
        assertEquals(ForkVersion(0, 3, 1), UpdateVersion.parse("v0.3-android.1"))
        assertEquals(ForkVersion(1, 0, 0), UpdateVersion.parse("v1.0-android"))
    }

    @Test
    fun `the upstream's own tags are not the fork's`() {
        assertNull(UpdateVersion.parse("v0.3.0"))
        assertNull(UpdateVersion.parse("0.3.0"))
        assertNull(UpdateVersion.parse("latest"))
        assertNull(UpdateVersion.parse(""))
        assertNull(UpdateVersion.parse("v0.3-android-2"))
    }

    @Test
    fun `a newer release is an update`() {
        assertTrue(UpdateVersion.isNewer("v0.4-android", "0.3-android"))
        assertTrue(UpdateVersion.isNewer("v0.3-android.1", "0.3-android"))
        assertTrue(UpdateVersion.isNewer("v1.0-android", "0.9-android"))
    }

    @Test
    fun `the same release is not an update`() {
        assertFalse(UpdateVersion.isNewer("v0.3-android", "0.3-android"))
        assertFalse(UpdateVersion.isNewer("0.3-android", "v0.3-android"))
    }

    @Test
    fun `an older release is not an update`() {
        assertFalse(UpdateVersion.isNewer("v0.2-android", "0.3-android"))
        assertFalse(UpdateVersion.isNewer("v0.3-android", "0.3-android.1"))
    }

    @Test
    fun `the numbers are compared, not the strings`() {
        assertTrue(UpdateVersion.isNewer("v0.10-android", "0.9-android"))
        assertFalse(UpdateVersion.isNewer("v0.9-android", "0.10-android"))
    }

    @Test
    fun `what cannot be read is not an update`() {
        // the release's tag has to be the fork's own shape - the upstream's v0.3.0 is not a release of ours
        assertFalse(UpdateVersion.isNewer("v0.3.0", "0.3-android"))
        assertFalse(UpdateVersion.isNewer("", ""))
    }

    @Test
    fun `the app's own version is read in either shape`() {
        assertEquals(ForkVersion(0, 3, 0), UpdateVersion.parseOwn("0.3-android"))
        assertEquals(ForkVersion(0, 1, 0), UpdateVersion.parseOwn("0.1.0"))
        assertEquals(ForkVersion(0, 3, 0), UpdateVersion.parseOwn("0.3.0"))
        assertNull(UpdateVersion.parseOwn("latest"))
    }

    @Test
    fun `an app on an older build is told about the release`() {
        // the phone carries the older build (0.1.0): the fork's release is still newer than it
        assertTrue(UpdateVersion.isNewer("v0.3-android", "0.1.0"))
        assertTrue(UpdateVersion.isNewer("v0.3-android.1", "0.3-android"))
        assertFalse(UpdateVersion.isNewer("v0.3-android", "0.3-android"))
        // the app's own version is read in the plain shape too, so a release above it counts
        assertTrue(UpdateVersion.isNewer("v0.4-android", "0.3.0"))
    }

    @Test
    fun `the tag is the last piece of the address the release page ended at`() {
        assertEquals("0.3-android", UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/releases/tag/v0.3-android"))
        assertEquals("0.3-android", UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/releases/tag/v0.3-android/"))
    }

    @Test
    fun `an address without a tag holds none`() {
        assertNull(UpdateVersion.tagFromUrl(""))
        assertNull(UpdateVersion.tagFromUrl("https://github.com/zFitness/nx2androidX/"))
    }

    @Test
    fun `the canonical writing is what the app remembers`() {
        assertEquals("0.3-android", ForkVersion(0, 3, 0).toString())
        assertEquals("0.3-android.1", ForkVersion(0, 3, 1).toString())
        assertEquals("1.0-android", ForkVersion(1, 0, 0).toString())
    }
}
