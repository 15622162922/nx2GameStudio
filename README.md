<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo-dark.svg">
    <img alt="nx2android" src="docs/images/logo.svg" height="140">
  </picture>
</h1>

Port your own Nintendo Switch games to Android, natively: the game's own arm64 code runs on the device, with the Switch's system services stood in for by a host and NVN graphics replayed on Vulkan. Pick the game's file, click **Port**, and get the game's data pack, which the nx2android app on the phone runs. Bring your own game files and keys; nothing from any game is in this repository.

This is the Android port of [nxnative](https://github.com/seedds/nxnative), which does the same on iPhone and iPad. Supported games: [Games](#games).

![The nx2android app's game library](docs/images/app-library.png)

The app is the whole tool on the phone: it ports the game on the device and plays the pack it makes. The game files it shows are the player's own.

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
4. The port leaves the game's data pack (`<Game>-data/`) in the game folder. The nx2android app on the phone is what plays a game: it ports on the phone itself, and it plays the packs it finds in its output folder.

Pokémon Legends: Arceus and Pokémon Legends: Z-A need the device during the port: their shaders are in Oodle-compressed archives, which the port decodes with the game's own decoder on the device. Have platform-tools (adb) from step 2, and plug in the device with USB debugging on before you click **Port**.

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

The game names and the trademarks in this README identify the games this tool is compatible with. They belong to Nintendo and to the respective publishers and developers. The screenshot is of the app itself, running the player's own game files. This project is not affiliated with or endorsed by them.
