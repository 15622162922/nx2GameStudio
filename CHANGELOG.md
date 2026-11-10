# Changelog

The releases of nx2androidX, newest first. Each one carries the phone app's
APK, signed with the project's own key, so a build installs over the one
before it. The pre-releases (`-alphaNN`) are published as GitHub
pre-releases: they do not become the repository's "Latest" release, so the
players on the released version are not pushed onto an alpha.

The release page has the APK and the full notes: <https://github.com/zFitness/nx2androidX/releases>.

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
