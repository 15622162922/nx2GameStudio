#!/usr/bin/env python3
"""Build the game's launcher into a dex file and put it in the app's assets.

The launcher is the activity the icon opens: it shows the game's information and lets the player pick the
language and the Vulkan driver before the game starts. It is Java, because it is a normal Android UI with
spinners and a button; the game itself stays a code-less NativeActivity.

The dex is built here, on the computer, and shipped in the APK: the phone has no JDK and no Android SDK, and
the .NET SDK's own problems make the SDK unusable there anyway (the same reason nx_extract is built here).

    python3 build_launcher.py [--out <file>] [--sdk <dir>]

The tools it needs: a JDK (JAVA_HOME, or Android Studio's), an Android SDK (for android.jar and d8).
"""
import argparse, os, shutil, subprocess, sys, tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
SOURCE = REPO / 'nxnative' / 'dm' / 'android' / 'launcher' / 'LauncherActivity.java'
DEFAULT_OUT = Path(__file__).resolve().parent / '.cache' / 'launcher.dex'
PACKAGE = 'com.nx2a.launcher'


def find_jdk():
    """A JDK: JAVA_HOME, then Android Studio's own, then the system's"""
    candidates = []
    if os.environ.get('JAVA_HOME'): candidates.append(Path(os.environ['JAVA_HOME']))
    for app in ('/Applications/Android Studio.app/Contents/jbr/Contents/Home',
                '/Applications/Android Studio.app/Contents/jbr/Contents/Home',
                r'C:\Program Files\Android\Android Studio\jbr',
                r'C:\Program Files\Android\Android Studio\jre'):
        candidates.append(Path(app))
    java_home = shutil.which('javac')
    if java_home: candidates.append(Path(java_home).resolve().parent.parent)
    for c in candidates:
        if (c / 'bin' / 'javac').is_file(): return c
    raise SystemExit('no JDK found (set JAVA_HOME, or install Android Studio: the launcher is built with javac)')


def find_sdk():
    """The Android SDK: ANDROID_HOME, ANDROID_SDK_ROOT, or the usual places"""
    candidates = []
    for v in ('ANDROID_HOME', 'ANDROID_SDK_ROOT'): 
        if os.environ.get(v): candidates.append(Path(os.environ[v]))
    candidates += [Path.home() / 'Library' / 'Android' / 'sdk', Path.home() / 'Android' / 'Sdk',
                   Path('C:/Users/' + os.environ.get('USERNAME', '') + '/AppData/Local/Android/Sdk')]
    for c in candidates:
        if c.is_dir(): return c
    raise SystemExit('no Android SDK found (set ANDROID_HOME)')


def find_android_jar(sdk: Path) -> Path:
    platforms = sorted((sdk / 'platforms').glob('android-*'), key=lambda p: p.name, reverse=True)
    for p in platforms:
        jar = p / 'android.jar'
        if jar.is_file(): return jar
    raise SystemExit(f'no android.jar in {sdk / "platforms"} (install a platform with the SDK manager)')


def find_d8(sdk: Path) -> Path:
    tools = sorted((sdk / 'build-tools').glob('*'), key=lambda p: p.name, reverse=True)
    for t in tools:
        for name in ('d8', 'd8.bat'):
            d = t / name
            if d.is_file(): return d
    raise SystemExit(f'no d8 in {sdk / "build-tools"} (install the build tools with the SDK manager)')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default=str(DEFAULT_OUT))
    ap.add_argument('--sdk', default='')
    a = ap.parse_args()
    out = Path(a.out)
    jdk = find_jdk()
    sdk = Path(a.sdk) if a.sdk else find_sdk()
    jar = find_android_jar(sdk)
    d8 = find_d8(sdk)
    print(f'>> the launcher: javac {jdk}, android.jar {jar.name}, d8 {d8.parent.name}')

    with tempfile.TemporaryDirectory(prefix='nx2a-launcher.') as tmp:
        tmp = Path(tmp)
        classes = tmp / 'classes'
        classes.mkdir()
        cmd = [str(jdk / 'bin' / 'javac'), '-encoding', 'UTF-8', '-nowarn', '-classpath', str(jar),
               '-d', str(classes), str(SOURCE)]
        r = subprocess.run(cmd, capture_output=True, text=True, errors='replace')
        if r.returncode:
            print(r.stdout + r.stderr, file=sys.stderr)
            raise SystemExit('javac failed')
        dexdir = tmp / 'dex'
        dexdir.mkdir()
        cmd = [str(d8), '--min-api', '29', '--output', str(dexdir)] + [str(p) for p in sorted(classes.rglob('*.class'))]
        r = subprocess.run(cmd, capture_output=True, text=True, errors='replace')
        if r.returncode:
            print(r.stdout + r.stderr, file=sys.stderr)
            raise SystemExit('d8 failed')
        dex = dexdir / 'classes.dex'
        if not dex.is_file(): raise SystemExit(f'd8 wrote no classes.dex in {dexdir}')
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(dex, out)
    print(f'>> {out} ({out.stat().st_size:,} bytes)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
