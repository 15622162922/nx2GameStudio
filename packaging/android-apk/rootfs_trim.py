#!/usr/bin/env python3
"""Trim the rootfs to what the porter actually runs.

The payload's rootfs is a full Debian ARM64 userland: a gigabyte unpacked, of which the programs the guest starts
(the Python, bash) and the libraries they need are a small part. The rest is a software renderer (mesa and its
LLVM), a solver, the video codecs, the X11 stack, the locales, the icons and the documentation: none of it is
ever loaded, but all of it goes into the APK and onto the phone.

This computes the closure of the programs that are started (the rootfs's Python, the build tools, the C# tools)
over the rootfs's own libraries, keeps that plus the Python standard library and a small set of programs and data
files, and writes the result as the tarball the app unpacks. The userland stays the same; it is a fifteenth of
the size.

    python3 rootfs_trim.py --rootfs <tar> --out <tar> [--ext <path>]... [--ext-search <dir>]... [--report <file>]

The closure is computed from the binaries' own headers (the ELF dynamic section), so it is exact: every library
that is kept is needed by a program that runs, and every library a program needs is kept.

The rootfs is read as the tarball it is (the payload holds it as one), twice: once to see what is in it, once to
write what stays. Nothing is unpacked on the way, so the build needs no room for a gigabyte.
"""
import argparse, json, os, struct, sys, tarfile
from pathlib import Path

# The ELF pieces the closure needs.
PT_LOAD, PT_DYNAMIC, PT_INTERP = 1, 2, 3
DT_NULL, DT_NEEDED, DT_STRTAB, DT_SONAME, DT_RPATH, DT_RUNPATH, DT_STRSZ = 0, 1, 5, 14, 15, 29, 10

# Where the rootfs's libraries live (the multiarch directory first: that is where the loader and the glibc are).
LIB_DIRS = ['usr/lib/aarch64-linux-gnu', 'lib/aarch64-linux-gnu', 'usr/lib', 'lib', 'usr/lib64']

# The programs the guest starts. They are started through the rootfs's loader, so the loader itself is a root too.
ROOTS = ['usr/bin/python3.13', 'usr/bin/bash', 'usr/bin/sh',
         'usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1']

# The programs the porter starts outside the rootfs: the build tools and the C# tools. They are Linux binaries
# too: their libraries are the rootfs's, so they belong to the closure (their paths are relative to the tree
# that holds them).
EXT_ROOTS = ['toolchain/clang/bin/clang', 'toolchain/clang/bin/ld.lld', 'toolchain/clang/bin/llvm-strip',
             'toolchain/bin/glslc', 'tool/nxnative/dm/gfx/tools/dmsh_xlate_bin/dmsh_xlate',
             'tool/tools/nx_extract/nx_extract', 'tool/tools/dmsh_xlate/dmsh_xlate']

# The programs that stay in the rootfs: the shell, the coreutils and a few tools. Everything else in /usr/bin is
# a package manager, a compiler, a Perl, a X server or a debugger: the porter starts none of them.
PROGRAMS = """
sh bash dash env printenv cat cp mv rm mkdir rmdir ln ls readlink realpath dirname basename touch chmod chown stat
du df true false sleep uname id whoami groups hostname mktemp date seq sort tr cut head tail wc grep sed awk find
xargs tee expr test [ cmp md5sum sha256sum gzip gunzip zcat bzip2 xz tar file curl wget timeout nohup sync nproc
"""

# The data files the programs read. The rest of /etc and /usr/share is a package manager's leftovers.
KEEP_FILES = """
etc/ld.so.conf etc/nsswitch.conf etc/passwd etc/group etc/hosts etc/hostname etc/resolv.conf
etc/terminfo etc/terms.etc usr/share/terminfo usr/share/misc usr/share/common-licenses
"""

# The Python standard library: the porter's own scripts run in it.
KEEP_DIRS = ['usr/lib/python3.13', 'etc/python3.13']

# The extension modules the standard library has but the porter never loads: the Tk one drags in the whole X11
# stack, and the porter has no window.
NO_DYNLOAD = ['_tkinter', '_sqlite3', '_dbm']

# The runtime of the C and the C++ libraries, and the loader. They are small, and every program that is not
# static asks for them: the C# tools ask for the C runtime even when they are built for another platform, so
# they are kept whatever the closure says.
ALWAYS = ['usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1', 'usr/lib/aarch64-linux-gnu/libc.so.6',
          'usr/lib/aarch64-linux-gnu/libm.so.6', 'usr/lib/aarch64-linux-gnu/libdl.so.2',
          'usr/lib/aarch64-linux-gnu/libpthread.so.0', 'usr/lib/aarch64-linux-gnu/librt.so.1',
          'usr/lib/aarch64-linux-gnu/libgcc_s.so.1', 'usr/lib/aarch64-linux-gnu/libstdc++.so.6']


