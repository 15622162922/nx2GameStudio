package com.nx.android.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build

internal object AppProcess {
    fun isMainProcess(packageName: String, processName: String): Boolean =
        packageName == processName

    fun isMainProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            ActivityManager.RunningAppProcessInfo().let { info ->
                ActivityManager.getMyMemoryState(info)
                info.processName ?: context.packageName
            }
        }
        return isMainProcess(context.packageName, processName)
    }
}
