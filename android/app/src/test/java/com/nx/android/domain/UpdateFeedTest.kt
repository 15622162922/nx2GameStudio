package com.nx.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release feed: the releases of the repository with their own notes, as GitHub's Atom feed writes them.
 */
class UpdateFeedTest {

    /** A feed shaped like the repository's own: a title that says more than the tag, and notes as HTML */
    private val feed = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Release notes from nx2androidX</title>
          <entry>
            <id>tag:github.com,2008:Repository/1450766338/v0.3.0-alpha04</id>
            <link rel="alternate" type="text/html" href="https://github.com/zFitness/nx2androidX/releases/tag/v0.3.0-alpha04"/>
            <title>v0.3.0-alpha04</title>
            <content type="html">&lt;h2&gt;Changelog&lt;/h2&gt;
&lt;ul&gt;
&lt;li&gt;&lt;strong&gt;The pack is built in the app&lt;/strong&gt; (&lt;code&gt;files/ports&lt;/code&gt;).&lt;/li&gt;
&lt;/ul&gt;</content>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1450766338/v0.3.0-alpha02</id>
            <link rel="alternate" type="text/html" href="https://github.com/zFitness/nx2androidX/releases/tag/v0.3.0-alpha02"/>
            <title>v0.3.0-alpha02: The Android line&#39;s second alpha</title>
            <content type="html">&lt;p&gt;The second alpha.&lt;/p&gt;</content>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `every release of the feed is read`() {
        val entries = UpdateFeed.parse(feed)
        assertEquals(2, entries.size)
        assertEquals("v0.3.0-alpha04", entries[0].tag)
        assertEquals("v0.3.0-alpha02", entries[1].tag)
    }

    @Test
    fun `the notes are the release's own text`() {
        val notes = UpdateFeed.notes(feed, "v0.3.0-alpha04") ?: ""
        assertTrue(notes.startsWith("<h2>Changelog</h2>"))
        assertTrue(notes.contains("<strong>The pack is built in the app</strong>"))
        assertTrue(notes.contains("<code>files/ports</code>"))
    }

    @Test
    fun `the tag is found with or without its v`() {
        assertTrue(UpdateFeed.notes(feed, "0.3.0-alpha04") != null)
        assertTrue(UpdateFeed.notes(feed, "v0.3.0-alpha02")!!.contains("The second alpha."))
        assertNull(UpdateFeed.notes(feed, "v0.3.0-alpha09"))
    }

    @Test
    fun `the entities become the characters they stand for`() {
        val notes = UpdateFeed.notes(feed, "v0.3.0-alpha02") ?: ""
        assertEquals("<p>The second alpha.</p>", notes)
        assertEquals("a & b < c > d \" e ' f", UpdateFeed.unescape("a &amp; b &lt; c &gt; d &quot; e &#39; f"))
    }

    @Test
    fun `a release without notes has none`() {
        val bare = """
            <feed><entry>
              <link href="https://github.com/zFitness/nx2androidX/releases/tag/v0.4.0"/>
              <title>v0.4.0</title>
            </entry></feed>
        """.trimIndent()
        assertEquals("", UpdateFeed.notes(bare, "v0.4.0"))
    }

    @Test
    fun `what is not a feed is not read`() {
        assertTrue(UpdateFeed.parse("").isEmpty())
        assertTrue(UpdateFeed.parse("not xml at all").isEmpty())
        assertNull(UpdateFeed.notes("<html><body>404</body></html>", "v0.3.0-alpha04"))
    }
}
