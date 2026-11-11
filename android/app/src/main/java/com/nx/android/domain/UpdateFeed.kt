package com.nx.android.domain

/**
 * The repository's releases as GitHub's Atom feed writes them: every release with its tag and its notes.
 *
 * The feed is a plain web page (https://github.com/<owner>/<repo>/releases.atom), so no API and no rate
 * limit - the update check only asks it when there is a new version to describe. Each entry carries the
 * release's tag in its link and the release's own notes in its content: HTML, already rendered by GitHub
 * from the release's Markdown, so the app needs no Markdown reader of its own.
 *
 * Pure Kotlin, no Android: the JVM tests read it.
 */
object UpdateFeed {

    /** One release in the feed: its tag (as the link writes it, v0.3.0-alpha04) and its notes (HTML) */
    data class Entry(val tag: String, val notes: String)

    private val ENTRY = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""/releases/tag/([^"<>/]+)""")
    private val CONTENT = Regex("""<content[^>]*>(.*?)</content>""", RegexOption.DOT_MATCHES_ALL)
    private val CDATA = Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL)
    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);")

    /** The named entities the feed's notes use */
    private val NAMED = mapOf(
        "lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "hellip" to "\u2026", "mdash" to "\u2014", "ndash" to "\u2013",
    )

    /** The feed's releases, newest first (the feed's own order) */
    fun parse(feed: String): List<Entry> {
        if (feed.isEmpty()) return emptyList()
        return ENTRY.findAll(feed).mapNotNull { m ->
            val body = m.groupValues[1]
            val tag = TAG.find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            val notes = CONTENT.find(body)?.groupValues?.get(1)?.let { raw ->
                // CDATA's text is the HTML itself; without it the HTML arrives written as XML entities
                val cdata = CDATA.find(raw)
                if (cdata != null) cdata.groupValues[1].trim() else unescape(raw.trim())
            } ?: ""
            Entry(tag, notes)
        }.toList()
    }

    /**
     * The notes of one release (the tag with or without its leading v), null when the feed has no such
     * release. The update check says the tag without the v, the feed's link writes it with one.
     */
    fun notes(feed: String, tag: String): String? =
        parse(feed).firstOrNull { bare(it.tag) == bare(tag) }?.notes

    /** A tag without its leading v: the feed writes v0.3.0-alpha04, the update check says 0.3.0-alpha04 */
    private fun bare(tag: String): String = tag.removePrefix("v")

    /**
     * The feed's entities back to the characters they stand for. The feed's notes are HTML inside XML, so
     * every & and < in them arrives written as an entity.
     */
    fun unescape(text: String): String {
        if (!text.contains('&')) return text
        return ENTITY.replace(text) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.drop(2).toIntOrNull(16)?.toChar()?.toString() ?: m.value
                body.startsWith("#") ->
                    body.drop(1).toIntOrNull()?.toChar()?.toString() ?: m.value
                else -> NAMED[body.lowercase()] ?: m.value
            }
        }
    }
}
