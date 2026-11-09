"""The app's own language: the two text tables and the words that are left in the source.

The guard on the app's own text (android/app/src/main/res/values*/strings.xml) and on the Kotlin sources
that read it. Everything here runs in CI: `python -m unittest discover tests`.

What it holds:
  1. the two tables carry the same keys (a word in one language and not in the other is a hole)
  2. no Chinese is left in the Kotlin sources - a word that was not moved is a word the player never sees
     in English (the layout fixture in appstate/LibraryState.kt is the one exception: it is there to see
     the Chinese glyphs' width)
  3. every R.string.x the sources name is in the table (a typo would be a crash on that screen)
  4. every stringResource(R.string.x, ...) passes as many arguments as the string has placeholders
     (Android checks that at runtime, on the screen nobody has opened yet)
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KT = ROOT / 'android/app/src/main/java/com/nx/android'
DEFAULT = ROOT / 'android/app/src/main/res/values/strings.xml'
CHINESE = ROOT / 'android/app/src/main/res/values-zh/strings.xml'

# the layout fixture: the fake game names are there to see the Chinese glyphs' width, and they are
# behind a flag that is off (FAKE_DATA = false)
EXEMPT = {'appstate/LibraryState.kt'}

STR = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
CJK = re.compile(r'[一-鿿]')


def table(path):
    """The keys of a text table, and the ones that are not translated on purpose."""
    text = path.read_text(encoding='utf-8')
    keys, untranslatable = set(), set()
    for name, attrs, _ in STR.findall(text):
        keys.add(name)
        if 'translatable="false"' in attrs:
            untranslatable.add(name)
    return keys, untranslatable


def sources():
    """Every Kotlin source of the app, with its text.

    The path is written with slashes on every platform: the exemption below is a path, and on Windows
    str() would give it a backslash.
    """
    for p in sorted(KT.rglob('*.kt')):
        rel = p.relative_to(KT).as_posix()
        yield rel, p.read_text(encoding='utf-8')


class AppTextTest(unittest.TestCase):

    def test_the_two_tables_carry_the_same_keys(self):
        en, en_fixed = table(DEFAULT)
        zh, zh_fixed = table(CHINESE)
        self.assertTrue(en, 'the default table is empty')
        self.assertEqual(en - en_fixed, zh - zh_fixed,
                         'a word is in one language and not in the other')

    def test_every_word_of_the_default_table_is_translated(self):
        """The default table is English; the Chinese one is a translation of it, not a different list."""
        text = CHINESE.read_text(encoding='utf-8')
        for name, attrs, body in STR.findall(text):
            if 'translatable="false"' in attrs:
                continue
            self.assertTrue(body.strip(), f'"{name}" is empty')

    def test_no_chinese_is_left_in_the_kotlin_sources(self):
        """A word that stayed in the source is a word the player never sees in English."""
        left = []
        for rel, text in sources():
            if rel in EXEMPT:
                continue
            stripped = re.sub(r'//[^\n]*', '', re.sub(r'/\*.*?\*/', '', text, flags=re.S))
            for i, line in enumerate(stripped.split('\n'), 1):
                for m in re.finditer(r'"((?:[^"\\\n]|\\.)*)"', line):
                    if CJK.search(m.group(1)):
                        left.append(f'{rel}:{i}  {m.group(1)[:40]}')
        self.assertEqual([], left, 'Chinese left in the source')

    def test_no_table_string_leans_on_the_whitespace(self):
        """aapt2 folds the runs of spaces and trims the ends: a string that leans on them changes.

        The separator between a duration and the state after it is in the code, not in a string.
        """
        for path in (DEFAULT, CHINESE):
            for name, attrs, body in STR.findall(path.read_text(encoding='utf-8')):
                self.assertNotIn('  ', body, f'"{name}" has a run of spaces (aapt2 folds it)')
                self.assertEqual(body.strip(), body, f'"{name}" starts or ends with a space (aapt2 trims it)')
                self.assertNotIn('\t', body, f'"{name}" has a tab (aapt2 folds it)')

    def test_every_word_the_sources_name_is_in_the_table(self):
        en, _ = table(DEFAULT)
        named = set()
        for _, text in sources():
            named |= set(re.findall(r'R\.string\.(\w+)', text))
        self.assertTrue(named, 'no R.string is used at all')
        missing = sorted(named - en)
        self.assertEqual([], missing, 'a word the sources use is not in the table')

    def test_the_arguments_match_the_placeholders(self):
        """Android checks the argument count at runtime: a mismatch is a crash on that screen."""
        text = DEFAULT.read_text(encoding='utf-8')
        placeholders = {}
        for name, attrs, body in STR.findall(text):
            placeholders[name] = len(set(re.findall(r'%(\d+)\$', body)))
        for rel, src in sources():
            for m in re.finditer(r'stringResource\(R\.string\.(\w+)((?:,\s*[^()]*?)*)\)', src):
                name, args = m.group(1), m.group(2).strip()
                if name not in placeholders:
                    continue
                n = 0 if not args else len([a for a in args.split(',') if a.strip()])
                self.assertEqual(placeholders[name], n,
                                 f'{rel}: {name} takes {placeholders[name]} arguments, {n} given')


if __name__ == '__main__':
    unittest.main()
