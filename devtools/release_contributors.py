# The contributors to name in a release's notes: the pull requests merged since the last
# release tag, and who opened them. A release's notes carry a "Community ports" section
# naming the people whose work went into it.
#
#   python3 devtools/release_contributors.py            # markdown, ready to paste
#   python3 devtools/release_contributors.py --json     # the same, as JSON
#   python3 devtools/release_contributors.py --since v0.3.0-alpha04
#
# It reads the repository's tags and asks the GitHub CLI for the merged pull requests, so
# the gh CLI has to be signed in with read access to the repository.
import json
import subprocess
import sys

REPO = "zFitness/nx2androidX"


def sh(*args):
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        sys.exit(result.returncode)
    return result.stdout


def last_tag():
    """The newest release tag: v<base>-alphaNN or v<base>, by version order."""
    tags = [t for t in sh("git", "tag", "--list", "v*", "--sort=-v:refname").split() if t]
    return tags[0] if tags else None


def tag_date(tag):
    """The commit date of a tag: the cutoff for the merged pull requests."""
    return sh("git", "log", "-1", "--format=%cI", tag).strip()


def merged_pulls(since):
    """The merged pull requests, newest first, filtered by the date locally: the search
    qualifier's date format differs between GitHub's endpoints, so the filter is ours."""
    pulls = json.loads(sh("gh", "pr", "list", "--repo", REPO, "--state", "merged", "--limit", "100",
                           "--json", "number,title,author,mergedAt"))
    if not since:
        return pulls
    return [p for p in pulls if p["mergedAt"] and p["mergedAt"] >= since]


def main():
    args = [a for a in sys.argv[1:] if a not in ("--json",)]
    tag = None
    for arg in args:
        if arg.startswith("--since="):
            tag = arg.split("=", 1)[1]
    if tag is None:
        tag = last_tag()
    since = tag_date(tag) if tag else None
    pulls = merged_pulls(since)

    if "--json" in sys.argv:
        print(json.dumps({"since": since, "pulls": pulls}, indent=2))
        return

    print(f"<!-- since {tag} ({since}) -->")
    if not pulls:
        print(f"<!-- no pull requests merged since {tag} -->")
        return
    print()
    for pull in sorted(pulls, key=lambda p: p["mergedAt"]):
        author = pull["author"]["login"] if pull["author"] else "?"
        print(f"- **@{author}** — #{pull['number']}: {pull['title']}")


if __name__ == "__main__":
    main()
