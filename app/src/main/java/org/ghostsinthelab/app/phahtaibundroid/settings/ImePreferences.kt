package org.ghostsinthelab.app.phahtaibundroid.settings

import android.content.Context
import androidx.core.content.edit
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem

/** User settings shared by the settings screen and the IME service. */
class ImePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var romanSystem: RomanSystem
        get() = enumOrNull<RomanSystem>(prefs.getString(KEY_ROMAN_SYSTEM, null)) ?: RomanSystem.TAILO
        set(value) = prefs.edit { putString(KEY_ROMAN_SYSTEM, value.name) }

    /** The output mode last chosen, either in settings or by toggling on the keyboard. */
    var outputMode: OutputMode
        get() = enumOrNull<OutputMode>(prefs.getString(KEY_OUTPUT_MODE, null)) ?: OutputMode.HANLO
        set(value) = prefs.edit { putString(KEY_OUTPUT_MODE, value.name) }

    var fuzzyRules: Set<FuzzyRule>
        get() = prefs.getStringSet(KEY_FUZZY_RULES, null)
            ?.mapNotNull { enumOrNull<FuzzyRule>(it) }?.toSet()
            ?: FuzzyRule.DEFAULTS
        set(value) = prefs.edit { putStringSet(KEY_FUZZY_RULES, value.map { it.name }.toSet()) }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private companion object {
        const val KEY_ROMAN_SYSTEM = "roman_system"
        const val KEY_OUTPUT_MODE = "output_mode"
        const val KEY_FUZZY_RULES = "fuzzy_rules"
    }
}
