# PreToolUse hook (.claude/settings.json): checks release commands against CLAUDE.md's release rules before they run.
#   gh release create|upload|edit  needs --repo zFitness/nx2androidX (gh here defaults to seedds/nxnative) and attaches no zip (this
#                                  line's release carries the phone app's APK only; the desktop bundles are built by hand when
#                                  they are wanted, and never attached to a release)
#   gh release create <tag>        and git tag <tag>: the tag is the upstream base this line carries with the line's own
#                                  pre-release stage on it: v<base>-alphaNN (or -betaNN, -rcNN), the base being
#                                  porter/__init__.py's __version__ (bump it first), or the base itself (v<base>) for the
#                                  released version of it
#   gh release create <tag>        a pre-release tag (an alpha) is published with --prerelease, so the players on the
#                                  released version are not pushed onto an alpha; the base itself is not
# A refused command gets the reason back; an allowed gh release create gets the steps that follow publishing.
import json, re, shlex, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO = 'zFitness/nx2androidX'

# The stages a pre-release can be in, and the number that says which one of the stage
STAGE = r'(?:alpha|beta|rc)\d+'


def version():
    m = re.search(r"^__version__ = '([^']+)'", (ROOT / 'porter/__init__.py').read_text(encoding='utf-8'), re.M)
    return m and m.group(1)


def tag_base(v):
    """The upstream base this line carries: the whole version in porter/__init__.py (0.3.0)"""
    return f'v{v}' if v else None


def tag_ok(tag, v):
    """Whether the tag is this line's: the base with a pre-release stage on it (v0.3.0-alpha02), or the base itself
    (v0.3.0, the released version of that base)"""
    base = tag_base(v)
    if not base: return False
    if tag == base: return True
    return bool(re.fullmatch(re.escape(base) + r'-(?:alpha|beta|rc)\d+', tag))


def is_prerelease(tag):
    """Whether the tag is a pre-release of the base: an alpha, a beta, a candidate"""
    return bool(re.search(r'-(?:alpha|beta|rc)\d+$', tag))


def has_prerelease(w):
    """Whether the command asks for a pre-release"""
    for a in w:
        if a == '--prerelease': return True
        if a.startswith('--prerelease='):
            return a.split('=', 1)[1].lower() not in ('false', '0', 'no')
    return False


def words(cmd):
    try: return shlex.split(cmd, posix=True)
    except ValueError: return cmd.split()


def segments(cmd):
    """The simple commands of a shell line (split on ; && || | and newlines), as word lists."""
    return [words(s) for s in re.split(r'&&|\|\||[;|\n]', cmd) if s.strip()]


def check_gh(w):
    sub = w[2] if len(w) > 2 else ''
    if sub not in ('create', 'upload', 'edit'): return None
    repo = next((w[i + 1] for i, a in enumerate(w[:-1]) if a in ('--repo', '-R')), None) or next((a.split('=', 1)[1] for a in w if a.startswith('--repo=')), None)
    if repo != REPO:
        return f'gh release {sub} needs --repo {REPO}: gh in this checkout defaults to seedds/nxnative, which is not this project.'
    if sub in ('create', 'upload') and any(a.lower().endswith('.zip') or '.zip#' in a.lower() for a in w):
        return ('Do not attach the bundle zips by hand: this line\'s release carries the phone app\'s APK only, and the desktop '
                'bundles are built by hand when they are wanted (CLAUDE.md, Releases). Create the release without a zip.')
    if sub == 'create':
        tag = next((a for a in w[3:] if not a.startswith('-')), None)
        v = version()
        if tag and v and not tag_ok(tag, v):
            base = tag_base(v)
            return (f'The tag {tag} is not this line\'s: {base} (the upstream base in porter/__init__.py), '
                    f'{base}-alphaNN for a pre-release of it, or {base} itself for the released version of it. '
                    'Bump the base in porter/__init__.py in a commit "chore(release): version <x.y.z>", and set '
                    "the app's versionName (android/app/build.gradle.kts) to the tag without the v.")
        if tag and is_prerelease(tag) and not has_prerelease(w):
            return (f'{tag} is a pre-release: publish it with --prerelease, so the players on the released version are '
                    'not pushed onto an alpha (CLAUDE.md, Releases).')
        if tag and not is_prerelease(tag) and has_prerelease(w):
            return (f'{tag} is the released version of the base, not a pre-release: it must not carry --prerelease '
                    '(CLAUDE.md, Releases).')
    return None


def check_git_tag(w):
    # any tag that looks like a version is checked, so the retired shapes are refused too
    tags = [a for a in w[2:] if re.fullmatch(r'v\d+\.\d+(\.\d+)?(-[A-Za-z][\w.-]*)?', a)]
    if not tags or any(a in ('-d', '--delete', '-l', '--list', '-v', '--verify') for a in w): return None
    v = version()
    if v and not tag_ok(tags[0], v):
        base = tag_base(v)
        return (f'The tag {tags[0]} is not this line\'s: {base} (the upstream base in porter/__init__.py), '
                f'{base}-alphaNN for a pre-release of it, or {base} itself for the released version of it.')
    return None


AFTER = ('Release rules (CLAUDE.md, Releases): the release carries the phone app\'s APK, which .github/workflows/android-apk.yml '
         'builds and attaches when the release is published; watch that run (gh run list --repo ' + REPO + ' --limit 4) and check '
         'that the APK is on the release - a failed run means the release has no app in it. A pre-release does not become the '
         'repository\'s "Latest" release, so the app\'s update check stays on the released version.')


def main():
    data = json.load(sys.stdin)
    cmd = (data.get('tool_input') or {}).get('command') or ''
    if 'release' not in cmd and 'tag' not in cmd: return
    creates = False
    for w in segments(cmd):
        while w and re.fullmatch(r'\w+=.*', w[0]): w = w[1:]   # VAR=value prefixes
        reason = None
        if len(w) > 1 and Path(w[0]).stem == 'gh' and w[1] == 'release':
            reason = check_gh(w); creates |= reason is None and len(w) > 2 and w[2] == 'create'
        elif len(w) > 1 and Path(w[0]).stem == 'git' and w[1] == 'tag':
            reason = check_git_tag(w)
        if reason:
            print(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': 'deny', 'permissionDecisionReason': reason}}))
            return
    if creates:
        print(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'additionalContext': AFTER}}))


if __name__ == '__main__':
    main()
