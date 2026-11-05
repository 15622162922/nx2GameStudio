#!/usr/bin/env python3
"""Build the C# tools for the phone (linux-arm64) and put them in the app's assets.

nx_extract is the C# tool that reads the NCAs and merges the patches, and dmsh_xlate the shader translator
(the games' Maxwell shaders to SPIR-V). The phone's .NET SDK cannot build either: its globalization mode (no ICU
in the rootfs) makes the SDK itself crash, a restore needs the network (the LibHac package, the translator's own
packages), and reading the NuGet settings never ends. So both are built here, for linux-arm64, and shipped in the
APK: the phone only runs them (the .NET runtime, which works with the invariant mode).

    python3 build_nx_extract.py [--out <dir>] [--sdk <dotnet>]

The output is what `dotnet publish -r linux-arm64 --self-contained false` makes: the apphost, the assembly,
LibHac.dll and the runtime config. The phone's porter uses nx_extract as it is (NX2A_NX_EXTRACT) and the
translator as it is (the tool tree's tools/dmsh_xlate, which the porter's shader stage looks for).
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
    # nx_extract (the extractor) and the shader translator (dmsh_xlate): the phone runs both, it builds neither
    for name, project in (('nx_extract', REPO / 'nxnative' / 'dm' / 'tools' / 'nx_extract'),
                          ('dmsh_xlate', REPO / 'nxnative' / 'dm' / 'gfx' / 'tools' / 'dmsh_xlate')):
        out = Path(a.out) if name == 'nx_extract' else Path(a.out).parent / name
        print(f'>> {name} for {a.runtime} (the phone ships it)')
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
            if f.is_file():
                print(f'   {f.name:40} {f.stat().st_size / 1024:8.0f} KB')
        exe = out / (f'{name}.exe' if os.name == 'nt' else name)
        if not exe.exists():
            print(f'>> no executable in {out}')
            return 1
        # the apphost is a Linux program: it has to be executable (the phone cannot exec a 0644 file)
        exe.chmod(0o755)
    # the phone has no ICU: the runtime needs the invariant mode, and .NET 9+ wants it declared here
    # (with the environment variable alone the runtime stops the program)
    import json
    for rc in Path(a.out).parent.glob('*/' + '*.runtimeconfig.json'):
        d = json.loads(rc.read_text())
        props = d.setdefault('runtimeOptions', {}).setdefault('configProperties', {})
        props['System.Globalization.Invariant'] = True
        rc.write_text(json.dumps(d, indent=2))
    # The APK's assets carry no permissions: the app restores them from modes.json, and a file that is not in it
    # lands as 0644 (not executable). The assets are built before these tools, so their modes are added here.
    modes_path = Path(a.out).parents[2] / 'modes.json'
    if modes_path.is_file():
        modes = json.loads(modes_path.read_text())
        for d in sorted(Path(a.out).parent.iterdir()):
            if not d.is_dir(): continue
            for f in sorted(d.rglob('*')):
                if f.is_file(): modes[f'tool/tools/{d.name}/{f.relative_to(d)}'] = f.stat().st_mode & 0o777
        modes_path.write_text(json.dumps(modes))
        print(f'>> the modes of the tools are in {modes_path}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
