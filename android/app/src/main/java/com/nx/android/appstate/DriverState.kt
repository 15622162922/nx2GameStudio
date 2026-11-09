package com.nx.android.appstate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.Driver
import com.nx.android.data.Drivers

/**
 * 玩家装好的 Vulkan 驱动：一份列表，设置页和驱动页都读它，两边就不会不一致。
 * 驱动在 App 自己的目录里（files/drivers/），列表是从那里读出来的。
 */
class DriverState {

    var drivers by mutableStateOf(listOf<Driver>()); private set

    /** 重读 App 自己的目录（装好、删掉之后，或者页面重新出现的时候） */
    fun refresh(context: Context) { drivers = Drivers.list(context) }

    /** 内置的那个驱动自己就位：App 自带的那一个，不是玩家的数据 */
    fun ensureShipped(context: Context) { Drivers.ensureShipped(context) }}
