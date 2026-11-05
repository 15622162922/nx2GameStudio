#!/usr/bin/env python3
"""Assemble the phone app's payload: the rootfs, the toolchain, the tool's tree and the Python modules.

The app unpacks this on its first start. The rootfs comes from the nixport payload (the supply channel's release:
the patched glibc, Python and the rest of the runtime), the toolchain from Google's NDK and an ARM64 LLVM, and the
tool's own tree from this repository.

    python3 build_assets.py --out <dir> [--payload <file>] [--toolchain <dir>] [--wheels <dir>]

The result is what the Gradle build takes as the app's assets:

    <out>/rootfs.pkg      the rootfs (the payload's tarball, gzipped)
    <out>/tool/            the tool: porter/, nxnative/ and the Python modules
    <out>/toolchain/       the NDK, the ARM64 compiler, glslc
    <out>/manifest.json    what is in there and what it is for
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, tarfile, urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
# What the app needs from the repository: the pipeline and the engine. The desktop app's GUI (PySide6), the probes,
# the tests and the documentation are not on the phone.
KEEP = ['porter', 'nxnative']
SKIP_DIRS = {'__pycache__', '.git', '.github', '.githooks', '.claude', '.idea', '.vscode', 'probes', 'tests', 'devtools',
             'packaging', 'docs', 'gui', 'ios', 'out', 'build', '.pytest_cache'}
# The tool's own tree holds what a local build left behind (nx_extract's bin/ and obj/): the phone does not need it.
# The build tools have bin directories of their own (the toolchain's clang/bin, its bin/glslc): they are part of the
# tools, so only the tool's tree is stripped of them.
TOOL_SKIP = SKIP_DIRS | {'bin', 'obj'}
SKIP_FILES = {'.DS_Store'}
# The Python modules the porter needs on the phone (the desktop GUI's PySide6 is not among them).
MODULES = ['lz4', 'capstone', 'pillow', 'fonttools', 'pyyaml', 'pycryptodome', 'backports.zstd']
# The NuGet packages the C# tools need (nx_extract reads the NCAs): the phone restores them from its own folder.
NUGET = [('LibHac', '0.19.0')]


def copy_tree(src: Path, dst: Path, skip=SKIP_DIRS):
    """Copies a tree, leaving out what the phone does not need"""
    for p in sorted(src.rglob('*')):
        rel = p.relative_to(src)
        if any(part in skip for part in rel.parts): continue
        if p.name in SKIP_FILES or p.name.endswith(('.pyc', '.apk', '.dmsv', '.spv')): continue
        target = dst / rel
        if p.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        elif p.is_file():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, target)
            if p.suffix == '.py': target.chmod(0o644)
        elif p.is_symlink():
            target.parent.mkdir(parents=True, exist_ok=True)
            link = os.readlink(p)
            if not target.exists(): target.symlink_to(link)


def nuget_packages(target: Path):
    """The NuGet packages the tools need, as a local source: the phone has no network, and a restore that
    waits for a name server that never answers looks like a build that never ends."""
    print('>> the NuGet packages (the phone builds offline)')
    packages = target / 'packages'
    packages.mkdir(parents=True, exist_ok=True)
    for name, version in NUGET:
        f = packages / f'{name.lower()}.{version}.nupkg'
        if not f.exists():
            url = f'https://www.nuget.org/api/v2/package/{name}/{version}'
            with urllib.request.urlopen(url) as r, open(f, 'wb') as out:
                shutil.copyfileobj(r, out, 1 << 20)
        print(f'   {f.name}')
    # the restore looks here and nowhere else: no name server, no timeout
    (target / 'NuGet.config').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<configuration>\n  <packageSources>\n    <clear />\n'
        '    <add key="local" value="packages" />\n  </packageSources>\n</configuration>\n', encoding='utf-8')
    return target


def wheels(target: Path):
    """The Python modules, as the aarch64 wheels for the rootfs's Python (3.13)"""
    if target.exists(): return target
    print('>> the Python modules (aarch64 wheels for Python 3.13)')
    tmp = Path(str(target) + '.tmp')
    shutil.rmtree(tmp, ignore_errors=True); tmp.mkdir(parents=True)
    names = []
    for m in MODULES:
        r = subprocess.run([sys.executable, '-m', 'pip', 'download', '--only-binary=:all:', '--no-deps',
                            '--platform', 'manylinux2014_aarch64', '--python-version', '3.13', '--implementation', 'cp',
                            '-d', str(tmp), m], capture_output=True, text=True)
        if r.returncode: raise SystemExit(f'pip download {m} failed:\n{r.stdout}\n{r.stderr}')
        names.append(m)
    for wheel in tmp.glob('*.whl'):
        import zipfile
        with zipfile.ZipFile(wheel) as z: z.extractall(target)
        print(f'   {wheel.name}')
    shutil.rmtree(tmp, ignore_errors=True)
    return target


def payload_rootfs(payload: Path, out: Path):
    """The rootfs out of the payload: the release gives a tarball with the rootfs inside"""
    print(f'>> the rootfs out of {payload.name}')
    # the name matters: the build expands an asset called *.tar.gz, and the app wants the stream as it is
    target = out / 'rootfs.pkg'
    if target.exists(): return target
    with tarfile.open(payload, 'r:gz') as t:
        member = next((m for m in t.getmembers() if m.name.endswith('rootfs-arm64.tar')), None)
        if not member: raise SystemExit(f'{payload}: no rootfs-arm64.tar inside')
        src = t.extractfile(member)
        import gzip
        with gzip.open(target, 'wb', compresslevel=6) as f:
            shutil.copyfileobj(src, f, 1 << 20)
    return target


