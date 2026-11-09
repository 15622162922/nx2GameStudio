# Development guide

How to run nx2android from the repository, build the player's bundle and send changes. Players need none of this: the [README](../README.md) and [packaging/INSTALL.md](../packaging/INSTALL.md) cover the app. How the pieces fit together: [ARCHITECTURE.md](ARCHITECTURE.md).

## Setup from the repository

Install [Python](https://www.python.org) 3.11 or later and `python -m pip install -r requirements.txt`, then `python -m porter tools --download` (or install them yourself):
- [Android NDK r27c](https://developer.android.com/ndk/downloads) (only the NDK: no Android Studio, SDK or JDK)
- [.NET SDK 10](https://dotnet.microsoft.com/download)
- Android [platform-tools](https://developer.android.com/tools/releases/platform-tools) (adb), for the legacy path's install and the save manager
- your own `prod.keys`, dumped from your Switch

## Port a game

The app:

```
python -m gui
```

Check the **Setup** tab, choose the game's file (the base game NSP, XCI, NSZ or XCZ; its update and DLC next to it are taken too) on the **Port** tab, and click **Port**.

Or the command line:

```
python -m porter doctor
python -m porter port "D:\Games\NEO" -k "D:\keys\prod.keys"
```

The data pack (`NeoTheWorldEndsWithYou-data/`) appears in the game folder (`-o` picks another); the phone app runs the game from it. The first port takes a while (extraction, rewriting the game's code, translating its shaders); later ones reuse what has not changed. `python -m porter -h` lists the commands.

### Saves

`porter saves` does what the app's **Saves** tab does (see the README's [Saves](../README.md#saves)):

```
python -m porter saves list
python -m porter saves export "D:\Backups\Celeste"
python -m porter saves import "D:\Backups\Celeste\20261030-141200"
```

How the export folders are laid out: [ARCHITECTURE.md](ARCHITECTURE.md#saves).

## Layout

- `porter/`: the port pipeline (`python -m porter`).
- `gui/`: the desktop app (`python -m gui`).
- `nxnative/`: our fork of nxnative (see [nxnative](#nxnative)), with the Android runtime in `nxnative/dm/android/` and the game profiles in `nxnative/dm/games/`.
- `packaging/`: the player's bundle (`python packaging/build_bundle.py`).
- `devtools/`: scripts for bringing up a game on a connected device (`devtools/device.json` names the device: `{"serial": "...", "package": "..."}`).
- `probes/`, `RESEARCH.md`: the feasibility study and the first on-device probes.
- `.githooks/`: the commit-msg hook (see [Commit messages](#commit-messages)).

## Adding a game

Each game is a profile in `nxnative/dm/games/<title ID>/` with an `android.tsv`: [ARCHITECTURE.md](ARCHITECTURE.md#adding-a-game). A finished port updates, in the profile's commit, the games table in the [README](../README.md#games) (its row, with a gameplay frame captured on the device in `docs/images/` and the tag of the nx2android version it was tested on) and [PORT_CHECKLIST.md](PORT_CHECKLIST.md).

## Releases and version numbers

The version is `0.MINOR.PATCH`. A release that only adds games and fixes bugs raises the patch version; a release with any other core change (a new feature or a change in behaviour) raises the minor version. The major version stays 0 while the project is early. A release checks only the games it adds or fixes, not the older ones; a break in an older game is fixed by a later patch release. The version is in `porter/__init__.py`, raised by a commit `chore(release): version <x.y.z>`.

## Building the bundle

```
python -m pip install -r requirements.txt pyinstaller
python packaging/build_bootloader.py      (Windows only)
python packaging/build_bundle.py
```

On Windows, `build_bootloader.py` compiles PyInstaller's launcher on this PC (it needs the Microsoft C++ Build Tools), because a few antivirus engines flag the prebuilt one every PyInstaller app shares. Run it again after installing or upgrading PyInstaller.

`dist/nx2android/` and `dist/nx2android-<version>-<os>-<cpu>.zip` (`windows-x64`, `macos-arm64`): the app, the committed `nxnative/` and the install guide; no toolchains, keys or game files. Publishing a release builds the macOS and Windows zips on GitHub's runners and attaches them (`.github/workflows/release-macos.yml`, `release-windows.yml`; `--clobber` replaces a zip of the same name, so do not upload them by hand). Run either workflow by hand (Actions → Run workflow) for a test build of a branch, kept as the run's artifact.

## Tests

```
python -m unittest discover tests
```

## Commit messages

[Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/), with commitlint's `config-conventional` types and limits:

```
<type>[(<scope>)][!]: <description>

[body, after one blank line]

[footers: Co-Authored-By: …, BREAKING CHANGE: <what breaks>]
```

- type: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`, `style` or `revert`, lower case.
- scope: optional and free, the part touched: `vk`, `android`, `porter`, `gui`, `profile`, `status`, `release`…
- description: no full stop at the end; it may start with a capital (game and API names). Header, body and footer lines at most 100 characters (a lone URL may run longer).
- `!` before the `:` or a `BREAKING CHANGE:` footer marks a breaking change.

```
fix(vk): a transfer read where a run of rasterizer-discard draws ends (XC3 grass flicker)
feat(profile): Tales of Eternia Remastered playable on update 2.0.0 (v65536)
docs(status): bundle 8372959 ported Tales of Eternia Remastered from scratch
chore(release): version 0.1.1
```

`.githooks/commit-msg` refuses other messages (merges, reverts and `fixup!` commits from git pass). Turn it on once per clone:

```
git config core.hooksPath .githooks
```

The commits before 2026-10-28 predate the rule.

## nxnative

`nxnative/` started as a squashed git subtree of [seedds/nxnative](https://github.com/seedds/nxnative) (upstream 6034fca, 2026-10-24). Since 2026-10-29 it is our own fork: no `git subtree pull`, cherry-pick or merge from upstream, and the repository has no `upstream` remote (removed 2026-10-31): `origin` is the only one. Upstream's profiles are still a good reference when bringing up a game it has; read them on GitHub (`https://github.com/seedds/nxnative/tree/main/dm/games/<title ID>`) and write our own.

## The phone app

`android/` is an Android app that runs the porter **on the phone**: the game files are turned into a data pack on the
device, and the app plays the game from it. It is a shell — the environment (the Linux rootfs from the nixport payload, the build tools, the tool's own
tree) is unpacked on the first start, and the porter runs inside it; the screen is Android's own.

```bash
bash packaging/android-apk/build_apk.sh          # the payload and a debug APK
bash packaging/android-apk/build_apk.sh release  # a release APK
```

The build needs the `gh` CLI (the rootfs comes from the private supply channel), Python 3.11+ and the Android SDK;
[packaging/android-apk/README.md](../packaging/android-apk/README.md) has the parts and what the phone can and cannot
do (no adb: the APK is installed with the system installer and the data pack is copied by the app).

Two changes in the tool itself make it run on an ARM64 Linux host:

- `nxnative/dm/android/build.py` builds the game's host with the phone's own compiler (`NX2A_ARM_HOST`): Google's
  NDK ships host tools for x86-64 only, so the compiler is the ARM64 LLVM the app carries and the NDK gives the
  Android sysroot and the compiler-rt.
- `porter/toolchain.py` finds an NDK that ships with the program (`NX2A_NDK`), and `porter/fetch.py` does not
  fetch adb on an ARM64 Linux host (Google ships platform-tools for Linux as x86-64 only).
