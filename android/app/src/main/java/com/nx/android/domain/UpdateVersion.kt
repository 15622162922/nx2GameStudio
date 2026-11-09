package com.nx.android.domain

/**
 * The fork's own version: the upstream base it carries, plus the fork's own revision.
 *
 * The release tag and the app's own versionName are the same thing, written two ways - the tag carries a
 * leading "v":
 *
 *     v0.3-android        the upstream 0.3, the fork's first release
 *     v0.3-android.1      the same, the fork's first own revision
 *
 * The upstream's own tags (v0.3.0) are not this shape and are not read: the fork's releases are the ones
 * the app looks for. Free of Android, so the tests run on the JVM.
 */
data class ForkVersion(val major: Int, val minor: Int, val revision: Int) : Comparable<ForkVersion> {

    override fun compareTo(other: ForkVersion): Int =
        compareValuesBy(this, other, ForkVersion::major, ForkVersion::minor, ForkVersion::revision)

    /** The canonical writing: what the app remembers and what the notice shows */
    override fun toString(): String = "$major.$minor-android" + (if (revision > 0) ".$revision" else "")
}

object UpdateVersion {

    /** The release page of the fork: it redirects to the newest release's tag */
    const val RELEASES_LATEST_URL = "https://github.com/zFitness/nx2androidX/releases/latest"

    /** The fork's own shape; the upstream's v0.3.0 and anything unreadable are not it */
    private val TAG = Regex("^v?(\\d+)\\.(\\d+)-android(?:\\.(\\d+))?$", RegexOption.IGNORE_CASE)

    /** The plain shape the older builds carry in their versionName (0.1.0) */
    private val PLAIN = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")

    /** A tag or a versionName read as a version; null when it is not the fork's shape */
    fun parse(text: String): ForkVersion? {
        val m = TAG.matchEntire(text.trim()) ?: return null
        return ForkVersion(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
        )
    }

    /**
     * The app's own version: the fork's shape, or the plain X.Y.Z the older builds carry (0.1.0). The app
     * knows what it is - it only has to be able to say which release is newer than it. A release's tag has
     * to be the fork's own to count as a release at all.
     */
    fun parseOwn(text: String): ForkVersion? = parse(text) ?: PLAIN.matchEntire(text.trim())?.let { m ->
        ForkVersion(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
        )
    }

    /**
     * The tag out of the address the release page ended at: its last piece, without the leading "v".
     * Null when the address holds no tag of ours (the page moved, the repository itself answered, or
     * the tag is written in another shape) - the app would rather stay quiet than nag.
     */
    fun tagFromUrl(url: String): String? {
        val last = url.trim().trimEnd('/').substringAfterLast('/')
        if (last.isEmpty()) return null
        val tag = if (last.length > 1 && last[0] == 'v' && last[1].isDigit()) last.substring(1) else last
        return if (parse(tag) != null) tag else null
    }

    /**
     * Whether the release is newer than what the app has. Numbers, not strings: "0.10" is newer than
     * "0.9", and a release that is older than the app (a build ahead of the releases) is not an update.
     * Anything unreadable is not an update either - the app would rather stay quiet than nag.
     */
    fun isNewer(release: String, mine: String): Boolean {
        val r = parse(release) ?: return false
        val m = parseOwn(mine) ?: return false
        return r > m
    }
}
