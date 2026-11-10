# NOTICE: nx_extract

`nx_extract` is the NSP reader of this project: it reads the NCAs and layers
a patch NCA over its base NCA to get the merged RomFS. Its own code is
GPL-3.0-or-later, covered by the repository's [LICENSE](../../../LICENSE).

It depends on **LibHac**, which reimplements parts of the Nintendo Switch
operating system's content formats so that they can be read. LibHac is MIT,
Copyright © 2018-2022 Alex Barney (https://github.com/Thealexbarney/LibHac),
and its license text is in [`LICENSES/LibHac-MIT.txt`](LICENSES/LibHac-MIT.txt).

The library is restored from NuGet (version 0.19.0) when the tool is built,
and the built tool — with LibHac.dll in it — is what the desktop bundle and
the phone app ship. The license text is here because the binary is
redistributed.
