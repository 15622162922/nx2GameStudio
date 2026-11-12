package com.nx.android.domain.environment

/** Starts a Linux guest process with the app's private rootfs and toolchain environment. */
interface GuestProcessLauncher {
    fun guestEnv(): MutableMap<String, String>
    fun guest(args: List<String>, extra: Map<String, String> = emptyMap()): Process
}
