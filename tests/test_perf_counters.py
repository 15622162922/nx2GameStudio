"""The performance HUD's counters in the presentation path, without a Vulkan device.

The counters live in gfx_vk_present.c: the game's frames presented, the frame generation's frames presented, and
the intervals between the game's frames (their sum and their count) for the average frame time. The app reads
two samples a second apart and does the arithmetic; this checks the counters themselves.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1] / 'nxnative/dm/android/gfx_vk_present.c'
MARKER = "// ---- the performance HUD's counters (dm_present_sample) ----"


class PerfCounters(unittest.TestCase):
    def test_counters(self):
        cc = shutil.which('cc') or shutil.which('clang')
        if not cc:
            self.skipTest('a C compiler is needed for the counters check')
        src = SOURCE.read_text(encoding='utf-8')
        start = src.index(MARKER)
        block = src[start:src.index('static void pace_reset', start)]
        harness = r'''
#include <assert.h>
#include <stdatomic.h>
#include <stdint.h>
#include <string.h>

uint64_t vk_refresh_cycle_ns(void) { return 16666667ull; }   // the stub of the display timing
''' + block + r'''
int main(void) {
    uint64_t s[5];

    // the first game frame: the counter moves, but there is no interval to it yet
    hud_count(1, 1.0 / 60.0, 1000000000ull);
    dm_present_sample(s);
    assert(s[0] == 1);            // the game's frames
    assert(s[1] == 0);            // the generation's frames
    assert(s[2] == 0);            // the intervals' sum
    assert(s[3] == 0);            // the intervals' count
    assert(s[4] == 16666667ull);  // the display's refresh

    // 59 more game frames, 16.666 ms apart: the intervals add up
    for (int i = 1; i < 60; i++) hud_count(1, 1.0 / 60.0, 1000000000ull + (uint64_t)i * 16666667ull);
    dm_present_sample(s);
    assert(s[0] == 60);
    assert(s[3] == 59);                       // the first frame has no interval before it
    assert(s[2] == 59 * 16666667ull);         // the intervals between the frames

    // the frame generation's frames count apart: they reach the screen too
    for (int i = 0; i < 60; i++) hud_count(0, 1.0 / 120.0, 2000000000ull + (uint64_t)i * 8333333ull);
    dm_present_sample(s);
    assert(s[0] == 60);
    assert(s[1] == 60);
    assert(s[3] == 59);                       // the generation's frames are not the game's intervals

    // a redraw (the menu, the editor) presents the last frame again: not a frame
    hud_count(1, -1, 3000000000ull);
    dm_present_sample(s);
    assert(s[0] == 60);

    // a pause: the next frame comes much later, and that gap is not a frame time
    hud_count(1, 1.0 / 60.0, 3000000000ull + 5000000000ull);
    dm_present_sample(s);
    assert(s[0] == 61);
    assert(s[3] == 59);                       // the long gap is not counted
    assert(s[2] == 59 * 16666667ull);

    // the next frame after the pause: its interval is measured from the pause's end
    hud_count(1, 1.0 / 60.0, 3000000000ull + 5000000000ull + 16666667ull);
    dm_present_sample(s);
    assert(s[0] == 62);
    assert(s[3] == 60);
    assert(s[2] == 59 * 16666667ull + 16666667ull);

    // a null pointer is answered, not a crash
    dm_present_sample(NULL);
    return 0;
}
'''
        with tempfile.TemporaryDirectory(prefix='nx2a_perf_counters_') as tmp:
            c = Path(tmp) / 'check.c'
            exe = Path(tmp) / 'check'
            c.write_text(harness, encoding='utf-8')
            result = subprocess.run([cc, '-std=gnu11', '-Wall', '-Wextra', '-Werror', '-pthread', str(c), '-o', str(exe)],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([str(exe)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
