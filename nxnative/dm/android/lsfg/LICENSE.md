# License of this folder: GPL-3.0-or-later

The files here are a C port of the Lossless Scaling frame generation (LSFG) of [Eden](https://git.eden-emu.dev/eden-emu/eden)
(`src/video_core/frame_gen/{lossless_dll,lsfg_translate}.cpp`, `src/video_core/renderer_vulkan/present/{frame_gen_pacer,lsfg_*}.cpp`), which builds on
[lsfg-vk](https://github.com/PancakeTAS/lsfg-vk). Both are licensed GPL-3.0-or-later, and so is this folder: each file keeps its SPDX copyright lines
(Eden Emulator Project 2026, lsfg-vk 2025). The full license text is [COPYING](COPYING).

The rest of nxnative is under the same terms ([LICENSE](../../../LICENSE)), and so is the rest of the repository ([LICENSE](../../../../LICENSE)). The Android host links these files by
default (`-DNX_LSFG=1`, set by `nxnative/dm/android/build.py`): the host it builds — `libdmplay.so` for the app, `libdmhost.so` for the per-game APK — is then under
GPL-3.0-or-later as a whole; see [android/NOTICE.md](../../../../android/NOTICE.md). Built without them (`build.py --no-lsfg`, which the porter takes as
`--no-lsfg` and the app as the Gradle property `-PnxLsfg=false`), none of this folder is in the host, and the host is under the same terms: leaving the frame generation out does not change the license of the code the build was made from.

The phone app ships this folder's source in its assets, so the source that the GPL work is built from travels with the app that builds it.

The shaders are not here and never are: they are read at run time from the user's own copy of `Lossless.dll` (Lossless Scaling, proprietary), which is not part of
this repository or of any APK.

| File | From Eden |
|---|---|
| `lsfg_dll.c` | `frame_gen/lossless_dll.cpp`, `frame_gen/lsfg_translate.cpp` (no translated-module cache) |
| `lsfg_pacer.c` | `present/frame_gen_pacer.cpp` |
| `lsfg_chain.c` | `present/lsfg_common.cpp`, `lsfg_chain.cpp`, `lsfg_mipmaps.cpp`, `lsfg_alpha.cpp`, `lsfg_beta.cpp`, `lsfg_gamma.cpp`, `lsfg_delta.cpp`, `lsfg_generate.cpp` |
| `lsfg.h` | the headers of those |
