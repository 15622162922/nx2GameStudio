#!/usr/bin/env python3
"""Build nx_extract for the phone (linux-arm64) and put it in the app's assets.

nx_extract is the C# tool that reads the NCAs and merges the patches. The phone's .NET SDK cannot build it:
its globalization mode (no ICU in the rootfs) makes the SDK itself crash, and a restore needs the network
(the LibHac package). So the tool is built here, for linux-arm64, and shipped in the APK: the phone only
runs it (the .NET runtime, which works with the invariant mode).

    python3 build_nx_extract.py [--out <dir>] [--sdk <dotnet>]

The output is what `dotnet publish -r linux-arm64 --self-contained false` makes: the apphost, the assembly,
LibHac.dll and the runtime config. The phone's porter uses it as it is (NX2A_NX_EXTRACT).
"""
import argparse, os, shutil, subprocess, sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
DEFAULT_OUT = Path(__file__).resolve().parent / '.cache' / 'assets' / 'tool' / 'tools' / 'nx_extract'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default=str(DEFAULT_OUT))
    ap.add_argument('--sdk', default=os.environ.get('DOTNET', str(Path.home() / '.dotnet' / 'dotnet')))
    ap.add_argument('--runtime', default='linux-arm64')
    a = ap.parse_args()
    out = Path(a.out)
    dotnet = a.sdk
    if not Path(dotnet).is_file():
        print(f'>> the .NET SDK is missing: {dotnet} (install it with dot.net, or set DOTNET=/path/to/dotnet)')
        return 1
    project = REPO / 'nxnative' / 'dm' / 'tools' / 'nx_extract'
    print(f'>> nx_extract for {a.runtime} (the phone ships it)')
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True, exist_ok=True)
    r = subprocess.run([dotnet, 'publish', str(project), '-c', 'Release', '-r', a.runtime,
                        '--self-contained', 'false', '-o', str(out), '-v', 'quiet', '-nologo'],
                       capture_output=True, text=True)
    if r.returncode:
        print(r.stdout[-4000:])
        print(r.stderr[-4000:])
        return r.returncode
    for f in sorted(out.iterdir()):
        print(f'   {f.name:40} {f.stat().st_size / 1024:8.0f} KB')
    exe = out / ('nx_extract.exe' if os.name == 'nt' else 'nx_extract')
    if not exe.exists():
        print(f'>> no executable in {out}')
        return 1
    # the apphost is a Linux program: it has to be executable (the phone cannot exec a 0644 file)
    exe.chmod(0o755)
    # the phone has no ICU: the runtime needs the invariant mode, and .NET 9+ wants it declared here
    # (with the environment variable alone the runtime stops the program)
    rc = out / 'nx_extract.runtimeconfig.json'
    if rc.exists():
        import json
        d = json.loads(rc.read_text())
        props = d.setdefault('runtimeOptions', {}).setdefault('configProperties', {})
        props['System.Globalization.Invariant'] = True
        rc.write_text(json.dumps(d, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
