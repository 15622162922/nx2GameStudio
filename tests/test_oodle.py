# porter/pipeline.py's Oodle path without a game and without a device: the job file the game's own decoder reads, its answer, and where the decoder runs
# (python -m unittest discover tests).
import os, struct, sys, tempfile, unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from porter.pipeline import on_android, oodle_job, oodle_result, oodle_where   # noqa: E402
from porter.upstream import BuildError   # noqa: E402


def gfpak(entries):
    """A GFLXPACK archive (Pokémon Legends: Arceus's): 'GFLXPACK', u32 version, u32 0, u32 file count, u32 folder count, u64 file table offset, then the entries'
    data and the file table (0x18 bytes an entry: u16 compression, u32 raw size, u32 stored size, u64 offset)"""
    data = b''.join(e[2] for e in entries)
    table, off = b'', 0x20
    for ct, usz, blob in entries:
        table += struct.pack('<2xHII4xQ', ct, usz, len(blob), off)
        off += len(blob)
    head = b'GFLXPACK' + struct.pack('<II', 0x1000, 0) + struct.pack('<I', len(entries)) + struct.pack('<I', 0) + struct.pack('<Q', 0x20 + len(data))
    return head + data + table


class OodleJob(unittest.TestCase):
    def test_gfpak_entries(self):
        """Only the Oodle entries (compression 3) go into the job file, in order"""
        with tempfile.TemporaryDirectory() as t:
            rom = Path(t) / 'RomFS'; (rom / 'bin/archive').mkdir(parents=True)
            arc = gfpak([(3, 100, b'\x01\x02'), (1, 50, b'\x03'), (3, 7, b'\x04\x05\x06')])
            (rom / 'bin/archive/a.gfpak').write_bytes(arc)
            recs, job = oodle_job([rom], Path(t))
            b = job.read_bytes()
            self.assertEqual(b[:4], b'DMOJ')
            self.assertEqual(struct.unpack_from('<I', b, 4)[0], 2)   # the two Oodle entries
            self.assertEqual(recs, [('/bin/archive/a.gfpak', len(arc), 0), ('/bin/archive/a.gfpak', len(arc), 2)])
            self.assertEqual(struct.unpack_from('<II', b, 8), (2, 100))   # u32 stored size, u32 raw size
            self.assertEqual(b[16:18], b'\x01\x02')
            self.assertEqual(struct.unpack_from('<II', b, 18), (3, 7))
            self.assertEqual(b[26:], b'\x04\x05\x06')

    def test_no_archive(self):
        """A folder with no Oodle archive gives an empty job file"""
        with tempfile.TemporaryDirectory() as t:
            rom = Path(t) / 'RomFS'; rom.mkdir()
            recs, job = oodle_job([rom], Path(t))
            self.assertEqual(recs, [])
            self.assertEqual(job.read_bytes(), b'DMOJ' + struct.pack('<I', 0))

    def test_other_archive(self):
        """A file that is not a GFLXPACK is left alone"""
        with tempfile.TemporaryDirectory() as t:
            rom = Path(t) / 'RomFS'; rom.mkdir()
            (rom / 'a.gfpak').write_bytes(b'NOTAPACK' + b'\x00' * 32)
            recs, job = oodle_job([rom], Path(t))
            self.assertEqual(recs, [])


class OodleResult(unittest.TestCase):
    def test_answer(self):
        """The entries that hold a BNSH shader are written back as the split's decoded folder (DGFD); the ones left out are not"""
        with tempfile.TemporaryDirectory() as t:
            tmp = Path(t); recs = [('/bin/archive/a.gfpak', 100, 0), ('/bin/archive/a.gfpak', 100, 1)]
            (tmp / 'job.out').write_bytes(b'DMOR' + struct.pack('<I', 2) + struct.pack('<I', 0xffffffff) + struct.pack('<I', 3) + b'abc')
            dec = oodle_result(tmp, recs, 'GFPAK', 'here')
            b = (dec / 'bin__archive__a.gfpak.dec').read_bytes()
            self.assertEqual(b[:4], b'DGFD')
            self.assertEqual(struct.unpack_from('<QI', b, 4), (100, 1))   # the archive's size, the entries kept
            self.assertEqual(b[16:], struct.pack('<II', 1, 3) + b'abc')
            self.assertFalse((tmp / 'job.out').exists())   # the answer is consumed

    def test_trinity_answer(self):
        """A Trinity pack's entry keeps its pack offset and its index (DTPD)"""
        with tempfile.TemporaryDirectory() as t:
            tmp = Path(t); recs = [('/arc/data.trpfs', 200, (4096, 7))]
            (tmp / 'job.out').write_bytes(b'DMOR' + struct.pack('<I', 1) + struct.pack('<I', 2) + b'xy')
            dec = oodle_result(tmp, recs, 'Trinity', 'here')
            b = (dec / 'arc__data.trpfs.dec').read_bytes()
            self.assertEqual(b[:4], b'DTPD')
            self.assertEqual(struct.unpack_from('<QI', b, 4), (200, 1))
            self.assertEqual(b[16:], struct.pack('<QII', 4096, 7, 2) + b'xy')

    def test_wrong_answer(self):
        """An answer for other entries than it was given is refused"""
        with tempfile.TemporaryDirectory() as t:
            tmp = Path(t)
            (tmp / 'job.out').write_bytes(b'DMOR' + struct.pack('<I', 5))
            with self.assertRaisesRegex(BuildError, 'answered for other entries'): oodle_result(tmp, [('/a.gfpak', 1, 0)])

    def test_failed_entry(self):
        """A record the decoder failed on is an error, not an empty entry"""
        with tempfile.TemporaryDirectory() as t:
            tmp = Path(t)
            (tmp / 'job.out').write_bytes(b'DMOR' + struct.pack('<I', 1) + struct.pack('<I', 0))
            with self.assertRaisesRegex(BuildError, 'failed on'): oodle_result(tmp, [('/a.gfpak', 1, 0)])


class OodleWhere(unittest.TestCase):
    def test_local(self):
        """On the phone app the device is this machine: the decoder runs here, with no adb"""
        with mock.patch.dict(os.environ, {'NX2A_ON_DEVICE': '1'}): self.assertEqual(oodle_where({}), 'local')
        with mock.patch.dict(os.environ, {'NX2A_ON_DEVICE': '1'}): self.assertTrue(on_android())

    def test_device(self):
        """A computer with adb decodes on the connected device"""
        with mock.patch.dict(os.environ, {'NX2A_ON_DEVICE': '0'}): self.assertEqual(oodle_where({'adb': '/x/adb'}), 'device')

    def test_none(self):
        """A computer with no adb cannot decode at all"""
        with mock.patch.dict(os.environ, {'NX2A_ON_DEVICE': '0'}): self.assertIsNone(oodle_where({}))
        with mock.patch.dict(os.environ, {'NX2A_ON_DEVICE': '0'}): self.assertFalse(on_android())


if __name__ == '__main__':
    unittest.main()