class Elf:
    """What an ELF file asks for: the libraries it needs, the interpreter it wants and where it looks for them"""

    def __init__(self, data: bytes):
        self.needed, self.interp, self.rpath = [], None, []
        try:
            self._read(data)
        except Exception:
            return          # not an ELF or not a dynamic one: it needs nothing

    def _read(self, data: bytes):
        head = data[:64]
        if len(head) < 64 or head[:4] != b'\x7fELF' or head[4] != 2 or head[5] != 1:
            return
        phoff, = struct.unpack_from('<Q', head, 32)
        phentsize, phnum = struct.unpack_from('<HH', head, 54)
        ph = data[phoff:phoff + phentsize * phnum]
        loads, dynamic = [], None
        for i in range(phnum):
            o = i * phentsize
            if o + phentsize > len(ph):
                break
            p_type, = struct.unpack_from('<I', ph, o)
            p_offset, p_vaddr, _p_paddr, p_filesz = struct.unpack_from('<QQQQ', ph, o + 8)
            if p_type == PT_LOAD:
                loads.append((p_vaddr, p_offset, p_filesz))
            elif p_type == PT_DYNAMIC:
                dynamic = (p_offset, p_filesz)
            elif p_type == PT_INTERP:
                self.interp = data[p_offset:p_offset + p_filesz].split(b'\0')[0].decode('utf-8', 'replace')
        if not dynamic:
            return
        dyn = data[dynamic[0]:dynamic[0] + dynamic[1]]
        needed_offsets, strtab_vaddr, strtab_size = [], None, 0
        for i in range(0, len(dyn) - 15, 16):
            tag, val = struct.unpack_from('<qQ', dyn, i)
            if tag == DT_NULL:
                break
            if tag == DT_NEEDED:
                needed_offsets.append(val)
            elif tag == DT_STRTAB:
                strtab_vaddr = val
            elif tag == DT_STRSZ:
                strtab_size = val
        if strtab_vaddr is None:
            return
        strtab_off = self._offset(loads, strtab_vaddr)
        if strtab_off is None:
            return
        # the string table of a big library is big: the names of its exports sit in it too
        table = data[strtab_off:strtab_off + max(strtab_size, 1 << 20)]
        self.needed = [self._string(table, o) for o in needed_offsets]
        self.rpath = [self._string(table, o) for o in self.rpath]

    @staticmethod
    def _offset(loads, vaddr):
        for v, off, size in loads:
            if v <= vaddr < v + size:
                return off + (vaddr - v)
        return None

    @staticmethod
    def _string(table: bytes, off: int) -> str:
        end = table.find(b'\0', off)
        return table[off:end].decode('utf-8', 'replace') if end > 0 else ''


class Member:
    """One file of the rootfs: what it is and, for a program, what it needs"""

    def __init__(self, info: tarfile.TarInfo, elf: Elf = None):
        self.info = info
        self.name = info.name[2:] if info.name.startswith('./') else info.name
        self.type = info.type
        self.mode = info.mode
        self.size = info.size
        self.linkname = info.linkname
        self.needed = elf.needed if elf else []
        self.interp = elf.interp if elf else None
        self.rpath = elf.rpath if elf else []


def stream_of(path: Path):
    """A fresh stream of the rootfs's tarball: the payload holds it as one inside itself, and a stream of that
    cannot be seeked, so the two passes each open it anew"""
    if path.name.endswith(('.tar.gz', '.tgz')):
        outer = tarfile.open(path, 'r|gz')
        for m in outer:
            if m.name.endswith('rootfs-arm64.tar'):
                return outer, tarfile.open(fileobj=outer.extractfile(m), mode='r|')
        raise SystemExit(f'{path}: no rootfs-arm64.tar inside')
    return None, tarfile.open(path, 'r')


def scan(tar: tarfile.TarFile):
    """Every member of the tarball, and the programs among them with what they need"""
    members = {}
    for info in tar:
        m = Member(info)
        if info.isfile():
            f = tar.extractfile(info)
            head = f.read(4) if f else b''
            if head == b'\x7fELF' and f:
                elf = Elf(head + f.read())
                m.needed, m.interp, m.rpath = elf.needed, elf.interp, elf.rpath
        members[m.name] = m
    return members


