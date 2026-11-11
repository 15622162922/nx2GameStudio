package com.nx.android.data

import com.nx.android.domain.UpdateVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Which release is the newest one.
 *
 * Not the GitHub API: an unauthenticated API gives 60 requests per hour per address, and the phones sit
 * behind the same address more often than not - the driver catalogue stopped using the API for the same
 * reason. The release page itself redirects to the newest release's tag, so following that redirect and
 * reading the last piece of the address is enough. It costs one request and no JSON.
 */
object UpdateChecker {

    private const val UA = "nx2android"

    /** The feed of a repository is a few tens of kilobytes; more than this is not read */
    private const val MAX_BODY = 4 shl 20

    /**
     * The tag of the newest release. A failure is a failure: no release yet, no network, or the page
     * answered something else - the caller stays quiet about it.
     */
    suspend fun latestTag(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val finalUrl = head(UpdateVersion.RELEASES_LATEST_URL)
                ?: get(UpdateVersion.RELEASES_LATEST_URL)
                ?: throw IOException("no answer")
            UpdateVersion.tagFromUrl(finalUrl) ?: throw IOException("no tag in $finalUrl")
        }
    }

    /** HEAD: almost no traffic. Some gateways refuse it (405) - then the GET below answers. */
    private fun head(url: String): String? = try {
        request(url, "HEAD")
    } catch (e: IOException) {
        null
    }

    private fun get(url: String): String? = try {
        request(url, "GET")
    } catch (e: IOException) {
        null
    }

    /**
     * The feed of the repository's releases (Atom): every release with its own notes. A plain web page, so
     * no API's rate limit - asked only when there is a new version to describe.
     */
    suspend fun releaseFeed(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { body(UpdateVersion.RELEASES_FEED_URL) ?: throw IOException("no answer") }
    }

    /** The page's own text; a failure is a failure - the caller stays quiet about it */
    private fun body(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            String(readCapped(conn.inputStream, MAX_BODY), Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        null
    }

    /**
     * The answer, but not more than max bytes: a feed that suddenly weighs a gigabyte does not get to
     * fill the phone's memory (the feed of a repository is a few tens of kilobytes).
     */
    private fun readCapped(stream: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            total += n
            if (total > max) throw IOException("the answer is larger than $max bytes")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** The address the request ended at - after the redirect, that is the release's tag address */
    private fun request(url: String, method: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.url?.toString()
        } finally {
            conn.disconnect()
        }
    }
}
