package com.nx.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.nx.android.data.AppLocale

/**
 * The app's own language, for the screens: the one the player picked, or the phone's own.
 *
 * The words come from the app's own resources. The phone picks the bundle when the player has not picked a
 * language: a phone set to Chinese gets `values-zh`, every other phone the default (`values`, English). A
 * player who picks a language gets that bundle instead — the resources are replaced for the whole
 * composition, so `stringResource` reads the right words everywhere.
 */
val LocalAppLocale = androidx.compose.runtime.staticCompositionLocalOf<AppLocale> {
    error("the app's language is not in the composition")
}

/**
 * Hands the app's language to the screens: the resources they read their words from, and the language
 * itself for the code that asks for a word outside a composition.
 *
 * The phone's own language needs no replacing — the resources follow it — but the words are read again when
 * the phone's configuration changes, so a screen does not keep the words of the old language.
 */
@Composable
fun ProvideAppLocale(locale: AppLocale, content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val mode = locale.mode
    val res = remember(cfg, mode) { locale.resources() }
    CompositionLocalProvider(
        LocalResources provides res,
        LocalAppLocale provides locale,
        content = content,
    )
}