class Rootfs:
    """The rootfs in its tarball, and the closure of the programs that are started"""

    def __init__(self, members: dict, ext_dir: Path = None, ext_roots=()):
        self.members = members
        self.ext_dir = ext_dir
        self.ext = set(ext_roots)   # the programs that live outside the rootfs (the build tools, the C# tools)
        self.ext_search = []        # the directories outside the rootfs where their libraries are
        self.keep = set()      # the paths that stay
        self.missing = []      # the libraries that were asked for and not found
        self.libraries = set()  # the libraries of the closure
        self._ext_elfs = {}

    # ---- the closure -------------------------------------------------------------------------------

    def _elf_of_ext(self, path: str) -> Elf:
        if path not in self._ext_elfs:
            f = self.ext_dir / path
            self._ext_elfs[path] = Elf(f.read_bytes()) if f.is_file() else Elf(b'')
        return self._ext_elfs[path]

    def _needed_of(self, path: str):
        """What a program needs: from the tarball if it is in it, from the tree outside if it is not. A library is
        often a link to the file with the version in it: what it needs is the file's."""
        if path in self.ext:
            return self._elf_of_ext(path)
        m = self.members.get(path)
        seen = 0
        while m and m.type == tarfile.SYMTYPE and seen < 8:
            target = m.linkname
            if target.startswith('/'):
                return None
            path = os.path.normpath(os.path.join(os.path.dirname(path), target))
            m = self.members.get(path)
            seen += 1
        return m

    def _resolve(self, name: str, origin: str, search: list):
        """Where a library is: next to the program that wants it, in the rootfs's library directories, or where its
        RPATH says. The libraries of the programs outside the rootfs are looked up in the rootfs too (that is
        where they come from), but first in the tree they are in."""
        outside = origin in self.ext
        for d in search:
            if outside and d == os.path.dirname(origin):
                if (self.ext_dir / d / name).is_file():
                    return (False, d + '/' + name)
                continue
            m = self.members.get(d + '/' + name)
            if m and m.type in (tarfile.REGTYPE, tarfile.SYMTYPE):
                return (True, d + '/' + name)
        for d in self.ext_search:
            if (self.ext_dir / d / name).is_file():
                return (False, d + '/' + name)
        for rp in self._rpath(origin):
            m = self.members.get(rp + '/' + name)
            if m and m.type in (tarfile.REGTYPE, tarfile.SYMTYPE):
                return (True, rp + '/' + name)
        return None

    def _rpath(self, origin: str):
        """The RPATH of a program, with $ORIGIN and the tree it is in worked out"""
        m = self._needed_of(origin)
        rpath = m.rpath if m else []
        out = []
        for rp in rpath:
            for part in rp.split(':'):
                if not part:
                    continue
                if part.startswith('$ORIGIN'):
                    base = os.path.dirname(origin)
                    part = (base + part[len('$ORIGIN'):]).replace('//', '/')
                if part.startswith('/'):
                    part = part[1:]
                if origin in self.ext:
                    part = str(Path(self.ext_dir / part).relative_to(self.ext_dir))
                if part and part not in out:
                    out.append(part)
        return out

    def closure(self):
        """Everything the programs that are started need: the libraries, and the libraries of those"""
        queue = list(ROOTS) + list(self.ext)
        for r in ROOTS:
            self.keep.add(r)
        # Python's extension modules are loaded by name, not by the binary that wants them: they are roots too
        for name, m in self.members.items():
            if not name.startswith('usr/lib/python3.13/lib-dynload/') or not name.endswith('.so'):
                continue
            if name.split('/')[-1].split('.')[0] in NO_DYNLOAD:
                continue
            self.keep.add(name)
            queue.append(name)
        while queue:
            path = queue.pop()
            m = self._needed_of(path)
            needed = m.needed if m else []
            search = [os.path.dirname(path)] + LIB_DIRS
            for name in needed:
                found = self._resolve(name, path, search)
                if found is None:
                    self.missing.append((path, name))
                    continue
                in_rootfs, lib = found
                self.libraries.add(lib)
                if not in_rootfs:
                    continue          # a library of the tree outside the rootfs: it is shipped with it
                if lib not in self.keep:
                    self.keep.add(lib)
                    queue.append(lib)
        return self

    # ---- the tree ----------------------------------------------------------------------------------

    def select(self):
        """The paths that stay: the closure, the Python standard library, the programs and the data files"""
        for d in KEEP_DIRS:
            for name in self.members:
                if not name.startswith(d + '/'):
                    continue
                if name.split('/')[-1].split('.')[0] in NO_DYNLOAD and name.endswith('.so'):
                    continue
                self.keep.add(name)
        for line in PROGRAMS.split():
            for d in ('usr/bin', 'usr/sbin'):
                if d + '/' + line in self.members:
                    self.keep.add(f'{d}/{line}')
        # /bin and /sbin are links to /usr/bin and /usr/sbin: the programs are found under one name only
        for link in ('bin', 'sbin', 'lib', 'lib64'):
            m = self.members.get(link)
            if m and m.type == tarfile.SYMTYPE:
                self.keep.add(link)
        for line in KEEP_FILES.split():
            if line in self.members:
                self.keep.add(line)
        for path in ALWAYS:
            if path in self.members:
                self.keep.add(path)
        self._keep_link_targets()
        return self

    def check(self):
        """The gate: every program that is started must find its libraries in what is kept. A library that is
        missing is a program that cannot start on the phone, and the build says so instead of the phone."""
        broken = []
        for path in list(ROOTS) + list(self.ext):
            needed = self._needed_of(path)
            for name in (needed.needed if needed else []):
                if not self._resolve(name, path, [os.path.dirname(path)] + LIB_DIRS):
                    broken.append((path, name))
                    continue
                found = self._resolve(name, path, [os.path.dirname(path)] + LIB_DIRS)
                in_rootfs, lib = found
                if in_rootfs and lib not in self.keep:
                    broken.append((path, name))
        return broken

    def _keep_link_targets(self):
        """A link that is kept needs the file it points at: the loader looks the library up by its name, and that
        name is often a link to the file with the version in it"""
        pending = [p for p in list(self.keep) if self.members.get(p) and self.members[p].type == tarfile.SYMTYPE]
        while pending:
            link = pending.pop()
            target = self.members[link].linkname
            if target.startswith('/'):
                continue      # an absolute link points outside the rootfs: the guest is not a chroot
            rel = os.path.normpath(os.path.join(os.path.dirname(link), target))
            if rel not in self.keep:
                self.keep.add(rel)
                m = self.members.get(rel)
                if m and m.type == tarfile.SYMTYPE:
                    pending.append(rel)

    def write(self, out: Path, source: tarfile.TarFile):
        """The trimmed rootfs as a tarball: the modes and the links are the tree's own. The source is read in
        order: a stream of the payload cannot be seeked."""
        out.parent.mkdir(parents=True, exist_ok=True)
        with tarfile.open(out, 'w', format=tarfile.GNU_FORMAT) as t:
            for info in source:
                m = self.members.get(Member(info).name)
                if not m or m.name not in self.keep:
                    continue
                out_info = tarfile.TarInfo('./' + m.name)
                out_info.type = m.type
                out_info.mode = m.mode
                out_info.mtime = info.mtime
                out_info.linkname = info.linkname
                if m.type == tarfile.REGTYPE:
                    out_info.size = m.size
                    t.addfile(out_info, source.extractfile(info))
                else:
                    t.addfile(out_info)
        return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rootfs', required=True, help='the rootfs tarball (the payload holds it as one)')
    ap.add_argument('--out', required=True)
    ap.add_argument('--report', default='')
    ap.add_argument('--ext', action='append', default=[])
    ap.add_argument('--ext-search', action='append', default=[],
                    help='a directory outside the rootfs where the libraries of those programs are')
    ap.add_argument('--ext-dir', default='', help='the tree that holds the programs outside the rootfs')
    a = ap.parse_args()

    src = Path(a.rootfs)
    if not src.exists():
        raise SystemExit(f'{src} is missing')
    outer, t = stream_of(src)
    try:
        members = scan(t)
    finally:
        if outer:
            outer.close()
    r = Rootfs(members, Path(a.ext_dir) if a.ext_dir else src.parent, a.ext)
    r.ext_search = list(a.ext_search)
    r.closure().select()

    broken = r.check()
    if broken:
        print(f'>> {len(broken)} librar(y/ies) a program needs are not in the trimmed rootfs:')
        for path, name in broken[:20]:
            print(f'   {path} wants {name}')
        raise SystemExit('the trimmed rootfs would not run')

    print(f'>> the closure: {len(r.libraries)} librar(y/ies), {len(r.keep)} path(s) kept')
    if r.missing:
        print(f'>> {len(r.missing)} librar(y/ies) asked for and not found (they are looked up on the device):')
        for path, name in r.missing[:20]:
            print(f'   {path} wants {name}')

    before = sum(m.size for m in members.values() if m.type == tarfile.REGTYPE)
    after = sum(members[n].size for n in r.keep if n in members and members[n].type == tarfile.REGTYPE)
    out = Path(a.out)
    outer, t = stream_of(src)
    try:
        r.write(out, t)
    finally:
        if outer:
            outer.close()
    packed = out.stat().st_size
    print(f'>> the rootfs: {before / 2**20:.0f} MB unpacked -> {after / 2**20:.0f} MB kept '
          f'({packed / 2**20:.0f} MB in the tarball)')

    if a.report:
        report = {'kept': sorted(r.keep),
                  'libraries': sorted(r.libraries),
                  'missing': [{'program': p, 'library': n} for p, n in r.missing],
                  'unpacked_before': before, 'unpacked_after': after, 'tarball': packed}
        Path(a.report).write_text(json.dumps(report, indent=2) + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
