<h1 align="center">
  <img alt="nx2android" src="docs/images/logo-badge.svg" height="140">
</h1>

> **English**. 中文文档：[README.zh.md](README.zh.md)

Port your own Nintendo Switch games to Android, natively: the game's own arm64 code runs on the device, with the Switch's system services stood in for by a host and NVN graphics replayed on Vulkan. Pick the game's file, click **Port**, and get the game's data pack, which the nx2androidX app on the phone runs. Bring your own game files and keys; nothing from any game is in this repository.

This repository is nx2androidX, built on nx2android — the PC tool that ports a Switch game and packages the APK. nx2androidX moves that to the phone: give it the game's files and it compiles and runs them on the device itself, with no computer in the loop. The games that play on Android are listed upstream: [Games](#games).

The app is the whole tool on the phone: it ports the game on the device and plays the pack it makes. The game files it shows are the player's own.

---

## What changed on this Android branch

This branch closed the loop on "playing it on the phone": the app went from a player to the whole porting tool.

### The app is now the whole tool

Porting used to happen on the computer: install the tools, pick the file, click Port, copy the data pack back to the phone. Now the phone starts the port itself — pick a game file on the **Builds** tab, click **Port**, the port runs in the background (the notification bar shows "移植进度"), the data pack lands in the output folder, and the game is playable from the library.

