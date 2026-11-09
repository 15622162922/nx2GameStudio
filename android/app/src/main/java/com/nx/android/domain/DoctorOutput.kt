package com.nx.android.domain

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The doctor's output out of the runner's stream.
 *
 * The runner writes its own lines into the same stream - the interpreter paths are being rewritten in front
 * of the doctor, the exit code may follow it - and only what the doctor itself printed is JSON. The rows are
 * found by trying every '[' in the stream: the doctor's array is the one that parses, the runner's lines are
 * not. Free of Android, so the tests run on the JVM.
 */
object DoctorOutput {

    /** The rows the doctor printed, or null when the stream holds no JSON */
    fun rows(stream: String): JSONArray? {
        var i = stream.indexOf('[')
        while (i >= 0) {
            val rows = runCatching { JSONTokener(stream.substring(i)).nextValue() as? JSONArray }.getOrNull()
            // the runner's own lines are leniently read as an array of one word ("[RED] ..."), so only an
            // array of rows - of objects - is the doctor's
            if (rows != null && rows.length() > 0 && allObjects(rows)) return rows
            i = stream.indexOf('[', i + 1)
        }
        return null
    }

    /** Whether every row is an object, the way the doctor prints them */
    private fun allObjects(rows: JSONArray): Boolean {
        for (i in 0 until rows.length()) {
            if (rows.optJSONObject(i) == null) return false
        }
        return true
    }

    /** The row about one tool, or null when the doctor did not report it */
    fun row(rows: JSONArray, name: String): JSONObject? {
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            if (r.optString("name") == name) return r
        }
        return null
    }
}
