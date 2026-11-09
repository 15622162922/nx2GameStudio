# NOTICE: the phone app's license

The nx2android phone app (the APK) is a combination of this project's own
code and of third-party components. This notice says which, and what the
app's license is.

## The default build: GPL-3.0-or-later

By default the app's native host links the LSFG frame generation code in
`nxnative/dm/android/lsfg/`, which is a port of the frame generation of the
Eden emulator, which builds on lsfg-vk. Both are GPL-3.0-or-later, and the
port carries that license:

- License: `nxnative/dm/android/lsfg/LICENSE.md`
- Full license text: `nxnative/dm/android/lsfg/COPYING`

Linking that code makes the app a combined work, so **the APK as distributed
is licensed under GPL-3.0-or-later**. The app's asset tree carries the
component's complete source and the full license text, so the source and the
license are with the binary, as GPL-3.0 requires.

The app's own code outside that component is MIT (see the repository's
[LICENSE](../LICENSE)), as is the rest of the tree.

## The build without the frame generation: MIT

Building the app with `-PnxLsfg=false` leaves the LSFG code out of the host
binary. The app then contains no copyleft component and is distributed under
the repository's MIT license. The frame generation feature reports that it is
not in that build.

The switch reaches the host build in `nxnative/dm/android/build.py`
(`--no-lsfg`), which the porter runs when it compiles the host for a port.

## What the app ships besides its own code

- **Third-party components and their licenses**: [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md),
  the index of everything the app redistributes. The MIT components shipped as
  binaries carry their license text next to them, in
  `nxnative/dm/gfx/tools/dmsh_xlate_bin/NOTICE.md` and its `LICENSES/` folder.
- **The LSFG source and license**: `nxnative/dm/android/lsfg/`, in the asset
  tree, with its `COPYING`.
- **Python modules** (lz4, capstone, pillow, fonttools, pyyaml, pycryptodome,
  backports.zstd) and **the .NET runtime**, fetched at build time into the
  app's payload.
- **No game content**: the app ships no game files, no keys, no firmware and
  nothing extracted from them. It ports the player's own game files, which the
  player supplies.

## Lossless Scaling's `Lossless.dll`

The frame generation reads the shaders from the player's own copy of
Lossless Scaling's `Lossless.dll` at run time. That file is proprietary, it is
not in this repository, not in any bundle and not in the APK. Nothing of it is
redistributed here.