![Four screens of the app: starting a port on the Builds tab, a game entry in the library, the in-game drawer's frame generation, and the Settings tab](docs/images/app-screens.png)

*Top left: starting a port on the Builds tab; top right: a game entry in the library; bottom left: the in-game drawer's frame generation; bottom right: the Settings tab (language, environment, keys, output folder, Vulkan drivers).*

The library got simpler too: it used to take a folder to be the library, and the scan behind it was recursive — one root directory was a long freeze. Now **the library only holds the games registered on the Builds tab**, the whole folder scan is gone, and an empty library shows one line of guidance instead of buttons.

### What this release added

| Feature | What it is |
|---|---|
| **Porting on the phone** | Pick a file on the Builds tab and the port starts; it runs in the background and the game is playable when it is done — no computer in the loop |
| **A glibc rootfs** | The app carries a glibc rootfs of its own, and the porting tools are Linux binaries that execute on the device itself: no proot, no root, no compatibility layer in between |
| **Renamed nx2androidX** | The phone app carries the new name; the releases live in `zFitness/nx2androidX` |
| **Onboarding fixed** | It can be finished now, the progress is not lost, and the keys picked in step three are the ones the port uses |
| **The game is a page of the app** | Ending the game falls back to the library instead of the home screen, and the recents hold one entry |
| **The app's own language** | Chinese and English, switchable at any time (the game's own language stays in the library's drawer, one game at a time) |
| **Saves you can share** | An export becomes a package handed to the system share sheet; an import takes one in from anywhere; the save it replaces is backed up first |
| **An in-game drawer** | The ☰ menu button opens it: frame generation and display & controls in the pause menu |
| **Frame generation** | Available while the game runs; the multiplier and the target frame rate take effect at once |
| **Vulkan driver management** | Download (Turnip builds), import from a file or remove, in the Settings tab; one Turnip build ships inside the app |
| **Update check on start** | The release page is checked quietly; a newer version says so on the home page, and the Settings tab can check again on demand |
| **Shareable logs** | The library's drawer shares the game's runtime log; the Settings tab clears it |
| **APK 460 MB → 272 MB** | The .NET on the phone went from the SDK to the runtime: the two C# tools are pre-built, so the phone only runs them, never builds them |

### Onboarding

The first launch walks four steps: storage access → environment → keys → done. The environment (rootfs, the build tools and the .NET runtime, about 700 MB) is unpacked once; later updates of the app only carry the new small tools, and that step does not repeat. Skipped steps are not fatal either: they can be made up in the Settings at any time, and the library's entry says what is missing.

### Saves you can share

The save actions in the library panel became a backup-and-share affair: an export writes the save as a package into the downloads folder and hands it to the system share sheet; an import takes a package in from anywhere — another phone, a cloud drive, a console's save tools. An import replaces the save the game has, so the app exports that one first (a folder ending `-before-import`), and the panel lists every package and backup, to restore, share or delete.

An import is not taken on trust: a package carrying another game's title ID is refused, a path pointing out of the folder is refused, and the unpacked size is capped.

### The in-game drawer and frame generation

The ☰ menu button opens the in-game drawer, with two tabs: **frame generation** and **display & controls**.

Frame generation uses Lossless.dll: the frames-per-game multiplier, the target frame rate and the optical flow quality are all adjustable, and the switch and the multiplier take effect at once; an updated DLL is copied to the private folder by itself. Lossless Scaling's resources are neither bundled nor downloaded.

The display & controls tab has the virtual gamepad: the layout editor (drag a control, pinch to resize, snap to grid), the pad's opacity, rumble, the frame rate counter and the docked mode.

### Vulkan drivers on the phone

The drivers are the player's, not the pack's: the **Vulkan 驱动** section of the Settings tab can download them (Mesa's Turnip builds, from their own addresses), import one from a file, and remove one. One Turnip build — the Adreno 8xx one — ships inside the app: it unpacks itself into the app's own folder when the app starts (no network, no click) and is marked as the built-in one, so it is not the player's to remove.

The driver a game runs with is picked in the library's drawer (**Vulkan 驱动**), the device's own driver first. The choice is kept in the data pack's `env.txt`, so it lasts, and it takes effect the next time the game starts.

---

## Games

The games that play on Android are listed upstream, with the nx2android version each was tested on and the frame
rate it runs at: [Games](https://github.com/shirai91/nx2android#games). The games ported so far, and the ones
next, are in [docs/PORT_CHECKLIST.md](docs/PORT_CHECKLIST.md).

A game needs a device whose GPU reads BC-compressed textures (Adreno, Xclipse; not Mali or PowerVR).
Tested on a Lenovo Legion Y700 (Snapdragon 8 Elite, Adreno 830).

## For players: port a game without programming

1. Download `nx2android-<version>-windows-x64.zip` (Windows) or `nx2android-<version>-macos-arm64.zip` (Mac with Apple silicon) from [Releases](https://github.com/zFitness/nx2androidX/releases/latest), unzip it and start **nx2android** (on a Mac, see [packaging/INSTALL.md](packaging/INSTALL.md#1-start-the-app) first).
2. **Setup** tab: click **Download missing tools** (the Android NDK, the .NET SDK and platform-tools, about 1 GB, from Google and Microsoft), then set your **prod.keys**. Every line turns green.
3. **Port** tab: choose the folder with the game's NSP, XCI, NSZ or XCZ files (base game, update, DLC) and click **Port**.
4. The port leaves the game's data pack (`<Game>-data/`) in the game folder. The nx2androidX app on the phone is what plays a game: it ports on the phone itself, and it plays the packs it finds in its output folder.

The app on the phone also looks for a newer release: when it starts, it checks the release page quietly, and if there is a newer version it says so on the home page. The **Settings** tab has a **Check for updates** entry that checks again on demand. The app downloads nothing by itself — the notice leads to the release page, where the APK is.

Pokémon Legends: Arceus and Pokémon Legends: Z-A keep their shaders in Oodle-compressed archives, which only the game's own decoder can open. On the phone the port decodes them here — the phone is the ARM64 machine that runs the game's own code — so nothing else is needed. On the desktop tool the same decode runs on a device you plug in: have platform-tools (adb) from step 2, and connect the device with USB debugging on before you click **Port**.

A game played by touch: tick **Touch game** on the Port tab (`--touch`). The on-screen gamepad then starts hidden and every touch but one on the menu button goes to the game; the pause menu shows the gamepad again.

A game slowed by its draw calls in busy scenes: tick **Multithread replay** on the Port tab (`--mt-replay`). The game's Vulkan commands are then recorded on a thread of their own. It stays on until you port the game again without it.

To arrange the on-screen controls, open the pause menu's **Gamepad** tab and choose **Edit layout**: drag a control to move it, pinch or use **− / +** to resize it. **Save** keeps the layout for the next launch and **Cancel** discards your edits. **Snap to grid** (off at first, and remembered) shows a square grid and snaps each control's center to it as you drag.

The game opens from the app's library page: it shows the game, where its files are, and the language and the Vulkan driver to run it with. The main action starts the game, and the choices are kept in the data pack's `env.txt`, so they last.

To run a game with a Vulkan driver the device's own does not (Mesa's Turnip on Adreno GPUs), put the driver libraries (.so) in a folder and set **Vulkan driver folder** on the Port tab (`--vulkan-driver-dir`): the pack carries them all and the app lets the player pick one, the device's own driver being the first choice.

On the phone the drivers are the player's, not the pack's: the app's **Settings** tab has a **Vulkan 驱动** section, where a driver can be downloaded (Mesa's Turnip builds, from their own addresses), imported from a file, and removed. One Turnip build — the Adreno 8xx one — ships inside the app: it unpacks itself into the app's own folder when the app starts (no network, no click) and is marked as the built-in one, so it is not the player's to remove. The game is run with one of them in the library's drawer, under **Vulkan 驱动**; the choice is kept in the data pack's `env.txt`, so it lasts, and it takes effect the next time the game starts.

A game that crashes with "AllocateAddressRegion … refused (0x1803)" in its log needs more address space: port it again with a larger **Extra address space** on the Port tab (`--alias-headroom` on the command line). [packaging/INSTALL.md](packaging/INSTALL.md#when-something-goes-wrong) says more.

The step-by-step guide is [packaging/INSTALL.md](packaging/INSTALL.md). You need your own game files and `prod.keys`, dumped from your own Switch; the tool never ships or downloads either. NSZ and XCZ files are decompressed during the port (into the build folder's `tmp/`, deleted once extracted).

## Saves

The game's pause menu (Game tab) has **Export save** and **Import save**: an export is a folder in `Android/data/<package>/files/saves/` on the device, the save's files as a console's save tools copy them, so a save from a console imports the same way. The app's **Saves** tab moves them between the device and the PC.

An import replaces the save the game has; the app exports that one first on the device (a folder ending `-before-import`). Uninstalling the app deletes its saves and its exports: copy them to the PC first.

## For developers

Running from the repository, the command line, the repository layout, building the bundle, tests and commit messages: [docs/DEVELOPMENT_GUIDE.md](docs/DEVELOPMENT_GUIDE.md). How it is put together: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Keys and game files

nx2android decrypts your game files with your `prod.keys` while it ports them, as Switch emulators do. This repository and its releases contain no keys, no game files, no firmware and nothing extracted from them, and the tool never downloads either. Dump both from your own Switch, and don't share them or the data packs built from them.

The game profiles under `nxnative/dm/games/` hold interoperation metadata only: the hashes that identify a game's shaders, the offsets inside a module, and patch stubs of a few bytes of machine code that make a particular game build run. They are the information the host needs to work with a game, not the game itself.

Nintendo's takedown notices so far have gone to whoever hosts a project (13 Switch emulator repositories on GitHub in February 2026), not to players. Whether decrypting your own games for your own use is allowed depends on where you live.

## License

The code this repository owns — the porter, the desktop app, the phone app and the packaging — is MIT: [LICENSE](LICENSE). nxnative, the engine it is built on, is MIT as well ([nxnative/LICENSE](nxnative/LICENSE)), and the third-party code in the tree keeps its own license; every component is listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

One exception: the phone app's frame generation ([nxnative/dm/android/lsfg/](nxnative/dm/android/lsfg/)) is GPL-3.0-or-later, so the host the app builds is a combined work under that license unless it is built without it. What that means for the app, and how to get an MIT build, is in [android/NOTICE.md](android/NOTICE.md).

## Acknowledgements

This project is one branch of a family, and it stands on other people's work:

- **[nx2android](https://github.com/shirai91/nx2android)** — the project this repository is built on: it ports a game on the PC and packages the APK; this repository is its phone side.
- **[seedds/nxnative](https://github.com/seedds/nxnative)** — the engine underneath it: the host that stands in for the console's services, the NVN-on-Vulkan replay, the shader translator, and the game profiles that many of these ports were brought up against. MIT licensed; our fork of it lives in `nxnative/`.
- **The nxnative contributors** — for the engine, the tooling and the profiles.
- **[NX2Apple](https://github.com/LNPANDY/NX2Apple)** — the Apple route of the same engine: the macOS builder and the shared iPhone/iPad library. Thanks for keeping the line alive on Apple devices.
- **[BananaNative](https://github.com/Raphix91/BananaNative)** — the branch that takes the engine to Android and runs a few Switch 2 games natively on a phone. Thanks for taking it further than we did.
- **[Mesa](https://www.mesa3d.org/)** — for Turnip, the Vulkan driver the app can download for Adreno GPUs.
- **Lossless Scaling** — for Lossless.dll, the frame generator the app can use; its resources are neither bundled here nor downloaded by us.
- **[Ryujinx](https://github.com/Ryujinx/Ryujinx)** — for the shader translator and the NVN declarations the graphics path is built on.
- **Everyone who filed an issue, sent a patch, wrote a game profile or translated the docs** — this branch stands on their work too.

Everything shipped in the tree keeps its own license; the index is [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Trademarks

The game names and the trademarks in this README identify the games this tool is compatible with. They belong to Nintendo and to the respective publishers and developers. The screenshot is of the app itself, running the player's own game files. This project is not affiliated with or endorsed by them.
