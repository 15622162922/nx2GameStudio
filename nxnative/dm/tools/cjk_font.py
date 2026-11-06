#!/usr/bin/env python3
"""The Chinese and Korean system font, for the shared font types 1-4.

The Switch's system fonts are six: the standard one (Latin), the Simplified and Traditional Chinese ones, the
Korean one, the Nintendo extension (the button icons) and the Chinese extension. The host has none of them and
serves stand-ins (dm/host/hle_sdk16.c): the standard font is the system's Latin one (Roboto on Android), and the
Chinese and Korean types need a font with CJK glyphs, or the game's Chinese text draws as "?".

This tool finds such a font on the machine that ports and writes it into the data pack; the port then points
DM_SHARED_FONT_CJK at it:

    python3 cjk_font.py <out file> [--font <file>]

Where the font comes from:
  1. the file given (--font, or $DM_CJK_FONT)
  2. DroidSansFallbackFull.ttf beside this script: it comes with the tools (Apache-2.0, AOSP's), so every port
     has one whatever the machine runs
  3. /system/fonts/DroidSansFallback.ttf (Android before 10: the same font, on the device)
  4. /System/Library/Fonts/Supplemental/Arial Unicode.ttf (macOS)

The font must have its outlines in the TrueType tables (glyf), like the Latin fonts the font engine reads every
day: Android's own CJK font since Android 10 is a collection of CFF outlines (a .ttc), which the font engine
does not read, so it is not used here.

Nothing found: no font is written and the port says so (the game's Chinese text then draws as "?").
"""
import argparse, os, shutil, sys
from pathlib import Path

SHIPPED = Path(__file__).resolve().parent / 'DroidSansFallbackFull.ttf'   # comes with the tools (Apache-2.0)
CANDIDATES = [str(SHIPPED),
              '/system/fonts/DroidSansFallback.ttf',
              '/System/Library/Fonts/Supplemental/Arial Unicode.ttf']
# The face of a CJK collection the Simplified Chinese one
SC_NAMES = ('Noto Sans CJK SC', 'Source Han Sans SC', 'PingFang SC', 'Arial Unicode MS')


def from_collection(src: Path, out: Path) -> bool:
    """The Simplified Chinese face of a font collection (a TTC), written out as a font of its own"""
    try:
        from fontTools.ttLib import TTFont, TTCollection
    except ImportError:
        print(f'>> no fontTools (pip install fonttools): {src} cannot be unpacked', file=sys.stderr)
        return False
    if src.suffix.lower() == '.ttc':
        try:
            c = TTCollection(str(src))
        except Exception as e:
            print(f'>> {src} cannot be read: {e}', file=sys.stderr)
            return False
        faces = c.fonts
    else:
        faces = [TTFont(str(src))]
    pick = None
    for f in faces:
        try:
            name = f['name'].getDebugName(4) or ''
        except Exception:
            name = ''
        if any(name.startswith(n) for n in SC_NAMES):
            pick = f
            break
    if pick is None and faces:
        pick = faces[0]   # a collection of one: its only face
    if pick is None:
        return False
    try:
        pick.save(str(out))
    except Exception as e:
        print(f'>> {src} cannot be written: {e}', file=sys.stderr)
        return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--font', default=os.environ.get('DM_CJK_FONT', ''), help='the font to use (a TTF, or a collection to take the Simplified Chinese face from)')
    a = ap.parse_args()
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.is_file() and out.stat().st_size > 0:
        print(f'>> the Chinese font is already there: {out}')
        return 0
    given = Path(a.font).expanduser() if a.font else None
    if given and not given.is_file():
        print(f'>> no such font: {given}', file=sys.stderr)
        return 1
    src = given or next((Path(p) for p in CANDIDATES if Path(p).is_file()), None)
    if src is None:
        print('>> no Chinese font on this machine: the game\'s Chinese text will draw as "?"')
        return 0
    if src.suffix.lower() in ('.ttf', '.otf') and not given:
        shutil.copyfile(src, out)   # a plain font: use it as it is
        print(f'>> the Chinese font: {src} ({out.stat().st_size:,} bytes)')
        return 0
    if from_collection(src, out):
        print(f'>> the Chinese font: {src} -> {out} ({out.stat().st_size:,} bytes)')
        return 0
    return 1


if __name__ == '__main__':
    sys.exit(main())
