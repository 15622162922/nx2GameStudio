"""The shipped tree's content, including late-staged hooks, controls environment refresh."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('phone_assets', Path(__file__).resolve().parents[1] / 'packaging/android-apk/build_assets.py')
assets = importlib.util.module_from_spec(spec)
spec.loader.exec_module(assets)


class PayloadStampTest(unittest.TestCase):
    def test_same_size_and_late_runtime_components_change_stamp(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'tool/porter/revision.py'
            source.parent.mkdir(parents=True)
            source.write_bytes(b'old')
            assets.finalize_metadata(root)
            before = (root / 'build-id').read_text()
            source.write_bytes(b'new')
            assets.finalize_metadata(root)
            after = (root / 'build-id').read_text()
            self.assertNotEqual(before, after)
            hook = root / 'tool/tools/adrenotools/libhook_impl.so'
            hook.parent.mkdir(parents=True)
            hook.write_bytes(b'hook')
            assets.finalize_metadata(root)
            self.assertNotEqual(after, (root / 'build-id').read_text())
            self.assertIn('libhook_impl.so', (root / 'modes.json').read_text())
