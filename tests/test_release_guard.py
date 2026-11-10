# The release guard: the rules of a release on this line, checked before the command runs
# (.claude/hooks/release_guard.py, wired in .claude/settings.json).
#
# What it holds:
#   1. the tag is this line's: the upstream base (porter/__init__.py's __version__) with a pre-release stage on
#      it, or the base itself
#   2. a pre-release is published with --prerelease, so it does not become the repository's "Latest" release
#      and push the players on the released version onto an alpha
#   3. the release is made in this line's repository, and carries the phone app's APK only (no zip)
import importlib.util, json, subprocess, sys, unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOOK = ROOT / '.claude' / 'hooks' / 'release_guard.py'

spec = importlib.util.spec_from_file_location('release_guard', HOOK)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def decide(command: str):
    """What the hook says about a command: None when it lets it through, the reason when it refuses"""
    out = subprocess.run([sys.executable, str(HOOK)], input=json.dumps({'tool_input': {'command': command}}),
                         capture_output=True, text=True)
    if not out.stdout.strip():
        return None
    hook = json.loads(out.stdout.strip())['hookSpecificOutput']
    if hook.get('permissionDecision') == 'deny':
        return hook.get('permissionDecisionReason', '')
    return None      # let through (an allowed release create also gets the steps that follow)


class TagShapeTest(unittest.TestCase):
    """1. the tag is this line's"""

    def test_the_tags_of_this_line_are_allowed(self):
        for tag in ('v0.3.0-alpha01', 'v0.3.0-alpha02', 'v0.3.0-beta01', 'v0.3.0-rc01', 'v0.3.0'):
            self.assertIsNone(decide(f'git tag -a {tag} -m "x"'), tag)

    def test_another_base_is_refused(self):
        for tag in ('v0.4.0-alpha01', 'v0.3.1-alpha01', 'v0.3-android', 'v0.3.0-android', 'v1.0.0'):
            self.assertIsNotNone(decide(f'git tag -a {tag} -m "x"'), tag)

    def test_the_base_comes_from_the_porter(self):
        base = guard.tag_base(guard.version())
        self.assertEqual(base, 'v0.3.0')
        self.assertTrue(guard.tag_ok('v0.3.0-alpha02', '0.3.0'))
        self.assertFalse(guard.tag_ok('v0.4.0-alpha02', '0.3.0'))


class PreReleaseTest(unittest.TestCase):
    """2. a pre-release does not take the released version's place"""

    def test_an_alpha_is_published_as_a_pre_release(self):
        reason = decide('gh release create v0.3.0-alpha02 --repo zFitness/nx2androidX')
        self.assertIsNotNone(reason)
        self.assertIn('--prerelease', reason)

    def test_an_alpha_with_the_flag_is_allowed(self):
        self.assertIsNone(decide('gh release create v0.3.0-alpha02 --prerelease --repo zFitness/nx2androidX'))

    def test_the_released_version_is_not_a_pre_release(self):
        reason = decide('gh release create v0.3.0 --prerelease --repo zFitness/nx2androidX')
        self.assertIsNotNone(reason)
        self.assertIsNone(decide('gh release create v0.3.0 --repo zFitness/nx2androidX'))

    def test_the_flag_is_read_as_it_is_written(self):
        self.assertTrue(guard.has_prerelease(['--prerelease']))
        self.assertTrue(guard.has_prerelease(['--prerelease=true']))
        self.assertFalse(guard.has_prerelease(['--prerelease=false']))
        self.assertFalse(guard.has_prerelease([]))

    def test_putting_a_release_back_to_a_draft_keeps_the_flag(self):
        # publishing a draft without --prerelease drops the flag: the release becomes the repository's "Latest"
        # and the players on the released version are pushed onto an alpha
        for command in ('gh release edit v0.3.0-alpha03 --draft=true --repo zFitness/nx2androidX',
                        'gh release edit v0.3.0-alpha03 --draft=false --repo zFitness/nx2androidX',
                        'gh release edit v0.3.0-alpha03 --draft --repo zFitness/nx2androidX'):
            reason = decide(command)
            self.assertIsNotNone(reason, command)
            self.assertIn('--prerelease', reason)

    def test_the_draft_round_trip_that_keeps_the_flag_is_allowed(self):
        for command in ('gh release edit v0.3.0-alpha03 --draft=true --prerelease=true --repo zFitness/nx2androidX',
                        'gh release edit v0.3.0-alpha03 --draft=false --prerelease=true --repo zFitness/nx2androidX'):
            self.assertIsNone(decide(command), command)

    def test_the_released_version_is_not_affected(self):
        self.assertIsNone(decide('gh release edit v0.3.0 --draft=false --repo zFitness/nx2androidX'))

    def test_an_edit_that_publishes_nothing_is_left_alone(self):
        self.assertIsNone(decide('gh release edit v0.3.0-alpha03 --notes-file notes.md --repo zFitness/nx2androidX'))


class ReleaseTargetTest(unittest.TestCase):
    """3. the release is made in this line's repository, and carries the APK only"""

    def test_the_repository_is_required(self):
        reason = decide('gh release create v0.3.0-alpha02 --prerelease')
        self.assertIsNotNone(reason)
        self.assertIn('zFitness/nx2androidX', reason)

    def test_a_zip_is_refused(self):
        reason = decide('gh release create v0.3.0-alpha02 --prerelease --repo zFitness/nx2androidX nx2android.zip')
        self.assertIsNotNone(reason)
        self.assertIn('APK', reason)

    def test_the_apk_is_not_refused(self):
        self.assertIsNone(decide('gh release upload v0.3.0-alpha02 app.apk --repo zFitness/nx2androidX'))


if __name__ == '__main__':
    unittest.main()
