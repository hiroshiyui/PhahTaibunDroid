package org.ghostsinthelab.app.phahtaibundroid

import android.content.Context
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import org.ghostsinthelab.app.phahtaibundroid.settings.ImePreferences

/**
 * A copy of the app's settings (the SharedPreferences behind [ImePreferences]), so tests that
 * change them can put back exactly what was there, including keys that were unset.
 */
class SettingsSnapshot {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences(ImePreferences.NAME, Context.MODE_PRIVATE)
    private val saved: Map<String, *> = HashMap(prefs.all)

    fun restore() {
        prefs.edit(commit = true) {
            clear()
            for ((key, value) in saved) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }
    }
}
