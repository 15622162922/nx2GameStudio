# The interpreter of the guest programs: Android has no /lib, so the programs the porter starts itself have to
# point at the app's own rootfs (packaging/android-apk/patch_interp.py).
import importlib.util, os, struct, unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / 'packaging' / 'android-apk' / 'patch_interp.py'
spec = importlib.util.spec_from_file_location('patch_interp', SCRIPT)
patch_interp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch_interp)


def elf(interp: bytes) -> bytes:
    """A minimal 64-bit little-endian ELF with one PT_INTERP segment"""
    phoff, phentsize = 64, 56
    ident = b'\x7fELF' + bytes([2, 1, 1]) + bytes(9)
    header = ident + struct.pack('<HHIQQQIHHHHHH', 2, 0x3e, 1, 0, phoff, 0, 0, 64, phentsize, 1, 0, 0, 0)
    body = bytearray(b"\0" * 8192)
    body[0:len(header)] = header
    # PT_INTERP: type 3, flags 4, offset, vaddr, paddr, filesz, memsz, align
    struct.pack_into('<IIQQQQQQ', body, phoff, 3, 4, 1024, 1024, 1024, len(interp), len(interp), 1)
    body[1024:1024 + len(interp)] = interp
    return bytes(body)


class PatchInterp(unittest.TestCase):
    def setUp(self):
        self.dir = Path(self.enterContext(__import__('tempfile').TemporaryDirectory()))
        self.tmp = self.dir / 'prog'

    def test_longer_path(self):
        target = '/data/user/0/ro.cobrabm.nx2android/files/rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1'
        self.tmp.write_bytes(elf(b'/lib/ld-linux-aarch64.so.1\0'))
        self.assertTrue(patch_interp.patch(self.tmp, target))
        got = patch_interp._interp_of(self.tmp)
        self.assertIsNotNone(got)
        self.assertEqual(target, got[3])
        self.assertEqual(len(target) + 1, got[2])   # the segment's size follows the new string

    def test_already_there(self):
        self.tmp.write_bytes(elf(b'/lib/ld-linux-aarch64.so.1\0'))
        self.tmp.chmod(0o755)
        self.assertFalse(patch_interp.patch(self.tmp, '/lib/ld-linux-aarch64.so.1'))

    def test_twice_is_the_same(self):
        # the second round must find the new path and leave the file alone: a file that grows every time
        # would fill the disk
        target = '/data/user/0/com.nx.android/files/rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1'
        self.tmp.write_bytes(elf(b'/lib/ld-linux-aarch64.so.1\0'))
        self.assertTrue(patch_interp.patch(self.tmp, target))
        self.tmp.chmod(0o755)
        size = self.tmp.stat().st_size
        self.assertFalse(patch_interp.patch(self.tmp, target))
        self.assertEqual(size, self.tmp.stat().st_size)
        self.assertEqual(target, patch_interp._interp_of(self.tmp)[3])

    def test_the_header_survives(self):
        # the program header is written at its own place: a wrong offset would break the file
        target = '/data/user/0/com.nx.android/files/rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1'
        self.tmp.write_bytes(elf(b'/lib/ld-linux-aarch64.so.1\0'))
        patch_interp.patch(self.tmp, target)
        data = self.tmp.read_bytes()
        self.assertEqual(2, struct.unpack_from('<H', data, 16)[0])       # e_type: the one the file had
        self.assertEqual(64, struct.unpack_from('<Q', data, 32)[0])       # e_phoff: where the headers are
        self.assertEqual(3, struct.unpack_from('<I', data, 64)[0])        # the PT_INTERP segment is still there
        self.assertEqual(target.encode() + b'\0', data[struct.unpack_from('<Q', data, 72)[0]:][:len(target) + 1])

    def test_the_execute_bit_comes_back(self):
        # a program that lost its execute bit cannot be started: the patch gives it back
        target = '/data/user/0/com.nx.android/files/rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1'
        self.tmp.write_bytes(elf(b'/lib/ld-linux-aarch64.so.1\0'))
        patch_interp.patch(self.tmp, target)
        self.tmp.chmod(0o600)
        self.assertTrue(patch_interp.patch(self.tmp, target))     # the bit is what changed
        self.assertTrue(os.access(self.tmp, os.X_OK))


if __name__ == '__main__':
    unittest.main()
