# Getting started

How to set up a machine to develop nx2android on, and the working loops once it is set up: the desktop tool
from the repository, and the phone app this repository ships. What the pieces are and how they fit:
[ARCHITECTURE.md](ARCHITECTURE.md). The deeper working agreements — the player's bundle, the nxnative fork,
licenses, commit messages in full: [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md).

## What you need

| Tool | For | Notes |
|---|---|---|
| [Python](https://www.python.org) 3.11+ | everything: the porter, the desktop app, the engine's own tools | `python -m pip install -r requirements.txt` |
| `prod.keys` | ports | dumped from your own Switch; never committed, never shared, only their name is printed |
| Android NDK r27c, .NET SDK 10, platform-tools (adb) | ports and the phone app's payload | `python -m porter tools --download` fetches all three (about 1 GB) from Google and Microsoft |
| A test device | trying a port | Android 8.0+, a GPU that reads BC textures (Adreno / Xclipse); the tested one is a Lenovo Legion Y700 |
| JDK 17 and the Android SDK | building the APK | any JDK 17 works (CI uses Temurin); no Android Studio needed, just the SDK |
| The `gh` CLI | building the APK | the rootfs payload comes from the private release `NixPort/nixport-rootfs@payload-latest`, so `gh` must be signed in with read access to it |

## Set up

```
git clone https://github.com/zFitness/nx2androidX
cd nx2androidX
python3 -m pip install -r requirements.txt
python3 -m porter tools --download
python3 -m porter doctor
```

`doctor` checks the toolchains, the free disk space and `prod.keys`; everything it lists should be green
(the desktop app's **Setup** tab runs the same check and sets the keys' path).

- The downloaded tools land in `toolchains/` under the settings folder (`~/Library/Application Support/nx2android`
  on a Mac, `%APPDATA%\nx2android` on Windows, `~/.config/nx2android` elsewhere; `NX2A_HOME` overrides the folder),
  and the finders search there first.
- **Game files go only in `.gamedump/<App>/`** inside the main checkout (git-ignored; `<App>` is the APK's name,
  `HadesII` and so on), as **hard links** to the downloads — never copies, never a folder elsewhere. Ports, dumps
  and recordings land there too, and the folder is deleted when the work is done (check each dump file's link
  count first: one link means it is the only copy).
- **The test device** is named in `devtools/device.json` (not committed):
  `{"serial": "<adb serial>", "package": "com.nx.android"}`. `NX2A_DEVICE` / `NX2A_PACKAGE` override it; wireless
  adb works. The devtools scripts read it, so no script needs the serial on its command line.
- Printing non-ASCII output: `PYTHONUTF8=1` (the porter sets it for its own tools).

## Loop A: the desktop tool (the fast inner loop)

```
python3 -m porter port "$HOME/Games/Game.nsp" -k "$HOME/keys/prod.keys"
```

or `python3 -m gui` and click **Port**. The stages write into `<game folder>/<title ID>_build/`, and the data
pack (`<App>-data/`) lands beside the game's files (or with `-o` wherever you point it). Every slow stage keys
its inputs and is skipped when nothing changed, and the host build is incremental, so a second port after a
core change takes seconds, not minutes.

- A debug host (`--debug`) carries debug information and the `DM_CTL` control file the device scripts drive;
  a release host does not read it.
- Rebuild only the host: `nxnative/dm/android/build.py --release` with the NDK on the path (the port stage
  does this itself).
- The Pokémon Legends games decode their Oodle-packed shaders through the game's own decoder, which the desktop
  port runs on a device you plug in: have adb from the tools step and connect the device with USB debugging
  before porting. On the phone no second device is needed.

## Loop B: the phone app (the product)

```
bash packaging/android-apk/build_apk.sh           # the payload and a debug APK
bash packaging/android-apk/build_apk.sh release   # a release APK

# the app only, without the payload (the phone keeps the environment it has already unpacked):
cd android && ./gradlew :app:assembleDebug -PnoPayload    # about 900 KB, seconds
```

The full build fetches the rootfs payload (needs `gh`), fetches and assembles the build tools, stages the
tool's tree, pre-builds the two C# tools (`nx_extract`, the shader translator — needs the .NET SDK, or
`DOTNET=/path/to/dotnet`), builds the Vulkan driver loader, and runs Gradle. Downloads are kept in
`packaging/android-apk/.cache/` (not committed). The parts and what the phone can and cannot do:
[packaging/android-apk/README.md](../packaging/android-apk/README.md).

