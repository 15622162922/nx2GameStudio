# packaging/android-apk — the phone app

An Android app that runs **nx2android on the phone**: the porter turns the Switch game files into an APK, on the
device itself. The app is a shell — the environment (the Linux rootfs, the build tools, the tool's own tree) is
unpacked on the first start, and the porter runs inside it; the screen is Android's own.

```
┌─ the app (com.nx.android) ─────────────────────────────────────┐
│  Setup: prod.keys, output folder          Port: game file       │
│  [准备环境] [下载 .NET SDK]                [开始移植]            │
│  the log: the porter's own output                             │
└────────────────────────────────────────────────────────────────┘
        │  the guest programs are Linux binaries
        ▼
files/rootfs   the nixport payload (the patched glibc, Python 3.13, coreutils)
files/tool     porter/ + nxnative/ (the pipeline and the engine) + the Python modules
files/toolchain  Google's NDK (its sysroot and compiler-rt) + an ARM64 LLVM + glslc
files/nx2a/toolchains/dotnet   the .NET SDK (the extractor and the shader translator are C#)
```

## Building it

```bash
bash packaging/android-apk/build_apk.sh          # the payload and a debug APK
bash packaging/android-apk/build_apk.sh release  # a release APK

# only the app, without the payload (the phone already has the environment unpacked):
cd android && ./gradlew :app:assembleDebug -PnoPayload     # 900 KB, 10 s
```

The payload is what makes the APK big (700 MB); the environment itself lives in the app's own folder, so an update
that leaves the payload out keeps it: the app sees that the environment is there and only runs its check.

The script needs the `gh` CLI (the rootfs comes from the private supply channel
`NixPort/nixport-rootfs@payload-latest`), Python 3.11+ and the Android SDK. The APK lands in
`android/app/build/outputs/apk/`.

## The parts

| File | What it does |
|---|---|
| `build_apk.sh` | the whole build: the payload, the build tools, the assets, the APK |
| `fetch_debs.py` | Debian's arm64 packages (glslc) with their dependencies, checked against the index's SHA-256 |
| `build_toolchain.py` | the build tools: the NDK's sysroot and compiler-rt (from Google's NDK), an ARM64 clang (from the community build) and glslc |
| `build_assets.py` | the app's assets: the rootfs, the tool's tree, the Python modules, the build tools, the .NET SDK |
| `patch_interp.py` | sets the ELF interpreter of the programs the porter starts itself (the compiler, the .NET SDK) |

## Why the interpreter is rewritten

Android has no `/lib/ld-linux-aarch64.so.1`, and the guest is not a chroot (nixport runs the programs as they are,
without proot). The rootfs the payload carries was built for another app's path, and the compiler and the .NET SDK
are ordinary Linux programs. So the app rewrites their interpreter path to its own rootfs before it starts them;
the Python of the rootfs is started through the loader directly and needs no rewriting.

## What the phone does and does not do

- **Makes the APK and the data pack** on the phone, into the output folder (`/sdcard/Switch/Ports` by default).
- **Installs the APK** with the system installer (the app hands it over; Android asks the user).
- **Copies the data pack** into the game's own folder (`/sdcard/Android/data/<package>/files`), where the game reads it.
- **No adb**: Google ships platform-tools for Linux as x86-64 only, so they cannot run on an ARM64 phone. The tool
  cannot install on a device by itself. The games whose shaders sit in Oodle-compressed archives (Pokémon Legends) are
  ported on the phone all the same: the phone is the ARM64 machine that runs the game's own decoder, so the port decodes
  them here, with no adb and no second device.
- **The .NET SDK is in the APK** (221 MB, unpacked about 800 MB): the app unpacks it on the first start, so the
  first run needs no network. Build it with `--no-dotnet` to leave it out: the Setup screen then downloads it
  (the porter's own path, checked against Microsoft's SHA-512).
