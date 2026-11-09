package com.nx.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The app's own language: the mode the player picked, and the code it is kept under.
 *
 * The rest of it (the resources, the choice itself) needs a Context, so it is checked on the phone.
 */
class AppLocaleTest {

    @Test
    fun `the mode comes from the code the choice is kept under`() {
        assertEquals(AppLocale.Mode.SYSTEM, AppLocale.Mode.of(""))
        assertEquals(AppLocale.Mode.ZH, AppLocale.Mode.of("zh"))
        assertEquals(AppLocale.Mode.EN, AppLocale.Mode.of("en"))
    }

    @Test
    fun `a code nobody wrote is the phone's own language`() {
        assertEquals(AppLocale.Mode.SYSTEM, AppLocale.Mode.of("nonsense"))
        assertEquals(AppLocale.Mode.SYSTEM, AppLocale.Mode.of("de"))
    }

    @Test
    fun `the code is what the choice is remembered under`() {
        assertEquals("", AppLocale.Mode.SYSTEM.code)
        assertEquals("zh", AppLocale.Mode.ZH.code)
        assertEquals("en", AppLocale.Mode.EN.code)
    }
}
