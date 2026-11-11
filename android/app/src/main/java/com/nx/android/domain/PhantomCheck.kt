package com.nx.android.domain

/**
 * Android's limit on the child processes of an app, as a check for the environment checklist.
 *
 * Android 12 (API 31) and newer keep the app's child processes in a list and stop them above a limit
 * (32 by default, counted for the whole system, not per app). The porter is one of those child
 * processes, and so are the compiler and the shader translator below it: when the list overflows, the
 * oldest one goes first, and that is the porter. The player sees the port die with no error at all.
 *
 * Two settings turn the limit off, and both are writable only by the shell user:
 * - `activity_manager/max_phantom_processes` in device_config (the limit itself);
 * - `settings_enable_monitor_phantom_procs` in the global settings (whether the limit is watched at
 *   all - Android 12L and newer; Android 14's Developer options writes exactly this one).
 *
 * This file holds the decision, free of Android, so the tests run on the JVM: the values are read
 * elsewhere (data/PhantomFix.kt) and handed to [status].
 */
object PhantomCheck {

    /** The limit's state */
    enum class Status {
        /** The system has no such limit (before Android 12) */
        NOT_NEEDED,

        /** The limit is switched off */
        OFF,

        /** The limit is on: the app's child processes can be stopped */
        ON,

        /** The limit's state cannot be read on this system */
        UNKNOWN,
    }

    /** The limit value that means "no limit" (the setting's own maximum) */
    private const val OFF_LIMIT = 1024

    /**
     * The limit's state, from what the system says.
     *
     * @param sdk the Android API level
     * @param deviceConfig the value of `device_config get activity_manager max_phantom_processes`,
     *   empty when it could not be read
     * @param globalSetting the value of the global setting `settings_enable_monitor_phantom_procs`,
     *   empty when it is not set
     * @param flagProperty the value of the feature flag property of the same name, empty when unset
     */
    fun status(sdk: Int, deviceConfig: String, globalSetting: String, flagProperty: String): Status {
        if (sdk < 31) return Status.NOT_NEEDED   // Android 12 is the first one with the limit
        val max = deviceConfig.trim().toIntOrNull()
        if (max != null && max >= OFF_LIMIT) return Status.OFF
        if (sdk > 31) {
            // Android reads the global setting first, then the feature flag property that Developer
            // options' "Disable child process restrictions" writes. Unset means the limit is on.
            val v = globalSetting.trim().ifEmpty { flagProperty.trim() }.lowercase()
            return if (v == "false" || v == "0") Status.OFF else Status.ON
        }
        // Android 12: apps may not read device_config, so the state is only known when it was read.
        return if (max != null) Status.ON else Status.UNKNOWN
    }

    /**
     * The checklist row about the limit: its level and which of the app's own texts say it.
     *
     * The limit being on is a hint, not a failure: the port can start either way, and the player
     * decides whether to switch it off.
     */
    data class Row(val level: EnvCheck.Level, val detailKey: Detail, val fixable: Boolean)

    /** Which text the row shows */
    enum class Detail {
        /** The system has no such limit */
        NONE,

        /** The limit is switched off */
        OFF,

        /** The limit is on */
        ON,

        /** The state cannot be read */
        UNKNOWN,
    }

    /** The row for a state */
    fun row(status: Status): Row = when (status) {
        Status.NOT_NEEDED -> Row(EnvCheck.Level.OK, Detail.NONE, false)
        Status.OFF -> Row(EnvCheck.Level.OK, Detail.OFF, false)
        Status.ON -> Row(EnvCheck.Level.HINT, Detail.ON, true)
        Status.UNKNOWN -> Row(EnvCheck.Level.HINT, Detail.UNKNOWN, true)
    }
}