DOTNET_MAJOR = 10
DOTNET_INDEX = f'https://builds.dotnet.microsoft.com/dotnet/release-metadata/{DOTNET_MAJOR}.0/releases.json'


def dotnet_sdk(cache):
    """The .NET SDK for an ARM64 Linux host: the newest one of the major the port builds with, checked against the
    SHA-512 in Microsoft's release list (the porter downloads the same file when the app does not ship it)"""
    req = urllib.request.Request(DOTNET_INDEX, headers={'User-Agent': 'nx2android-android-apk'})
    with urllib.request.urlopen(req, timeout=120) as r: rel = json.loads(r.read())
    item = None
    for release in rel['releases']:   # newest first
        for sdk in [release.get('sdk')] + release.get('sdks', []):
            for f in (sdk or {}).get('files', []):
                if f.get('rid') == 'linux-arm64' and f['name'] == 'dotnet-sdk-linux-arm64.tar.gz':
                    item = {'url': f['url'], 'hash': f['hash'], 'version': sdk['version']}
                    break
            if item: break
        if item: break
    if not item: raise SystemExit(f'no .NET SDK {DOTNET_MAJOR} for linux-arm64 in {DOTNET_INDEX}')
    target = cache / Path(item['url']).name
    if not target.exists():
        print(f'>> the .NET SDK ({item["version"]})')
        req = urllib.request.Request(item['url'], headers={'User-Agent': 'nx2android-android-apk'})
        with urllib.request.urlopen(req, timeout=120) as r, open(target.with_suffix('.part'), 'wb') as f:
            shutil.copyfileobj(r, f, 1 << 20)
        part = target.with_suffix('.part')
        got = hashlib.sha512(part.read_bytes()).hexdigest()
        if got.lower() != item['hash'].lower():
            part.unlink(missing_ok=True)
            raise SystemExit("the .NET SDK's SHA-512 is not the one Microsoft's list gives")
        part.replace(target)
    item['file'] = target
    return item


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    ap.add_argument('--payload', default='')
    ap.add_argument('--toolchain', default='')
    ap.add_argument('--cache', default='')
    ap.add_argument('--no-wheels', action='store_true')
    ap.add_argument('--no-dotnet', action='store_true', help='do not ship the .NET SDK: the app downloads it on the first run')
    a = ap.parse_args()
    out = Path(a.out); cache = Path(a.cache or (Path(__file__).resolve().parent / '.cache'))
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)

    payload = Path(a.payload) if a.payload else cache / 'payload-arm64.tar.gz'
    if not payload.exists(): raise SystemExit(f'{payload} is missing (fetch it from the nixport payload release)')
    payload_rootfs(payload, out)

    tool = out / 'tool'
    (tool / 'tools').mkdir(parents=True, exist_ok=True)
    for name in KEEP:
        src = REPO / name
        if not src.exists(): continue
        copy_tree(src, tool / name, skip=TOOL_SKIP)
    # the tool's own patch script (the interpreter paths of the programs the porter starts) and the SDK's
    # globalization fix (the phone's .NET has no ICU): both are run by the app, so they travel with it
    shutil.copy2(REPO / 'packaging/android-apk/patch_interp.py', tool / 'tools/patch_interp.py')
    shutil.copy2(REPO / 'packaging/android-apk/fix_globalization.py', tool / 'tools/fix_globalization.py')
    # the C# tools are built on the phone: the NuGet packages they restore have to be there (no network)
    nuget_packages(tool / 'nxnative' / 'dm' / 'tools' / 'nx_extract')
    # nx_extract itself: built here for linux-arm64, because the phone's .NET SDK cannot build it (its
    # globalization mode stops the SDK, and a restore needs the network)
    nx = tool / 'tools' / 'nx_extract'
    if not (nx / 'nx_extract').exists():
        print('>> nx_extract (linux-arm64): the .NET SDK is missing, the phone cannot build the tool')
    else:
        print(f'>> nx_extract (linux-arm64) at {nx}')
    if not a.no_wheels:
        wheels(tool / 'pylibs')

    toolchain = Path(a.toolchain) if a.toolchain else cache / 'toolchain'
    if not toolchain.exists(): raise SystemExit(f'{toolchain} is missing (run build_toolchain.py first)')
    print('>> the build tools')
    copy_tree(toolchain, out / 'toolchain')

    # The .NET SDK: the extractor and the shader translator are C# and are built with it. Shipping it makes the
    # first run work offline; downloading it (the porter's own path) keeps the APK small.
    dotnet = None
    if not a.no_dotnet:
        dotnet = dotnet_sdk(cache)
        shutil.copy2(dotnet['file'], out / 'dotnet.pkg')

    # The APK's assets have no permissions, so the modes of the tool's tree travel in a file of their own: the
    # guest's programs have to be executable, or the porter cannot start them.
    modes = {}
    for tree in ('tool', 'toolchain'):
        root = out / tree
        if not root.exists(): continue
        for p in root.rglob('*'):
            if p.is_file():
                modes[f'{tree}/{p.relative_to(root)}'] = p.stat().st_mode & 0o777
    (out / 'modes.json').write_text(json.dumps(modes))
    print(f'>> the modes of {len(modes)} file(s)')

    manifest = {'schema': 'nx2a.phone-assets.v1',
                'rootfs': 'rootfs.pkg',
                'tool': 'tool',
                'toolchain': 'toolchain',
                'modules': MODULES if not a.no_wheels else [],
                'dotnet': 'dotnet.pkg' if dotnet else '',
                'dotnet_sha512': dotnet['hash'] if dotnet else ''}
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    size = sum(f.stat().st_size for f in out.rglob('*') if f.is_file())
    print(f'>> the payload at {out}: {size / 2**20:.0f} MB')
    return 0


if __name__ == '__main__':
    sys.exit(main())
