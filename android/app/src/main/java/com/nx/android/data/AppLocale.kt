package com.nx.android.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's own language: the one the player picked, or the phone's own when they picked nothing.
 *
 * The words live in the app's own resources — `res/values/strings.xml` (English, the default bundle) and
 * `res/values-zh/strings.xml` (Chinese) — and the phone picks the bundle: a phone set to Chinese gets the
 * Chinese one, every other phone the English one. A player who picks a language in the settings gets that
 * one instead, from then on.
 *
 * The screens read the words with `stringResource` (the resources are handed to the composition, see
 * `ui/Locale.kt`), and the code that has no screen — the state holders, the service, the activity — reads
 * them with [str]. Both read the same resources.
 */
class AppLocale(private val base: Context, private val prefs: PrefsStore) {

    /** What the player picked: the phone's own setting, or one of the two languages */
    enum class Mode(val code: String) {
        SYSTEM(""), ZH("zh"), EN("en");

        companion object {
            fun of(code: String): Mode = entries.firstOrNull { it.code == code } ?: SYSTEM
        }
    }

    var mode: Mode by mutableStateOf(Mode.of(prefs.str(PrefsStore.LANG)))
        private set

    /** The language the player picked; it is remembered and it takes effect at once */
    fun pick(m: Mode) {
        mode = m
        prefs.put(PrefsStore.LANG, m.code)
    }

    /**
     * The locale the app shows in: null means the phone's own language.
     *
     * Both languages are given a region, and that is not decoration. A locale without one (`en`) does not
     * override the phone's own locale on Android 16: the configuration keeps the phone's (`zh_CN`), and the
     * words stay in the phone's language even though the player picked English. With a region (`en_US`,
     * `zh_CN`) the override takes. Measured on a Lenovo TB322FC, Android 16 — see the note in
     * `docs/ARCHITECTURE.md`.
     */
    val locale: Locale? get() = when (mode) {
        Mode.SYSTEM -> null
        Mode.ZH -> Locale.SIMPLIFIED_CHINESE
        Mode.EN -> Locale.US
    }

    /**
     * Bumped when the phone's configuration changed, so the words are read again: the phone's own language
     * is part of the configuration, and a screen that read a word before the change keeps the old one
     * otherwise.
     */
    private var revision by mutableIntStateOf(0)

    /** The phone's configuration changed (its language among it): the words are read again */
    fun configurationChanged() {
        revision++
    }

    private var cached: Resources? = null
    private var cachedMode: Mode? = null
    private var cachedRevision = -1

    /**
     * The resources the app's words are read from: the phone's own, or one forced to the language the
     * player picked.
     */
    fun resources(): Resources {
        val m = mode
        val rev = revision
        val c = cached
        if (c != null && cachedMode == m && cachedRevision == rev) return c
        val cfg = Configuration(base.resources.configuration)
        // the list is set, not added to: the language the player picked is the only one
        locale?.let { cfg.setLocales(LocaleList(it)) }
        val res = base.createConfigurationContext(cfg).resources
        cached = res
        cachedMode = m
        cachedRevision = rev
        return res
    }

    /** A word, for the code that has no screen to ask */
    fun str(@StringRes id: Int, vararg args: Any?): String = resources().getString(id, *args)
}
