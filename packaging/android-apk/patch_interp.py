#!/usr/bin/env python3
"""Set the ELF interpreter (PT_INTERP) of the binaries a package runs on the device.

Android has no /lib/ld-linux-aarch64.so.1 and the guest is not a chroot (nixport runs the programs as they are,
without proot), so a Linux binary that is to run in the guest needs its interpreter path pointed at the host's
rootfs, the way the ports built for nixport do it at link time (the rootfs's own binaries carry that path already).

A longer path than the one in the file is fine: the new string is appended to the file and the program header
points at it, so nothing else moves.

    python3 patch_interp.py <file or folder> <interpreter path> [--dry-run] [--verbose]

Idempotent: a file that already has the path is left alone. Files without an interpreter (static binaries) are
skipped. Only the header of a file is read when it does not need to change, so a tree of a few thousand files
goes over in seconds.
"""
import argparse, os, struct, sys, time
from pathlib import Path

PT_INTERP = 3
ELF = b'\x7fELF'


def _interp_of(path):
    """The interpreter a file asks for, read from its header only"""
    with path.open('rb') as f:
        head = f.read(64)
        if len(head) < 64 or head[:4] != ELF: return None
        cls = head[4]
        if cls == 2:
            phoff, = struct.unpack_from('<Q', head, 32); phentsize, phnum = struct.unpack_from('<HH', head, 54)
        elif cls == 1:
            phoff, = struct.unpack_from('<I', head, 28); phentsize, phnum = struct.unpack_from('<HH', head, 42)
        else: return None
        f.seek(phoff)
        ph = f.read(phentsize * phnum)
        if len(ph) < phentsize * phnum: return None
        for i in range(phnum):
            p = phoff + i * phentsize      # where the header sits in the file
            q = i * phentsize              # and where it sits in the block that was read
            p_type, = struct.unpack_from('<I', ph, q)
            if p_type != PT_INTERP: continue
            if cls == 2:
                p_offset, = struct.unpack_from('<Q', ph, q + 8)
                p_filesz, = struct.unpack_from('<Q', ph, q + 32)
            else:
                p_offset, = struct.unpack_from('<I', ph, q + 4)
                p_filesz, = struct.unpack_from('<I', ph, q + 16)
            f.seek(p_offset)
            s = f.read(p_filesz).split(b'\0')[0].decode('utf-8', 'replace')
            return p, p_offset, p_filesz, s
    return None


def _ensure_executable(path):
    """A program with an interpreter is a program: it has to be executable. The write of an earlier round (or
    a file that came without the bit) would leave it unstartable."""
    if os.name != 'posix':
        return False        # Windows has no execute bit: there is nothing to give back, and nothing changed
    try:
        mode = os.stat(path).st_mode & 0o777
        if mode & 0o111: return False
        os.chmod(path, mode | 0o111)
        return True
    except OSError:
        return False


def patch(path, interp, dry=False, verbose=False):
    """Sets the interpreter of one file; True when the file changed"""
    got = _interp_of(path)
    if not got: return False
    p, p_offset, p_filesz, cur = got
    if cur == interp:
        # the interpreter is right: the file may still have lost its execute bit
        return _ensure_executable(path)
    if verbose:
        print(f'  {path}: {cur} -> {interp}' if cur else f'  {path}: {interp}')
    if dry: return True
    data = path.read_bytes()
    if len(data) < 64 or data[:4] != ELF: return False
    new = interp.encode() + b'\0'
    at = (len(data) + 7) & ~7
    data = bytearray(data)
    data += b'\0' * (at - len(data))
    data += new
    cls = data[4]
    # the program header: p_offset, then p_filesz and p_memsz (the string's length)
    if cls == 2:
        struct.pack_into('<Q', data, p + 8, at)          # p_offset
        struct.pack_into('<Q', data, p + 32, len(new))    # p_filesz
        struct.pack_into('<Q', data, p + 40, len(new))    # p_memsz
    else:
        struct.pack_into('<I', data, p + 4, at)          # p_offset
        struct.pack_into('<I', data, p + 16, len(new))    # p_filesz
        struct.pack_into('<I', data, p + 20, len(new))    # p_memsz
    # the write goes to a temporary file and then replaces the original: a program that dies in the middle
    # leaves the old binary alone instead of a half written one. The new file keeps the old one's permissions:
    # a program that is not executable any more cannot be started.
    tmp = path.with_name(path.name + '.interp-tmp')
    tmp.write_bytes(bytes(data))
    os.chmod(tmp, os.stat(path).st_mode & 0o777)
    os.replace(tmp, path)
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('path')
    ap.add_argument('interp')
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--verbose', action='store_true')
    a = ap.parse_args()
    root = Path(a.path)
    files = [root] if root.is_file() else sorted(p for p in root.rglob('*') if p.is_file() and not p.is_symlink())
    t0 = time.time()
    n = 0
    for f in files:
        try:
            if patch(f, a.interp, a.dry_run, a.verbose): n += 1
        except (OSError, ValueError) as e:
            print(f'  {f}: skipped ({e})')
    print(f'patched {n} file(s) in {root} ({len(files)} file(s) seen, {time.time() - t0:.1f}s)'
          + (' (dry run)' if a.dry_run else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
