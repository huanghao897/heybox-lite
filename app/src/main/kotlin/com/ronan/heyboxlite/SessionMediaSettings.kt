package com.ronan.heyboxlite

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

private const val DATA_SAVER = "content_image_data_saver"

private fun SessionStore.mediaPreferences(): SharedPreferences =
    appContext().getSharedPreferences(SecureStrings.preferencesName(), Context.MODE_PRIVATE)

internal fun SessionStore.isDataSaverEnabled(): Boolean = mediaPreferences().getBoolean(DATA_SAVER, false)

internal fun SessionStore.setDataSaverEnabled(enabled: Boolean) {
    mediaPreferences().edit().putBoolean(DATA_SAVER, enabled).apply()
}

internal val LocalComposeDataSaver = staticCompositionLocalOf { false }

@Composable
internal fun ProvideComposeDataSaver(session: SessionStore, content: @Composable () -> Unit) {
    var enabled by remember(session) { mutableStateOf(session.isDataSaverEnabled()) }
    DisposableEffect(session) {
        val preferences = session.mediaPreferences()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DATA_SAVER) enabled = session.isDataSaverEnabled()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        enabled = session.isDataSaverEnabled()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(LocalComposeDataSaver provides enabled, content = content)
}
