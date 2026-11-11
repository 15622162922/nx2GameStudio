<!--
  Thanks for the pull request. Two things a reviewer needs from you: the evidence, and the scope.
  Keep this file's questions answered — an unchecked box is fine when it does not apply, but a
  silent one is not.
-->

## What this changes

<!-- One or two sentences: what the change does, and for whom. -->

## Type of change

- [ ] A game port: a new game, or a game fixed (`nxnative/dm/games/<title ID>/`)
- [ ] The shared core: the runtime, the Vulkan backend, the host, the rewriter, the shader tools
- [ ] The app (`android/`) or the porter (`porter/`, `gui/`)
- [ ] The documentation or a translation
- [ ] The build, the packaging or the release

## The evidence

- [ ] **A port change was tested on a real device.** Reasoning is not verification: the log, the frame
      rate and the screenshot are what count.
- Device, and the game's version:
- Frame rate, and where it drops:
- Screenshot or log (attach it, or link it):

<!-- A change to the shared core: which games it was measured on, and how the others were checked. -->

- [ ] A change to the shared core holds for every game, not only for the one it was found with. If it
      cannot, it belongs in that game's profile.

## The paperwork

- [ ] `docs/PORT_CHECKLIST.md` is updated in this commit (the game's line ticked, the `N / M ported` line
      recounted).
- [ ] The profile's `android.tsv` carries the status, the version and the device it was tested on, and its
      comment header says what was tested.
- [ ] The commit message follows [Conventional Commits](https://www.conventionalcommits.org), in English,
      in lowercase: `feat(profile): …`, `fix(vk): …`, `docs(status): …`.
- [ ] `python3 -m unittest discover tests` passes.
- [ ] No game file, no key, no data pack and no game artwork is in this pull request.
- [ ] The change is licensed GPL-3.0-or-later, the terms this repository's code is under.

<!-- Anything a reviewer should know before reading the diff: a crash you could not fix, a part you left
     out, a decision you were unsure about. -->
