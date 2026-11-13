package com.nx.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 设置页的分区清单是写死的（spec: app-settings「分区清单固定」）：日志搬去了构建页，
 * 这里就只剩环境、存储、驱动、画面、关于五个。
 */
class SettingsSectionTest {

    @Test
    fun theSettingsPageHasFiveSections() {
        assertEquals(
            listOf("ENVIRONMENT", "STORAGE", "DRIVERS", "DISPLAY", "ABOUT"),
            SettingsSection.entries.map { it.name },
        )
    }
}
