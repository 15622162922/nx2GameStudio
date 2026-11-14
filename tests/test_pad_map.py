"""The physical controller's keys: the player's own bindings, the default dictionary and the calibration.

The rules are pure C (nxnative/dm/android/pad_map.h): the phone's front end uses them, and here they are
compiled on the host and checked. The default dictionary must stay bit-for-bit what it was before the gamepad
center: the Switch's layout by position (the bottom face button is B, the right one A).
"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import host_cc  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
HDR = ROOT / 'nxnative/dm/android/pad_map.h'
DM = ROOT / 'nxnative/dm/host'

HARNESS = r'''
#include "pad_map.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>

static void defaults(void) {
    // by position: the Switch's layout (the bottom button is B, the right one A)
    assert(pad_default_bit(PAD_KEY_BUTTON_A, 0) == DM_BTN_B);
    assert(pad_default_bit(PAD_KEY_BUTTON_B, 0) == DM_BTN_A);
    assert(pad_default_bit(PAD_KEY_BUTTON_X, 0) == DM_BTN_Y);
    assert(pad_default_bit(PAD_KEY_BUTTON_Y, 0) == DM_BTN_X);
    assert(pad_default_bit(PAD_KEY_BUTTON_L1, 0) == DM_BTN_L);
    assert(pad_default_bit(PAD_KEY_BUTTON_R1, 0) == DM_BTN_R);
    assert(pad_default_bit(PAD_KEY_BUTTON_L2, 0) == DM_BTN_ZL);
    assert(pad_default_bit(PAD_KEY_BUTTON_R2, 0) == DM_BTN_ZR);
    assert(pad_default_bit(PAD_KEY_BUTTON_THUMBL, 0) == DM_BTN_LS);
    assert(pad_default_bit(PAD_KEY_BUTTON_THUMBR, 0) == DM_BTN_RS);
    assert(pad_default_bit(PAD_KEY_BUTTON_START, 0) == DM_BTN_PLUS);
    assert(pad_default_bit(PAD_KEY_BUTTON_SELECT, 0) == DM_BTN_MINUS);
    assert(pad_default_bit(PAD_KEY_DPAD_UP, 0) == DM_BTN_UP);
    assert(pad_default_bit(PAD_KEY_DPAD_DOWN, 0) == DM_BTN_DOWN);
    assert(pad_default_bit(PAD_KEY_DPAD_LEFT, 0) == DM_BTN_LEFT);
    assert(pad_default_bit(PAD_KEY_DPAD_RIGHT, 0) == DM_BTN_RIGHT);
    assert(pad_default_bit(9999, 0) == 0);

    // by label: A is A, B is B, X is X, Y is Y; the rest is the same
    assert(pad_default_bit(PAD_KEY_BUTTON_A, 1) == DM_BTN_A);
    assert(pad_default_bit(PAD_KEY_BUTTON_B, 1) == DM_BTN_B);
    assert(pad_default_bit(PAD_KEY_BUTTON_X, 1) == DM_BTN_X);
    assert(pad_default_bit(PAD_KEY_BUTTON_Y, 1) == DM_BTN_Y);
    assert(pad_default_bit(PAD_KEY_BUTTON_L1, 1) == DM_BTN_L);
    assert(pad_default_bit(PAD_KEY_BUTTON_START, 1) == DM_BTN_PLUS);
    assert(pad_default_bit(9999, 1) == 0);
}

static void table(void) {
    pad_map_t map;
    const char *spec =
        "v1\n"
        "dev 3 labels=1 dz=0.15 tr=0.55\n"
        "bind scan:305 a\n"
        "unbind scan:314\n"
        "dev 7 labels=0 dz=0.12 tr=0.5\n";
    assert(pad_map_parse(&map, spec) == 2);

    // the device's own table: the binding wins over the dictionary
    assert(pad_map_key_bit(&map, 3, 305, 97) == DM_BTN_A);
    // a key the player did not touch: the dictionary, by label for this device
    assert(pad_map_key_bit(&map, 3, 307, 99) == DM_BTN_X);
    // a disabled source: nothing
    assert(pad_map_key_bit(&map, 3, 314, 109) == 0);
    // the other device: by position, and its own table
    assert(pad_map_key_bit(&map, 7, 305, 97) == DM_BTN_A);
    assert(pad_map_key_bit(&map, 7, 0, 96) == DM_BTN_B);
    // a device without a table: the dictionary, by position
    assert(pad_map_key_bit(&map, 9, 0, 96) == DM_BTN_B);

    // the face buttons' flip: the table's, else the environment's (unset here)
    assert(pad_map_labels(&map, 3) == 1);
    assert(pad_map_labels(&map, 7) == 0);
    assert(pad_map_labels(&map, 9) == 0);

    // the calibration: the device's own, the default for the others
    assert(pad_map_deadzone(&map, 3) > 0.149f && pad_map_deadzone(&map, 3) < 0.151f);
    assert(pad_map_trigger(&map, 3) > 0.549f && pad_map_trigger(&map, 3) < 0.551f);
    assert(pad_map_deadzone(&map, 9) == PAD_DEFAULT_DEADZONE);
    assert(pad_map_trigger(&map, 9) == PAD_DEFAULT_TRIGGER);

    // an axis binding: the half of the axis, with its sign
    assert(pad_map_has_axis(&map, 3, PAD_AXIS_LTRIGGER) == 0);
    assert(pad_map_axis_bit(&map, 3, PAD_AXIS_LTRIGGER, 0.9f) == 0);
}

static void axes(void) {
    pad_map_t map;
    assert(pad_map_parse(&map, "v1\ndev 5 labels=0\nbind axis:17:+ zl\n") == 1);
    assert(pad_map_has_axis(&map, 5, PAD_AXIS_LTRIGGER) == 1);
    assert(pad_map_axis_bit(&map, 5, PAD_AXIS_LTRIGGER, 0.9f) == DM_BTN_ZL);
    assert(pad_map_axis_bit(&map, 5, PAD_AXIS_LTRIGGER, -0.9f) == 0);
}

static void broken(void) {
    pad_map_t map;
    // a broken table is a table with fewer entries, never a crash
    assert(pad_map_parse(&map, "v1\ndev notanumber\nbind\nbind scan:305 nosuchbutton\nunbind\n") >= 0);
    assert(pad_map_key_bit(&map, 0, 0, PAD_KEY_BUTTON_A) == DM_BTN_B);
}

int main(void) {
    defaults();
    table();
    axes();
    broken();
    printf("pad_map ok\n");
    return 0;
}
'''


class PadMapTest(unittest.TestCase):
    """The mapping table the gamepad center pushes to the host."""

    def test_harness_builds_and_passes(self):
        if not host_cc.posix():
            self.skipTest('no C compiler here')
        with tempfile.TemporaryDirectory() as d:
            src = Path(d) / 'harness.c'
            src.write_text(HARNESS)
            exe = Path(d) / 'harness'
            build = subprocess.run(
                ['cc', '-std=gnu11', '-I', str(HDR.parent), '-I', str(DM), '-o', str(exe), str(src)],
                capture_output=True, timeout=120)
            self.assertEqual(build.returncode, 0, build.stderr.decode('utf-8', 'replace'))
            run = subprocess.run([str(exe)], capture_output=True, timeout=60)
            self.assertEqual(run.returncode, 0, run.stderr.decode('utf-8', 'replace'))


if __name__ == '__main__':
    unittest.main()
