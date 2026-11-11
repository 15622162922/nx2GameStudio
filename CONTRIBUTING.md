# Contributing to nx2androidX

Thank you for looking. This project runs Switch games on Android: the game's own code runs on the phone,
the console's system services are stood in for by a host, and the graphics are replayed on Vulkan.

You do not need to be an engine programmer to help. There are three ways in, and the first one needs no
code at all.

| You want to | Path | What it takes |
|---|---|---|
| Say that a game works for you, or report one that does not | [Path A](#path-a--report-a-game-or-a-problem) | a phone, a few minutes |
| Bring a game that does not run yet to Android | [Path B](#path-b--port-a-game-to-android) | a phone, the game's own files, patience |
| Change the app, the engine, the porter or the docs | [Path C](#path-c--code-docs-and-translations) | a dev setup ([docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)) |

**New here? Start with Path A.** A report that a game runs at 60 fps on your device is worth as much as a
patch, and it is the first thing the project needs: it is what the game's entry in the port checklist is
based on.

---

## Rules that hold for every contribution

1. **No game files, no keys, no data packs.** This repository holds none of them and neither should your pull
   request. Your `prod.keys` and your `.nsp` / `.xci` files stay on your machine. See
   [Keys and game files](README.md#keys-and-game-files) in the README for why.
2. **A port change is verified on a real device.** A log line, a screenshot and a frame rate. Reasoning and
   inference are not verification: "this should work" is not a test result. If you cannot test on a device,
   say so in the pull request — an untested change is still welcome, it is just reviewed differently.
3. **English everywhere.** Code, comments, log output, documentation and commit messages. A commit message
   that is not English is not accepted.
4. **Conventional Commits**, checked by the repository's hook: `feat(profile): …`, `fix(vk): …`,
   `docs(status): …`. Turn the hooks on once per clone with `git config core.hooksPath .githooks`.
5. **A core change is a general rule.** Something that is true of the console, of NVN, of Vulkan or of the
   engine belongs in the core; something true of one game belongs in that game's profile
   ([docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)). A per-game branch in the code is never the answer.
6. **Your contribution is licensed GPL-3.0-or-later**, the terms the code of this repository is under
   ([LICENSE](LICENSE)). Third-party code keeps its own license: do not paste it into the tree
   ([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) lists what is in there).
7. **Do not add game content.** No shaders, no archives, no extracted assets, no artwork of the games.

---

## Path A — Report a game or a problem

Two kinds of report, both welcome, neither needs a patch:

- **A game works.** Open a *Game report* issue (the link is in the issue chooser). It asks for the game, the
  title ID, your device, the version you played and the frame rate you measured. If the game is not in the
  checklist yet, your report is what puts it there.
- **A game does not work.** Open a *Port request* issue with what happens: black screen, crash, wrong
  colours, a game that stops at a loading screen. The log the app can share (the game's drawer → the log
  entry → share) is the most useful thing you can attach. Redact anything that belongs to your keys or your
  game files.

What happens next: the report is checked against the games that already work, the game's checklist line is
updated, and **your name is recorded next to the game** — in the checklist line, in the profile's comment
header and in the release notes.

Questions that fit no template go to [Discussions](https://github.com/zFitness/nx2androidX/discussions):
*Show and tell* for your footage, *Q&A* for questions and for getting unstuck on a port, *Ideas* for what the
app should do next.

---

## Path B — Port a game to Android

This is the project's main contribution, and it needs no computer: **the app ports the game on the phone
itself.** Pick the game file on the *Builds* tab, tap *Start a port*, and the data pack lands in the output
folder.

The step-by-step guide, with the commands, the failure catalogue and what a finished port looks like:
**[docs/PORTING_GUIDE.md](docs/PORTING_GUIDE.md)**. Read it before you start — it saves an evening.

What you need:

- An Android 8.0+ phone whose GPU reads BC-compressed textures: **Adreno** (Snapdragon) or **Xclipse**
  (Exynos 2200 and later). Mali and PowerVR do not.
- About 1.5 GB free, plus the game's own files.
- The game's own files and your own `prod.keys`, dumped from your own Switch. Keep them out of the
  repository (`.gamedump/<App>/` inside the checkout is git-ignored and is the convention here, or any
  folder outside the repository).

What a finished port contains:

| File | What it is |
|---|---|
| `nxnative/dm/games/<title ID>/game.tsv` | the game's constants (upstream's format, documented in [nxnative/dm/games/README.md](nxnative/dm/games/README.md)) |
| `nxnative/dm/games/<title ID>/shader_*.tsv`, `pso_manifest.tsv`, `bf_want.tsv` | the shader tables the translator reads |
| `nxnative/dm/games/<title ID>/android.tsv` | the Android side: the status, the version and device it was tested on, the package name, the orientation, the device features it needs ([docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#game-profiles-on-android)) |
| `docs/PORT_CHECKLIST.md` | the game's line, ticked, with your handle |

**Some games cannot be ported**, and it is better to know early: a game that renders with Vulkan or OpenGL
of its own, or one whose shaders can only be produced by running the game on a computer, is refused by the
port. The guide says how to find out in one attempt, before you invest an evening.

---

## Path C — Code, docs and translations

The repository in one map:

| Path | What lives there |
|---|---|
| `android/` | the phone app: Gradle, Kotlin, the library, the settings, the pause menu |
| `nxnative/dm/android/` | the Android runtime: the Vulkan backend, the host, the input, the overlay, the pause menu |
| `nxnative/dm/games/<title ID>/` | the game profiles: data only, never code |
| `porter/` | the port pipeline (Python): the stages, the extraction, the shader split, the data pack |
| `gui/` | the desktop shell around the same pipeline |
| `packaging/` | the desktop bundle and `android-apk/`, which builds the APK |
| `docs/` | the documentation you are reading |

Start here: [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md) (the setup and the two working loops),
[docs/DEVELOPMENT_GUIDE.md](docs/DEVELOPMENT_GUIDE.md) (the repository layout, the tests, the commit
messages, the releases), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (the layers and the rules).

Before you commit:

```
python3 -m unittest discover tests
```

The suite includes `tests/arch_rules.py`, which enforces the layer rules (no game IDs or names in core code,
profiles hold data only, the GUI only drives `python -m porter`). A pull request that breaks it is not
ready.

Two playbooks are written for coding agents but read well for a person too, and they are the most complete
write-ups of how a game is brought up and how a device bug is chased:
[.claude/skills/nx2android-dev](.claude/skills/nx2android-dev/SKILL.md) and
[.claude/skills/switch-android-port](.claude/skills/switch-android-port/SKILL.md).

Translations are welcome: the app's English and Chinese strings live in the app, and the docs have a Chinese
README ([README.zh.md](README.zh.md)). Keep the two languages in step when you touch one of them.

---

## The pull request

1. Fork the repository and branch off the default branch (**`android`**).
2. Make the change, commit it with a Conventional Commit message.
3. Open the pull request against `android` and fill in the checklist that GitHub shows you.

What a reviewer looks at:

- **The evidence.** For a port: the device, the game's version, the frame rate, a screenshot or a log. A
  change to the core: the games it was measured on, and the evidence that it did not break the others.
- **The scope.** A change that touches the shared core has to hold for every game. If it only fits one
  game, it belongs in that game's profile.
- **The commit message.** Conventional Commits, English, lowercase, one change per commit.

Reviews aim to answer within two days. A pull request that needs work gets a comment saying what is missing
and why — a change of direction is explained, not just refused. If a pull request is quiet for a week, say
something in it; nobody is waiting for the other one to go quiet first.

---

## Code of conduct

Be decent to each other and to the people whose devices and games are involved. Assume good faith, keep
discussions about the work, and no harassment of any kind. This applies to the repository, the discussions
and the issue tracker. A maintainer can remove a comment or block an account that does not follow it.

## License

The code this repository owns and maintains is GPL-3.0-or-later ([LICENSE](LICENSE)); your contributions to
it are under the same terms. Third-party components keep their own licenses
([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). The game files, the keys and the game data stay with
their owners and never enter this repository.
