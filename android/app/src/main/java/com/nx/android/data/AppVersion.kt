package com.nx.android.data

import android.content.Context

/** The app's own version number (the versionName): the fork's writing, 0.3.0-alpha02 */
object AppVersion {

    fun of(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
