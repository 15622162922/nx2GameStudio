# The doctor's rows on a host that cannot run adb: an ARM64 Linux host (the phone app's own porter) has no
# adb at all, so there is no row for it - and require() must not fall over the missing one.
import sys, unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from porter import toolchain   # noqa: E402


class NoAdbOnThePhone(unittest.TestCase):

    def test_no_adb_row(self):
        with mock.patch.object(toolchain, 'find_adb', return_value=None), \
             mock.patch.object(toolchain, '_adb_host_unsupported', return_value=True):
            rows = toolchain.checks(cfg={'keys': '', 'out': str(Path.home())}, keys='')
        self.assertNotIn('adb', [r.name for r in rows])

    def test_the_row_is_there_on_a_host_that_can_have_one(self):
        with mock.patch.object(toolchain, 'find_adb', return_value=None), \
             mock.patch.object(toolchain, '_adb_host_unsupported', return_value=False):
            rows = toolchain.checks(cfg={'keys': '', 'out': str(Path.home())}, keys='')
        self.assertIn('adb', [r.name for r in rows])

    def test_require_does_not_need_the_row(self):
        rows = [toolchain.Check('Android NDK', True, '27.2', path='/ndk'),
                toolchain.Check('.NET', True, '10.0', path='/dotnet')]
        with mock.patch.object(toolchain, 'checks', return_value=rows):
            tools = toolchain.require(adb=True)
        self.assertIsNone(tools['adb'])       # no row, no adb - and no KeyError


if __name__ == '__main__':
    unittest.main()
