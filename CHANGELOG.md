# Changelog

The releases of nx2androidX, newest first. Each one carries the phone app's
APK, signed with the project's own key, so a build installs over the one
before it. The pre-releases (`-alphaNN`) are published as GitHub
pre-releases: they do not become the repository's "Latest" release, so the
players on the released version are not pushed onto an alpha.

The release page has the APK and the full notes: <https://github.com/zFitness/nx2androidX/releases>.

## Unreleased

### Changed

- **The whole repository is now GPL-3.0-or-later.** It was MIT, with the frame generation as the one GPL component in the tree. The MIT terms let anyone take the code and ship a closed app from it; the GPL terms require a derivative to be distributed under the same license, with its source and the notices kept. The license follows the code that was taken, not what the build linked: the frame generation can be left out of a build (`-PnxLsfg=false`), and the result is still GPL. The third-party components keep their own licenses, the upstream engine's copyright notice is kept, and the full license text travels with the app and the desktop bundle.

## v0.3.0-alpha04 - 2026-11-10

### Added

- **The app's data pack is built in the app's own folder** (`files/ports`), not on the shared storage. The extracted game files were copied over FUSE, which is slow (50-100 MB/s) and fragile; the copy now runs at the device's write speed (200-230 MB/s measured) and no media scanner shares the files. The log of 2026-11-09 shows three ports of Tears of the Kingdom dying in that stage: the system killed the app, a half-made pack left behind made the next port fail, and a cancel interrupted a read. The game reads its files from there as well.
- **The free-and-open-source notice.** It is shown once, when the setup finishes, and on the first start of an app that had already done the setup. It says the app is free and open source, that selling it is not allowed, and that a player who paid for it can ask the seller for a refund. "View the repo" opens the fork's page on GitHub.
- **Tapping the version in the Settings opens the repository on GitHub.** The underline says the row can be tapped; a device without a browser has no other way there, so the app stays quiet.
- **An experimental port says so.** The data pack's manifest carries the profile's own status: a game that was never brought up on Android is written with the status experimental, and the library shows it as one - the player is told the port may stop at a path that was never walked.
- **A developer setup guide** (`docs/GETTING_STARTED.md`).

### Fixed

- **A game's text entry froze the game and then killed it.** The host told the app "the drawer steps aside" with a JNI call made on the game's own thread, and that call blocked there: the log showed the entry opening and nothing after it, and the process died of SIGSEGV with no backtrace and no tombstone. The notice goes on a thread of its own now - the game's thread must never wait for the app.
- **A crash on Android was silent.** The crash handlers were only installed by the desktop host, so a SIGSEGV killed the game silently: the kernel had nowhere to run a handler, the process died of the signal and the log stayed empty. They are installed now, and every thread that can fault gets the signal alternate stack.
- **The log said the wrong reason.** The line read "audio suspended (background / interruption)" every time the game was held, but the game is held for three reasons: the app is in the background, the pause menu is open, or a text entry is open. A game's name entry froze with the keyboard never coming up, and the log blamed the background. The host names the reason now.
- **The app checks the all-files permission before a port starts.** Without it the game file cannot be read, and the port died in the middle of a Python stack trace.
- **The cleanup of a half-made pack is tolerant now.** The folder may be gone already (another process removed it), and a directory that vanishes while it is being removed must not stop the port - that was the FileNotFoundError that ended with exit 139.

## v0.3.0-alpha03 - 2026-11-10

### Added

- **The Oodle shader archives are decoded on the phone itself.** The two Pokémon Legends games keep their shaders in Oodle-compressed archives, which have to be unpacked with the game's own decoder. That decoder is an ARM64 program, and the phone is the ARM64 machine it runs on - so the app decodes them there, with no adb and no second device in the loop. Where the port runs, the decoding follows it: a computer with adb decodes on the connected device, the phone app decodes on itself, and a computer with neither says so instead of failing later.

### Changed

- **The README was rewritten**, in English and Chinese: the phone app first, what changed on this branch, and the games that play on Android.

## v0.3.0-alpha02 - 2026-11-10

### Added

- **The versions say what they are.** The tags carry the whole upstream base and this line's own stage on it: `v0.3.0-alpha02` is the base 0.3.0's second alpha, and `v0.3.0` is its released version. The app says the same version, so its update check can tell which release is newer than the build that is running.

### Fixed

- **The port's space check counts the right files.** It measured every archive in the folder, so a folder holding more than one game was sized as if all of it were going to be built, and a port that would have fitted was refused. It counts the picked game's files now: its base, its updates, its DLC.

## v0.3-android - 2026-11-09

The first release of this line. The phone app is the whole tool: it ports a game on the device itself and plays the data pack it makes.

### Added

- **Ports on the device.** The app carries the whole pipeline (the Linux rootfs, the build tools, the .NET runtime) and turns a game's own files into a data pack on the phone, then plays it.
- **The game is a page of the app.** Starting a game no longer opens a second task: the game sits on top of the library page, so "exit the game" falls back to the library.
- **The setup's keys are the ones a port uses.** One path: what the guided setup asks for is what the port starts with.
- **The app speaks the player's language.** Chinese or English, switchable in the settings and effective at once.
- **Saves can be shared.** A save can be packed and sent to someone else, and a package from elsewhere imported - with the old save kept as a backup.
- **Vulkan drivers.** The app ships one driver, downloads more, imports the player's own, and the game picks the one it runs with.
- **The in-game drawer.** The menu button opens it: frame generation and display & controls in the pause menu.
- **The update check on start.** The release page is checked quietly; a newer version says so on the home page, and the Settings tab can check again on demand.
