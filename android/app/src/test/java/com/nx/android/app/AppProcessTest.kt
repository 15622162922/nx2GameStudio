package com.nx.android.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessTest {
    @Test
    fun packageProcessIsTheMainProcess() {
        assertTrue(AppProcess.isMainProcess("com.nx.android", "com.nx.android"))
    }

    @Test
    fun namedGameProcessIsNotTheMainProcess() {
        assertFalse(AppProcess.isMainProcess("com.nx.android", "com.nx.android:play"))
    }
}
