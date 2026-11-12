# Porting a game to Android

How to bring a Switch game to a phone, and what a finished port looks like in this repository.

This is the main contribution path. It needs no computer: **the app ports the game on the device itself.**
Read [CONTRIBUTING.md](../CONTRIBUTING.md) first for the rules that hold for every contribution (no game
files in the repository, real-device evidence, English, Conventional Commits).

## What you need

| | |
|---|---|
| A phone | Android 8.0+ (API 26). The GPU must read BC-compressed textures: **Adreno** (Snapdragon) or **Xclipse** (Exynos 2200 and later). Mali and PowerVR do not. |
| Space | About 1.5 GB for the app and its environment, plus your own game files and the packs they make. |
| The game's own files | NSP, XCI, NSZ or XCZ, dumped from your own Switch. The tool never downloads them. |
| Your own `prod.keys` | Dumped from your own Switch. The tool needs them to read the game files; they never leave your machine and never enter a pull request. |

Keep the game files out of the repository. The convention here is `.gamedump/<App>/` inside the checkout
(git-ignored, `<App>` being the app's name, e.g. `HadesII`); any folder outside the repository works too.
The build folders, the surveys and the packs land next to the game files.

## Two ways to run a port

### On the phone (no computer needed)

1. Install the app from [Releases](https://github.com/zFitness/nx2androidX/releases/latest).
2. First start: the onboarding walks the first step (storage access, free space and the battery setting the
   long background jobs need) → environment → keys → done. The environment step
   unpacks about 700 MB once.
3. **Builds** tab → *Choose a game file* → pick the base game (the updates and the DLC beside it are taken
   along) → *Start a port*. The port runs in the background; the notification bar shows the progress.
4. **Library** tab → the pack is there → *Run*.

The port's own options sit on the *Builds* tab as well: *Touch game* (the on-screen gamepad starts hidden),
*60 fps* and *Experimental port* (for a game that is not adapted yet).

The log is worth having: the game's drawer → the log entry → share.

### On a computer (faster, and the logs are easier to read)

```
git clone https://github.com/zFitness/nx2androidX
cd nx2androidX
python3 -m pip install -r requirements.txt
python3 -m porter tools --download     # NDK, .NET SDK, platform-tools (about 1 GB)
python3 -m porter doctor                # everything it lists should be green
python3 -m porter port "<game folder>" -k <path to prod.keys>
```

The stages keep their output and are skipped when nothing changed, so only the changed stage runs again.
`--debug` builds a debug host (debug information and the `DM_CTL` control file the device scripts drive).
More in [GETTING_STARTED.md](GETTING_STARTED.md).

---

## Step 1 — Can it be ported at all?

Some games cannot be, and finding out early saves an evening:

```
python3 -m porter inspect "<game folder>"
```

It prints the title ID, the base game, the updates and the DLC it found, and whether the game is supported
on Android. The port **refuses**:

- a game whose own `main` renders with **Vulkan or OpenGL** — the Android host replays NVN only;
- a game whose shaders can only be produced by **running the game on a computer** (a GLSLC capture, an
  Unreal or Oodle-packed shader archive, a PlatinumGames archive).

If the game is refused, the project wants to know: open a **Port request** issue and paste the output. It
goes on the checklist with the reason, so the next person does not spend the evening either.

## Step 2 — The first port

A game that has no profile yet is ported as *experimental* first. Nothing in the repository changes yet.

On the phone: tick **Experimental port** on the *Builds* tab and start the port.

On a computer:

```
python3 -m porter port "<game folder>" --experimental --debug -k <path to prod.keys>
```

What happens:

- The survey is written to `<game folder>/<title ID>_survey/survey.md`: the modules and their build IDs, the
  SDK, the engine and graphics-API markers, the RomFS containers, the rewrite sites and the imports
  compared with the host's hooks. **Read it before going further.**
- A provisional profile, guessed from the game files, lands in
  `<game folder>/<title ID>_build/profile/<title ID>/` with an `android.tsv` of status `experimental`. The
  tools find it through `DM_GAMES_DIR`; it is outside the repository.
- The stage logs are in `<title ID>_build/logs/`.

If the game already has a profile in this repository (its line in [PORT_CHECKLIST.md](PORT_CHECKLIST.md)
says *profile here*), the port uses it, and its constants are already proven.

## Step 3 — Bring it up

Iterate: read `log.txt`, find the first `[RED]` line or the crash, fix it, port again. Only the stages whose
inputs changed run again.

| Symptom | Where it is fixed |
|---|---|
| A stub named in a `[RED]` line (an SDK or NVN function the host lacks) | The core, by its semantics: `nxnative/dm/host/hle_*.c`, `nxnative/dm/gfx/nvn/hle_nvn.c` |
| `program shader … is not in the bundle`, or shaders that do not translate | The shader readers (`nxnative/dm/gfx/tools/shader_split.py`, `unityfs.py`, …) or the translator (`nxnative/dm/gfx/tools/dmsh_xlate`) |
| Wrong rendering, flicker, wrong colours | The Vulkan backend `nxnative/dm/android/gfx_vk_*.c` |
| A crash in the game's own code, on one build | A general rule first; a per-build cut in the profile's `patches.py` only when no general rule fits |
| Memory or address-space layout | The `game.tsv` keys, checked against the survey |

A change that only one game needs belongs in that game's profile; a change that is true of the console,
of NVN, of Vulkan or of the engine belongs in the core, as a rule that holds for every game
([ARCHITECTURE.md](ARCHITECTURE.md)).

Play past the title into real gameplay before calling a game more than *boots*: the first two "soft-locks"
of a game are often the game's own answer to an input it did not expect, not a bug.

## Step 4 — Move the profile into the repository

When the game boots on the device:

1. Copy the profile files (not the rest of the build folder) into `nxnative/dm/games/<title ID>/`:
   `game.tsv`, `shader_map.tsv`, `shader_map.tsv.conflicts.tsv`, `shader_facts.tsv`, `shader_pairs.tsv`,
   `pso_manifest.tsv`, `bf_want.tsv`, and `patches.py` if one was written.
2. Check `game.tsv` by hand against the survey — the survey's values are guesses where it says so — and
   scrub any local path out of its comments.
3. Write `android.tsv`: a comment header first (the game, the engine, the SDK, what was tested on which
   device and when, the known gaps), then the rows. Only these keys exist:

   | Key | Meaning |
   |---|---|
   | `status` | `playable` (tested end to end on a device), `wip` (plays, but parts are missing), `boots` (starts, not playable yet) or `experimental` |
   | `tested` | `<version> <device>`: the game's version and the device it was checked on |
   | `package` | the app's package name (default `org.nx2a.t<title ID in lowercase>`) |
   | `orientation` | `landscape` (default) or `any` |
   | `requires` | the device features the game needs: `bcn` (BC texture compression) |
   | `setting` | `<name>=<value>`: the host's defaults on first launch (`rumble`, `docked`, `fps`) |
   | `env` | `KEY=VALUE`: a host switch the game needs on Android |
   | `overlay` | `<tablet\|phone> <control> <x> <y>`: moves one on-screen control |

   The full list and its wording: [ARCHITECTURE.md](ARCHITECTURE.md#game-profiles-on-android).
   `android.tsv` is not part of the shader keys: editing it re-translates nothing.
4. Port again **without** `--experimental`. It must reuse or rebuild cleanly from the repository profile.

## Step 5 — Playable, and the pull request

Before you open it, the game has to be played on the device:

- the title, the menus, real gameplay, a save, the pause menu, the on-screen gamepad, a controller if the
  game uses one, text entry if the game asks for names;
- a few minutes of steady frame rate (the `[perf]` lines) and no new `[RED]` lines;
- a screenshot and the frame rate, for the pull request.

Then:

- Set `status playable` and `tested <version> <device>` in `android.tsv`, and write what was tested in its
  comment header. The header is the record other people read first.
- **Tick the game in [PORT_CHECKLIST.md](PORT_CHECKLIST.md)**: `- [x] <name> · `<title ID>`` with the
  *profile here* / *upstream profile* note dropped (add the line in alphabetical order if the game is not
  listed), and recount the `N / M ported` line. The checklist goes in the same commit as the profile: a
  port is not done until it shows the game.
- Run the tests: `python3 -m unittest discover tests`.
- Open the pull request against `android` with the evidence: the device, the game's version, the frame
  rate, a screenshot or a log.

Your name goes on the game's checklist line and in the profile's header, and into the release notes.

## Step 6 — Clean up

The device and the disk are small. Once the port is merged and the test app is uninstalled, delete the
game's folder. Check each dump file's link count first (`ls -l <file>` on macOS — the second column): two
links or more means the downloaded copy remains; one link means that file is the only copy, so stop and ask
before deleting it.

## Where to read next

| Document | What is in it |
|---|---|
| [nxnative/dm/games/README.md](../nxnative/dm/games/README.md) | every `game.tsv` key, and how a profile is built |
| [ARCHITECTURE.md](ARCHITECTURE.md) | the layers, the port stages, the Android profile keys |
| [nxnative/docs/agents/](../nxnative/docs/agents/) | the bring-up catalogue: extraction, the survey, graphics, debugging (written for the Apple route; the failure catalogue and the survey method apply here) |
| [.claude/skills/nx2android-dev/](../.claude/skills/nx2android-dev/SKILL.md) | the profile workflow and the core-change rules, in detail |
| [.claude/skills/switch-android-port/](../.claude/skills/switch-android-port/SKILL.md) | the device playbooks: flicker, crashes, stutter, how to measure |
