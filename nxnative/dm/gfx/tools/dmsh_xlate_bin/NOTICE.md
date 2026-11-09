# NOTICE: the prebuilt shader translator

This directory holds the **prebuilt** Switch shader translator (`dmsh_xlate`)
and the .NET libraries it runs on. It is a binary distribution, so the license
text of each component travels with it in [`LICENSES/`](LICENSES/).

## What is in here

| File | What it is |
|---|---|
| `dmsh_xlate` | the launcher (a universal binary for macOS) |
| `dmsh_xlate.dll` | the translator itself |
| `dmsh_xlate.deps.json`, `dmsh_xlate.runtimeconfig.json` | its .NET runtime manifest |
| `Ryujinx.Graphics.Shader.dll`, `Ryujinx.Common.dll`, `Spv.Generator.dll` | the shader translation library from Ryujinx (MIT) |
| `Humanizer.dll`, `MsgPack.dll`, `Gommon.dll`, `Microsoft.IO.RecyclableMemoryStream.dll`, `System.CodeDom.dll` | the .NET libraries the Ryujinx projects depend on |
| `<locale>/` | the satellite resource assemblies of those libraries (one directory per locale) |

## Licenses

Every component above is MIT or Apache-2.0, and its license text is in
[`LICENSES/`](LICENSES/):

| Component | License | License text |
|---|---|---|
| Ryujinx (`Ryujinx.Graphics.Shader`, `Ryujinx.Common`) | MIT | `LICENSES/Ryujinx-MIT.txt` |
| Spv.Generator | MIT | `LICENSES/Spv.Generator-MIT.txt` |
| Humanizer | MIT | `LICENSES/Humanizer-MIT.txt` |
| MessagePack for C# | Apache-2.0 | `LICENSES/MessagePack-CSharp-Apache-2.0.txt` |
| Gommon | MIT | `LICENSES/Gommon-LICENSE.txt` |
| Microsoft.IO.RecyclableMemoryStream | MIT | `LICENSES/Microsoft.IO.RecyclableMemoryStream-MIT.txt` |
| System.CodeDom | MIT | `LICENSES/System.CodeDom-MIT.txt` |

The source of the Ryujinx part is in the repository, with the local changes
listed: `nxnative/dm/third_party/ryujinx/` (its `README.md` names every
change). Its own license text is `nxnative/dm/third_party/ryujinx/LICENSE.txt`.

The versions are the ones the build recorded: Ryujinx 1.3.2, Humanizer 3.0.10,
MessagePack 1.0.1, Gommon 2.8.1.2, Microsoft.IO.RecyclableMemoryStream 3.0.1,
System.CodeDom 4.4.0.

## Why the binaries are in the repository

The shader chain pins the exact build of this tool. `dm/gfx/tools/dmsh_chain.lock`
records the fingerprints of `dmsh_xlate.dll`, `Ryujinx.Graphics.Shader.dll`,
`Ryujinx.Common.dll`, the translator's source and the Apple tools beside it,
and `dmsh_chain.py --check-tools` verifies them. A translator rebuilt with
`dotnet publish` has different fingerprints, so the chain refuses it and the
shader libraries cannot be built.

That is why the prebuilt binaries are here: the Mac side of the chain needs
this exact build, and it cannot be reproduced elsewhere. The Android side does
not need it — the porter builds the translator from the source in this
repository, which picks up a fix as soon as it is in the source.

## Note on the debug symbols

The .NET debug symbol file (`dmsh_xlate.pdb`) is not tracked: it holds debug
information only, is not needed to run the tool, is not part of what the
fingerprint check measures, and it embeds the build machine's local paths.
