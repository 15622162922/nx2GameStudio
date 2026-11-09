# porter/saves.py's zip handling: the save folder in a package, and the device save beside it (a package the
# phone wrote holds both).
import sys, tempfile, unittest, zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from porter import saves   # noqa: E402
from porter.upstream import BuildError   # noqa: E402


class Unpacked(unittest.TestCase):
    def _zip(self, tmp, entries):
        pkg = Path(tmp) / 'pkg.zip'
        with zipfile.ZipFile(pkg, 'w') as z:
            for name, body in entries.items(): z.writestr(name, body)
        return pkg

    def test_a_zip_holding_one_folder(self):
        with tempfile.TemporaryDirectory() as t:
            pkg = self._zip(t, {'save/slot0.dat': 'progress'})
            folder = saves._unpacked(pkg, Path(t) / 'out')
            self.assertEqual('save', folder.name)
            self.assertTrue((folder / 'slot0.dat').is_file())

    def test_a_package_with_its_device_save(self):
        # the phone's package: the save folder, and the device save beside it
        with tempfile.TemporaryDirectory() as t:
            pkg = self._zip(t, {'game/slot0.dat': 'progress', 'game.device/island.dat': 'device'})
            folder = saves._unpacked(pkg, Path(t) / 'out')
            self.assertEqual('game', folder.name)
            self.assertTrue((folder / 'slot0.dat').is_file())
            device = saves._device_of(folder)
            self.assertIsNotNone(device)
            self.assertTrue((device / 'island.dat').is_file())

    def test_a_package_without_a_device_save(self):
        with tempfile.TemporaryDirectory() as t:
            pkg = self._zip(t, {'game/slot0.dat': 'progress'})
            folder = saves._unpacked(pkg, Path(t) / 'out')
            self.assertIsNone(saves._device_of(folder))

    def test_a_zip_that_points_outside_is_refused(self):
        with tempfile.TemporaryDirectory() as t:
            pkg = self._zip(t, {'../escaped.dat': 'gotcha'})
            with self.assertRaisesRegex(BuildError, 'points outside'): saves._unpacked(pkg, Path(t) / 'out')

    def test_not_a_zip(self):
        with tempfile.TemporaryDirectory() as t:
            f = Path(t) / 'save.bin'
            f.write_text('x')
            with self.assertRaisesRegex(BuildError, 'not a folder or a .zip'): saves._unpacked(f, Path(t) / 'out')


if __name__ == '__main__':
    unittest.main()
