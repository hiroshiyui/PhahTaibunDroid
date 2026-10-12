package org.ghostsinthelab.app.phahtaibundroid.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit

/**
 * The per-app UI language. On Android 13+ it is the system's per-app locale ([LocaleManager]),
 * shared with App info → Language. Older releases have no such setting, so the choice is stored
 * in our own preferences and applied by wrapping contexts with [wrap].
 */
object AppLanguage {
    /** The chosen language as a BCP 47 tag list, or `""` to follow the system. */
    fun get(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            prefs(context).getString(KEY_LANGUAGE, "").orEmpty()
        }

    /**
     * Stores [tags] (`""` follows the system). On Android 13+ the system recreates running
     * activities; on older releases the caller must recreate them itself.
     */
    fun set(context: Context, tags: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        } else {
            prefs(context).edit { putString(KEY_LANGUAGE, tags) }
        }
    }

    /** The chosen locales, or `null` to follow the system. */
    fun locales(context: Context): LocaleList? =
        get(context).takeUnless { it.isEmpty() }?.let(LocaleList::forLanguageTags)

    /**
     * [context] with the chosen language applied. Android 13+ applies it to the process itself,
     * but a running service only picks up a change after a restart, so this is used there too.
     */
    fun wrap(context: Context): Context {
        val locales = locales(context) ?: return context
        val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(ImePreferences.NAME, Context.MODE_PRIVATE)

    private const val KEY_LANGUAGE = "app_language"
}
