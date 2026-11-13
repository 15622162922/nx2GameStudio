# Worked example: the review of PR #301

The Chinese release of OCTOPATH TRAVELER 0 (`01001300232F4000`), shirai91/nx2android#301, reviewed 2026-11-13. The reviewer's three blockers and five inline notes, and what answered each.

## What the pull request carried

- the profile of the Chinese release: `game.tsv`, `android.tsv`, `patches.py`, the shader tables (the same 10,324 shaders as the international release, in another order in the archives)
- two host rows for **that build** (`e3e401c2…`): `hle_uemalloc.c` (the FMallocBinned3 object at main+0x68052c8, its vtable at main+0x5b09f38) and `hle_fpslock.c` (t.MaxFPS at main+0x7c768d0, rhi.SyncInterval at main+0x7b35690) — the international release's build ID is a different one, so its behaviour does not change
- the AES provider fix (`uepak.py`: CommonCrypto is a Mac's alone, and the porter runs where the game is) with `tests/test_uepak.py`
- the tests for the Oodle decode's rounds (`tests/test_porter.py`)
- the gamepad theme pack `octopath0` and its `themes.tsv` rows

## The three blockers

| The review said | The fix |
|---|---|
| "the README table loses Xenoblade Chronicles 3: it was the last cell (beside Viractal) and has no row now" | the table is re-flowed from the current main, not edited in place: every cell kept, the new one added next to the international release's, an odd count ending with a row of one game and an empty cell. The check is the **set** of games, not the diff (adding a cell re-pairs every row after it) |
| "two of the new UnrealShaders tests fail on Windows (release-windows.yml runs the test suite)" | the expected stamp was written with a slash (`Content/pakchunk0-Switch.pak\t3\n`), but `nsp.ue_stamp` prints `p.relative_to(d / 'RomFS')` — the platform's own path, a backslash on Windows. The tests now build the expected stamp with `nsp.ue_stamp(p.dirs['v1'])` and write the "another pak" one from it (the size changed, not the path) |
| "the profile registers `v1`, which is profile_guess's placeholder for an update file with no `[v<N>]` tag, not the update's real version" | the placeholder came from `version_of()`, which reads an English `[v<N>]`; the Chinese dump spells it `[升级档v1.0.5]`. The update is 1.0.5, so the real version is **v327680** (the international 1.0.5's): registered in `build`, `package`, `shader_versions`, the `android.tsv` `tested` row and the `patches.py` comment |

## The inline notes

| The review said | The fix |
|---|---|
| "this row uses the international game's image. The port docs ask for a gameplay frame captured on the device for this port" | `docs/images/octopath-traveler-0-chinese.jpg`: a frame captured on the Xiaomi (the Chinese release in town, with the new gamepad on top), cropped to the game's picture and resized to the 1280x720 every image there uses |
| "porter/dumps.py also tries `Cryptodome.Cipher` (the name some system package managers give pycryptodome). The same second try here keeps the two consistent" | `uepak.py` tries `Crypto.Cipher` and then `Cryptodome.Cipher` |
| "on a machine with neither CommonCrypto nor pycryptodome this test errors instead of skipping … the expected value is the AES-256 decryption of the NIST plaintext block … not the NIST ECB-AES256 ciphertext" | the vector test skips without a provider; the comment above the key says what the value is (the NIST ciphertext for that block is f3eed1bd…) |
| "This row puts the new pack on the international release too … the maintainer will decide whether it goes in with this PR or on its own" | left as it is, and the reply says so: dropping it is a one-line change |
| "please rebase onto the current main: it added Fire Emblem Engage and Pokémon Brilliant Diamond since, so the README table and the PORT_CHECKLIST count (now 46 / 103) conflict; the new checklist entry must also raise the total" | rebased onto the upstream's current main (by the time of the rebase the count was 51 / 106); the table re-flowed from it and the count became **52 / 107** — the total as well as the ported one |

## What the reply said

One paragraph per point, what was changed and why it is right (the CNMT's version number, the platform's own path, the device's frame). The point the maintainer left to himself was named as such.

## The result

Four commits, each carrying one thing: the profile, the AES fix, the decode's tests, the theme. The fixes went into the commit they belong to (`git commit --fixup` + `git rebase -i --autosquash`), so the pull request reads as four changes rather than nine.
