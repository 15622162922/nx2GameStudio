#!/usr/bin/env python3
"""Make the .NET SDK on the phone work without ICU.

The rootfs has no ICU, so .NET can only run in the invariant globalization mode. From .NET 9 on, the
environment variable DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1 alone is not enough: the runtime stops the
program (a FailFast in GlobalizationMode's static constructor). The mode has to be declared in the
runtimeconfig of every program that runs — the SDK's own tools included, or an MSBuild worker dies and
the build ends with "Build FAILED. 0 Warning(s) 0 Error(s)" and no reason at all.

    python3 fix_globalization.py <the .NET SDK folder>

Every runtimeconfig.json below the folder gets the declaration. The file that is already there is left
as it is (the SDK's own runtimeconfigs are written by the SDK).
"""
import json
import sys
from pathlib import Path


def fix(path: Path) -> bool:
    """Adds the declaration to a runtimeconfig; True when the file changed"""
    try:
        d = json.loads(path.read_text())
    except (OSError, ValueError):
        return False
    props = d.setdefault('runtimeOptions', {}).setdefault('configProperties', {})
    if props.get('System.Globalization.Invariant') is True:
        return False
    props['System.Globalization.Invariant'] = True
    path.write_text(json.dumps(d, indent=2))
    return True


def main() -> int:
    if len(sys.argv) < 2:
        print('usage: fix_globalization.py <the .NET SDK folder>')
        return 2
    sdk = Path(sys.argv[1])
    if not sdk.is_dir():
        print(f'no such folder: {sdk}')
        return 1
    n = 0
    for p in sdk.rglob('*.runtimeconfig.json'):
        if fix(p):
            n += 1
    print(f'{n} runtimeconfig(s) fixed in {sdk}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
