# Rules for Claude in this repository

The skills in `.claude/skills/` (nx2android-dev, switch-android-port) hold the full guide. These rules hold in every session, with or without a skill.

## Game files go only in `.gamedump/`

- Every port, test, check or bundle trial uses `.gamedump/<App>/` in the main checkout (git-ignored; `<App>` is the APK's name, e.g. `HadesII`), also from a worktree. Never make a game, bundle or player folder elsewhere on the PC, and never port into a session's scratchpad (`%TEMP%\claude\...`): `C:\nx2a-test` once kept 263 GB, and old scratchpads 66 GB of ports and pass dumps, that nobody deleted.
- Debug output too big for a few files (pass dumps, frame shots, screen recordings, a port's output) goes in `.gamedump/<App>/` beside the game, and is deleted with it or when the debugging ends.
- Fill it with hard links to the downloaded NSP / NSZ / XCI files (`ln`, same drive, no copy). Never write into the download folders; porter decompresses NSZ and XCZ itself.
- Delete `.gamedump/<App>/` when the work is done (port committed, test app uninstalled). First check that each dump file has 2 or more links (`fsutil hardlink list <file>`); a file with one link is the only copy, so ask first. The PC has little disk space.
- Before ending a session that made a `.gamedump` folder or wrote more than 1 GB anywhere, say which folders are left and why.

## Worktrees

- Always clean up a worktree after its branch is merged into main: remove the worktree and delete the merged branch (`git branch -d`). A session's own worktree goes when the session is archived; say so before ending the session.
- `tools/` (the NDK, the .NET SDK, platform-tools and `prod.keys`) exists only in the main checkout. It is git-ignored, and porter's config points to it by absolute path, so a worktree needs no copy of it. Never make a junction or link to it inside a worktree.
- A link to `tools/` inside a worktree is the main checkout's real folder: a recursive delete of the worktree goes through it and deletes the real files. On 2026-10-31, removing a worktree in Fork deleted the NDK, the .NET SDK and `prod.keys` through a `tools` junction.
- Before you remove a worktree (with git, Fork or any other tool), list its links (`Get-ChildItem -Force <worktree> -Recurse -Attributes ReparsePoint`) and remove each link alone with `cmd /c rmdir <link>` (no `/s`: that removes only the link). Then remove the worktree.

## Releases

- **This line's versions are the upstream base it carries.** `porter/__init__.py` holds that base (`0.MINOR.PATCH` of the upstream nxnative it is forked from), and the release tag is that base with the line's name on it: `v<major>.<minor>-android` — `v0.3-android` for the base 0.3.0. The line's own revisions take a number after it (`v0.3-android.1`); they do not change the version in `porter/__init__.py`. When the line picks up a new upstream base, that version is bumped (`0.4.0` → `v0.4-android`).
- The phone app says the same version: its `versionName` in `android/app/build.gradle.kts` is `0.3-android` (the tag without the `v`), so the update check can tell which release is newer than the app that is running.
- A release checks only the games it adds or fixes. Never port or test the older games again for a release, even after a core change; a break found later is fixed in a later patch release. README.md's Games table: each game's `<sub>` line starts with one tag, the highest version it was tested on.
- Steps: commit `chore(release): version <x.y.z>`, then an annotated tag `v<major>.<minor>-android` on it, push both, then `gh release create v<major>.<minor>-android --verify-tag --notes-file <notes> --repo zFitness/nx2androidX`, with no zip.
- **A release carries the phone app's APK, and nothing else.** `.github/workflows/android-apk.yml` builds it (the release variant, signed with the project's own key) and attaches it as `nx2android-<tag>.apk` when the release is published; watch that run and check that the APK is on the release. The desktop bundles (`release-windows.yml`, `release-macos.yml`) are **not** built on a published release — they are built by hand when they are wanted (Actions → the workflow → Run workflow) and stay as the run's artifact.
- Every `gh` command takes `--repo zFitness/nx2androidX`: `gh` in this checkout defaults to seedds/nxnative, which is not this project.
- The hook `.claude/hooks/release_guard.py` (`.claude/settings.json`) refuses `gh release create|upload|edit` without that `--repo` or with a zip, and a release tag that is not this line's (`v<major>.<minor>-android`, or that with a revision number).