- Install with adb: `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`.
- First launch walks the four onboarding steps and unpacks the environment (rootfs, build tools, .NET runtime,
  about 700 MB). Builds with `-PnoPayload` leave it alone.
- The porter itself runs on the phone: the **Builds** tab picks a game file and starts a port; the data packs
  land in the output folder (`/sdcard/Switch/Ports` by default) and the library runs them.
- Host and shader changes are rebuilt by the port on the phone (its own ARM64 compiler; `NX2A_ARM_HOST`), so
  the app is self-sufficient — but that loop is slower than Loop A. Most core work is faster on the desktop;
  the phone runs the finished pack.

## Tests

```
python3 -m unittest discover tests
```

The suite includes `tests/arch_rules.py`, which enforces [ARCHITECTURE.md](ARCHITECTURE.md)'s layer rules (no
game IDs in core code, profiles hold data only, the GUI only drives `python -m porter`), and
`tests/test_app_text.py`, which keeps the app's English and Chinese strings tables and their sources in step.
Run it before committing.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org) — `fix(vk): …`, `feat(profile): …`,
`docs(status): …` — checked by `.githooks/commit-msg`; turn the hooks on once per clone with
`git config core.hooksPath .githooks`. The types, scopes and limits:
[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md#commit-messages). Commit only when the change is asked for.

## Releases

The version is the upstream base this line carries: `porter/__init__.py` holds it (`0.3.0`), and the app's
`versionName` in `android/app/build.gradle.kts` is the release tag without the `v` (`0.3.0-alpha03`).

1. Commit `chore(release): version <x.y.z>`.
2. Tag the release `v<base>-alphaNN` and push the commit and the tag.
3. `gh release create v<base>-alphaNN --verify-tag --prerelease --notes-file <notes> --repo zFitness/nx2androidX`.

A release carries **only the APK** — no zip. The pre-release flag keeps it off the repository's "Latest", so
players on the released version are not pushed onto an alpha; the app's update check reads "Latest". Every
`gh` command needs `--repo zFitness/nx2androidX`, and the `release_guard` hook (`.claude/hooks/`,
`.claude/settings.json`) refuses release commands that break these rules. A release re-tests only the games it
adds or fixes, not the older ones. The desktop bundles are not released: their workflows
(`release-windows.yml`, `release-macos.yml`) run by hand and leave the zip as the run's artifact.

## Where to read next

- [ARCHITECTURE.md](ARCHITECTURE.md) — the layers and their rules, the port's stages, the phone app.
- [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md) — the player's bundle, the nxnative fork rules, licenses.
- [packaging/android-apk/README.md](../packaging/android-apk/README.md) — the APK's parts, why the interpreter
  of the guest programs is rewritten.
- [nxnative/docs/agents/](../nxnative/docs/agents/) — upstream's engine manuals: extraction, the survey, bring-up,
  graphics, debugging (written for Apple, the host and rewriter parts apply here).
- [.claude/skills/nx2android-dev](../.claude/skills/nx2android-dev/SKILL.md) and
  [.claude/skills/switch-android-port](../.claude/skills/switch-android-port/SKILL.md) — the bring-up and
  device-debugging playbooks (profile or core, the device loop, the render-bug and performance playbooks).
  They are written for coding agents, and they are the most complete write-ups of how a game gets brought up —
  useful reading for a person too.
