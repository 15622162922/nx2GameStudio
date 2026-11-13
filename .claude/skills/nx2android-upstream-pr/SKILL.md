---
name: nx2android-upstream-pr
description: How to contribute a port to the upstream engine repository (shirai91/nx2android): the branch and the GitHub fork a pull request needs, what a port's pull request carries (the profile, the host rows for that build, the tool fixes and their tests, the games table row, the device image, the checklist line), the review's recurring demands, and the commands that open, update and answer it. Use whenever the task is to push a port upstream, open or update a pull request against shirai91/nx2android, rebase a pull request onto the upstream main, answer the maintainer's review, or decide what a contribution to upstream has to carry. Bringing a game up on the phone is the nx2android-dev skill's; cutting a release is nx2android-release's.
---

# nx2android: contributing a port upstream

Upstream is **shirai91/nx2android**: the engine repository, where the game profiles and the shared core live and where the games table is kept. This checkout (zFitness/nx2androidX) is the product line: the phone app, the release line, and its own `main` (the upstream mirror, fast-forward only), `android` (product and release) and `next` (integration). A port reaches upstream as a **pull request**; this is that procedure, and the traps that come with it.

The port itself (the profile, the phone, the bring-up) is the **nx2android-dev** skill's ([.claude/skills/nx2android-dev](../nx2android-dev/SKILL.md)); a release is **nx2android-release**'s ([.claude/skills/nx2android-release](../nx2android-release/SKILL.md)). What is here is the part that leaves this checkout.

## The pull request needs a fork of upstream

GitHub wants a fork relation: `zFitness/nx2androidX` is **not** a fork of the upstream (it is the product's own repository), so a pull request against `shirai91/nx2android` needs one:

```bash
gh repo fork shirai91/nx2android --clone=false        # zFitness/nx2android
git remote add fork git@github.com:zFitness/nx2android.git
```

Every `gh` command about the pull request names the repository it acts on: `--repo shirai91/nx2android` for the pull request, `--repo zFitness/nx2android` for the branch's own repository.

## The branch

A contribution is a branch off the **current upstream main**, in a worktree (the main checkout keeps the product line):

```bash
git fetch upstream main                                  # the truth; the local mirror and the fork's main go stale
git worktree add ~/.local/share/opencode/worktree/<slug> -b feat/<slug> upstream/main
```

The local `main` mirror and `zFitness/nx2android`'s own `main` both lag the upstream (the mirror is synced on its own schedule, the hub change's "upstream sync" line). Always `git fetch upstream main` before branching or rebasing, and compare with `git rev-list --left-right --count HEAD...upstream/main`.

## What a port's pull request carries

| Part | Where | Note |
|---|---|---|
| The profile | `nxnative/dm/games/<title ID>/` | `game.tsv`, `android.tsv`, `patches.py`, the shader tables |
| The host rows **for that build** | `nxnative/dm/host/hle_*.c` | per-build tables keyed by the NSO build ID: a regional or other release is **its own binary**, so it needs its own rows (the allocator's object and vtable, the console variables) |
| The tool fixes the port needed | `nxnative/dm/gfx/tools/`, `porter/` | with their tests (`tests/test_*.py`) |
| The games table row | `README.md` | with the device's gameplay frame in `docs/images/<slug>.jpg` (1280x720) |
| The checklist line | `docs/PORT_CHECKLIST.md` | the game ticked, and the `N / M ported` count **with the total raised too** |

A pull request may carry more than the profile — the tool fix the port needed is part of it (the AES provider that only a Mac had, for instance, is what made the phone able to decode an encrypted pak at all).

## The review's recurring demands

The maintainer reads the pull request against the upstream's own rules, and the same points come up every time. The worked example (the Chinese release of OCTOPATH TRAVELER 0, PR #301) is in [references/pr301-review.md](references/pr301-review.md).

- **The games table is re-flowed from the current main.** Every game is kept — the diff is large even for one cell, because adding a cell re-pairs the rows after it, so check the **set** of games and not the diff. An odd number of cells ends with a row of one game and an empty cell (`| ![game](…) | |`).
- **The version is the real one.** `profile_guess` writes `v1` when it cannot read the update's version tag (it looks for an English `[v<N>]` in the file name; a Chinese dump spells it `[升级档v1.0.5]`). Register the update's **real** version (its CNMT's): the display `1.0.5` is `v327680`. It belongs in `build`, `package`, `shader_versions`, the profile's `android.tsv` `tested` row and the `patches.py` comment.
- **The tests run on Windows too.** The CI runs the suite on three platforms: never write a path separator into an expected value (build it with the platform's own, e.g. `nsp.ue_stamp(dir)`), and a test that needs an optional package skips when it is missing instead of erroring.
- **The image is the device's.** A gameplay frame captured on the device, in `docs/images/`, not the publisher's artwork and not a frame from another release.
- **Rebase onto the current main** before asking for a merge, and take the counts in the checklist from that main (the games it added are counted there too).

## Opening and updating it

```bash
git push fork feat/<slug>                                # the branch goes to the fork
gh pr create --repo shirai91/nx2android --base main --head zFitness:feat/<slug> \
    --title "feat(profile): <game> plays on android" --body-file <body>

gh pr view <n> --repo shirai91/nx2android --json reviews,comments,mergeable
gh api repos/shirai91/nx2android/pulls/<n>/comments --jq '.[] | "=== " + .path + ":" + (.line|tostring) + "\n" + .body'
```

After a rebase the branch is rewritten: `git push --force-with-lease fork feat/<slug>`. The pull request follows the branch; its commits are replaced in place.

Answer the review as a comment on the pull request, one paragraph per point, and say what each fix was. A point the maintainer leaves to himself (a row that touches another game's port, for instance) stays as it is and the reply says so.

## The traps

- **A stale mirror makes a wrong "rebase"**: the fork's `main` and the local `main` are both behind the upstream; `git fetch upstream main` is the only truth.
- **The games table's diff lies**: one added cell rewrites the rows after it. Compare the set of game names, not the diff.
- **`v1` in a profile is a placeholder**, not a version: a player's dump named the usual way then lands on a derived profile (a red line and a bigger address region).
- **A regional release is its own build**: its own main build ID, its own host rows, its own title ID, its own games table cell, its own image — and its own checklist line.
- **A force-push needs `--force-with-lease`**: the pull request's branch is shared with the fork.
- **The upstream's CI runs the suite on Windows**: a test that only passes on a Mac breaks the pull request, not the local run.
- **The local mirror is not updated by a pull request**: syncing this checkout's `main` with the upstream is its own line of work (the hub change's "upstream sync" tasks), not a side effect of the contribution.
