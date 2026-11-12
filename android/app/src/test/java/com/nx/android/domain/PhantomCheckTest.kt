package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The limit on the app's child processes, read on the JVM: what the system says and what the app makes of it.
 */
class PhantomCheckTest {

    // Android 12 is API 31; the settings and the feature flag came with 12L (32).
    private val android12 = 31
    private val android12l = 32
    private val android14 = 34

    @Test
    fun beforeAndroid12ThereIsNoLimit() {
        assertEquals(PhantomCheck.Status.NOT_NEEDED, PhantomCheck.status(30, "32", "", ""))
        assertEquals(PhantomCheck.Status.NOT_NEEDED, PhantomCheck.status(android12 - 1, "2147483647", "false", ""))
    }

    @Test
    fun aRaisedLimitMeansItIsOff() {
        assertEquals(PhantomCheck.Status.OFF, PhantomCheck.status(android12, "2147483647", "", ""))
        assertEquals(PhantomCheck.Status.OFF, PhantomCheck.status(android12l, "2147483647", "", ""))
    }

    @Test
    fun theDefaultLimitMeansItIsOn() {
        assertEquals(PhantomCheck.Status.ON, PhantomCheck.status(android12, "32", "", ""))
        assertEquals(PhantomCheck.Status.ON, PhantomCheck.status(android12l, "32", "", ""))
        assertEquals(PhantomCheck.Status.ON, PhantomCheck.status(android14, "32", "", ""))
    }

    @Test
    fun theGlobalSettingTurnsItOff() {
        assertEquals(PhantomCheck.Status.OFF, PhantomCheck.status(android12l, "32", "false", ""))
        assertEquals(PhantomCheck.Status.OFF, PhantomCheck.status(android14, "32", "0", ""))
        // the property is what Developer options' toggle writes
        assertEquals(PhantomCheck.Status.OFF, PhantomCheck.status(android14, "32", "", "false"))
        // the global setting is read first
        assertEquals(PhantomCheck.Status.ON, PhantomCheck.status(android14, "32", "true", "false"))
    }

    @Test
    fun android12WithoutTheValueIsUnknown() {
        // Android 12's apps may not read device_config
        assertEquals(PhantomCheck.Status.UNKNOWN, PhantomCheck.status(android12, "", "", ""))
    }

    @Test
    fun theRowIsAHintWhileTheLimitIsOn() {
        assertEquals(EnvCheck.Level.HINT, PhantomCheck.row(PhantomCheck.Status.ON).level)
        assertEquals(EnvCheck.Level.HINT, PhantomCheck.row(PhantomCheck.Status.UNKNOWN).level)
        assertEquals(EnvCheck.Level.OK, PhantomCheck.row(PhantomCheck.Status.OFF).level)
        assertEquals(EnvCheck.Level.OK, PhantomCheck.row(PhantomCheck.Status.NOT_NEEDED).level)
    }

    @Test
    fun onlyAnOpenLimitOffersTheFix() {
        assertEquals(true, PhantomCheck.row(PhantomCheck.Status.ON).fixable)
        assertEquals(true, PhantomCheck.row(PhantomCheck.Status.UNKNOWN).fixable)
        assertEquals(false, PhantomCheck.row(PhantomCheck.Status.OFF).fixable)
        assertEquals(false, PhantomCheck.row(PhantomCheck.Status.NOT_NEEDED).fixable)
    }
}
