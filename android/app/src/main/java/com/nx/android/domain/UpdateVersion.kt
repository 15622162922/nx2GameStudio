package com.nx.android.domain

/**
 * The fork's own version: the upstream base it carries, the stage of the pre-release, and that stage's
 * number.
 *
 *     v0.3.0-alpha02   the upstream base 0.3.0, the fork's second alpha
 *     v0.3.0           the upstream base 0.3.0, released
 *     v0.3-android     the fork's first alpha, in the writing the first release used (read, not written)
 *
 * The app only looks at the releases of this repository; another shape is not a release of ours and is
 * not read. Free of Android, so the tests run on the JVM.
 */

/** How far along the base the fork is: the pre-release stages first, the released one last */
enum class Stage(val rank: Int) {
    ALPHA(0), BETA(1), RC(2), FINAL(3);

    companion object {
        /** The stage a name gives; null when the name is not a stage */
        fun of(name: String): Stage? = when (name.lowercase()) {
            "alpha" -> ALPHA
            "beta" -> BETA
            "rc" -> RC
            "" -> FINAL
            else -> null
        }
    }
}

/**
 * A version of the fork. The base is the upstream version the line carries (0.3.0), the stage says how far
 * along that base it is (an alpha, a beta, a release candidate, or released), and the number says which one
 * of that stage (the second alpha).
 */
data class ForkVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Stage = Stage.FINAL,
    val stageNumber: Int = 0,
) : Comparable<ForkVersion> {

    override fun compareTo(other: ForkVersion): Int {
        val byBase = compareValuesBy(this, other, ForkVersion::major, ForkVersion::minor, ForkVersion::patch)
        if (byBase != 0) return byBase
        val byStage = stage.rank - other.stage.rank
        if (byStage != 0) return byStage
        return stageNumber - other.stageNumber
    }

    /** The canonical writing: what the app remembers and what the notice shows */
    override fun toString(): String = "$major.$minor.$patch" +
        if (stage == Stage.FINAL) "" else "-${stage.name.lowercase()}${stageNumber.toString().padStart(2, '0')}"
}

object UpdateVersion {

    /** The fork's own page on GitHub: the source, the issues and the releases all live there */
    const val REPO_URL = "https://github.com/zFitness/nx2androidX"

    /** The release page of the fork: it redirects to the newest release's tag */
    const val RELEASES_LATEST_URL = "$REPO_URL/releases/latest"

    /** The fork's own shape: the base with a stage on it (v0.3.0-alpha02), or the base alone (v0.3.0) */
    private val TAG = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-z]+)(\\d+))?$", RegexOption.IGNORE_CASE)

    /** The shape the first release was written in (v0.3-android) */
    private val LEGACY = Regex("^v?(\\d+)\\.(\\d+)-android(?:\\.\\d+)?$", RegexOption.IGNORE_CASE)

    /**
     * A tag read as a version; null when it is not the fork's shape. The first release's own writing is
     * read as the base's first alpha, so it is older than the alphas that came after it.
     */
    fun parse(text: String): ForkVersion? {
        val t = text.trim()
        TAG.matchEntire(t)?.let { m ->
            val stage = Stage.of(m.groupValues[4]) ?: return null
            return ForkVersion(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
                stage,
                m.groupValues[5].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
            )
        }
        LEGACY.matchEntire(t)?.let { m ->
            return ForkVersion(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                0,
                Stage.ALPHA,
                1,
            )
        }
        return null
    }

    /**
     * The app's own version: the same shapes a release's tag can have. The older builds carry the plain
     * X.Y.Z in their versionName (0.1.0) and the first release carried 0.3-android - both are read, so an
     * app from before knows what the releases are like.
     */
    fun parseOwn(text: String): ForkVersion? = parse(text)

    /**
     * The tag out of the address the release page ended at: its last piece, without the leading "v".
     * Null when the address holds no tag of ours (the page moved, the repository itself answered, or the
     * tag is written in another shape) - the app would rather stay quiet than nag.
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
