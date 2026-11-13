#!/usr/bin/env python3
"""The Chinese and Korean system font, for the shared font types 1-4.

The Switch's system fonts are six: the standard one (Latin), the Simplified and Traditional Chinese ones, the
Korean one, the Nintendo extension (the button icons) and the Chinese extension. The host has none of them and
serves stand-ins (dm/host/hle_sdk16.c): the standard font is the system's Latin one (Roboto on Android), and the
Chinese and Korean types need a font with CJK glyphs, or the game's Chinese text draws as "?".

This tool builds that font: shared_cjk.ttf, the file the tools ship and the port puts in the data pack, with
DM_SHARED_FONT pointing at it (the game's shared font is then this one for every type but the button icons).

    python3 cjk_font.py <out file> [--font <file>]

The port does not build it: the phone would need the tools for it, and a font made on one machine differs from
one made on another. The file beside this script (shared_cjk.ttf) is the one that ships; rebuild it with this
tool when the character set below changes.

The font is made of two of the fonts that come with the tools (both Apache-2.0):
  * OpenSans-Regular.ttf, the Latin base: the console's own shared fonts are like this (a plain TrueType font
    with its glyph names in a "post" table of format 2.0), and a game that reads the font the console's way
    expects that shape;
  * DroidSansFallbackFull.ttf (AOSP's), for the CJK glyphs themselves: the 6,669 characters of GB2312 (the
    commonest simplified Chinese ones and the rest), the 13,706 of Big5 (the traditional Chinese ones: 9,379 of
    them are not in GB2312, and a game in traditional Chinese drew every one of them as "?"), the zhuyin symbols
    and the ASCII ones.

The two are merged into one font, the CJK ones scaled to the base's units per em. A game parses this font
itself: Mario vs. Donkey Kong (1.0.1, SDK 15) draws its Chinese text from the shared font and builds its glyph
table from the glyph names, and with the CJK font alone (DroidSansFallback on its own) every glyph lookup missed
and the game crashed on the null glyph (main+0x428d1c, the same shape as The Legend of Zelda's main+0x8b37c).

Where the font comes from:
  1. the file given (--font, or $DM_CJK_FONT): the Latin base then (the CJK glyphs are still added)
  2. OpenSans-Regular.ttf beside this script

Nothing found: no font is written and the port says so (the game's Chinese text then draws as "?").
"""
import argparse, os, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BASE = HERE / 'OpenSans-Regular.ttf'             # the Latin base (Apache-2.0)
SHIPPED = HERE / 'DroidSansFallbackFull.ttf'     # the CJK glyphs (Apache-2.0)
CANDIDATES = [str(BASE), '/system/fonts/DroidSansFallback.ttf', '/System/Library/Fonts/Supplemental/Arial Unicode.ttf']


def gb2312():
    """The 6763 characters of GB2312 (level 1: the 3755 commonest ones, level 2: 2914 more), as code points.
    Level 2 is in it too: a game's text has characters out of the commonest ones (Mario vs. Donkey Kong's
    title has one: 咚), and a character the font does not have draws as "?"."""
    out = set()
    for hi in range(0xB0, 0xF7):
        for lo in range(0xA1, 0xFF):
            try:
                out.add(ord(bytes([hi, lo]).decode('gb2312')))
            except UnicodeDecodeError:
                pass
    return out


def big5():
    """The characters of Big5, the traditional Chinese standard, as code points (13,706: the 5,401 commonest
    ones and the rest). They are cut in too: GB2312 is simplified Chinese only, so a game in traditional Chinese
    (zh-TW, zh-HK) drew every character outside it as "?". The codec is big5hkscs, which is Big5 plus Hong Kong's
    extra characters (a game from Hong Kong uses them)."""
    out = set()
    for hi in range(0x81, 0xFF):
        for lo in range(0x40, 0xFF):
            try:
                s = bytes([hi, lo]).decode('big5hkscs')
            except UnicodeDecodeError:
                continue
            # four of the Hong Kong's pairs stand for two characters (a base and a combining mark): the base is the one the font has
            out.add(ord(s[0]))
    return out


def cjk_face(src: Path, out: Path, units: int) -> bool:
    """The CJK glyphs of a font, cut down to the common Chinese characters and scaled to the base's units"""
    try:
        from fontTools.ttLib import TTFont
        from fontTools.ttLib.scaleUpem import scale_upem
        from fontTools import subset
    except ImportError:
        print(f'>> no fontTools (pip install fonttools): {src} cannot be read', file=sys.stderr)
        return False
    uni = set(range(0x20, 0x7f)) | gb2312() | big5() | set(range(0x3000, 0x3040)) | set(range(0x3105, 0x3130)) | set(range(0xff00, 0xff60))
    try:
        f = TTFont(str(src))
        o = subset.Options()
        o.layout_features = []   # the layout tables keep the empty glyphs alive, and the game reads none of them
        o.name_IDs = ['*']; o.name_legacy = True; o.notdef_outline = True; o.recalc_bounds = True
        o.glyph_names = True; o.retain_gids = False
        s = subset.Subsetter(options=o)
        s.populate(unicodes=uni)
        s.subset(f)
        scale_upem(f, units)
        for t in ('kern', 'DSIG', 'vhea', 'vmtx', 'GDEF', 'GSUB', 'GPOS'):
            if t in f: del f[t]      # the base font has its own, and the merger wants them alike
        f.save(str(out))
    except Exception as e:
        print(f'>> {src} cannot be read: {e}', file=sys.stderr)
        return False
    return True


def build(base: Path, cjk: Path, out: Path) -> bool:
    """The font the game reads: the Latin base with the CJK glyphs merged in"""
    try:
        from fontTools.ttLib import TTFont
        from fontTools.merge import Merger
    except ImportError:
        print('>> no fontTools (pip install fonttools): the font cannot be made', file=sys.stderr)
        return False
    try:
        b = TTFont(str(base))
        units = b['head'].unitsPerEm
        if not cjk_face(cjk, out.with_suffix('.cjk.ttf'), units): return False
        font = Merger().merge([str(base), str(out.with_suffix('.cjk.ttf'))])
        font['post'].formatType = 2.0    # the glyph names, which the game builds its glyph table from
        font['post'].extraNames = {}; font['post'].mapping = {}
        font.save(str(out))
    except Exception as e:
        print(f'>> the font cannot be made: {e}', file=sys.stderr)
        return False
    finally:
        out.with_suffix('.cjk.ttf').unlink(missing_ok=True)
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out')
    ap.add_argument('--font', default=os.environ.get('DM_CJK_FONT', ''), help='the Latin base to use (a TTF); the CJK glyphs are still added')
    a = ap.parse_args()
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    given = Path(a.font).expanduser() if a.font else None
    if given and not given.is_file():
        print(f'>> no such font: {given}', file=sys.stderr)
        return 1
    base = given or next((Path(p) for p in CANDIDATES if Path(p).is_file()), None)
    if base is None:
        print('>> no Latin font on this machine: the game\'s Chinese text will draw as "?"')
        return 0
    if not SHIPPED.is_file():
        print(f'>> the CJK glyphs are missing: {SHIPPED}', file=sys.stderr)
        return 1
    if not build(base, SHIPPED, out):
        return 1
    print(f'>> the shared font: {base} + {SHIPPED.name} -> {out} ({out.stat().st_size:,} bytes)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
