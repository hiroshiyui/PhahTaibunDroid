package org.ghostsinthelab.app.phahtaibundroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibundroid.ime.PhahTaibunInputMethodService
import org.ghostsinthelab.app.phahtaibundroid.settings.AppLanguage
import org.ghostsinthelab.app.phahtaibundroid.settings.ImePreferences
import org.ghostsinthelab.app.phahtaibundroid.ui.theme.PhahTaibunTheme

/** Setup and settings screen; also the IME's settings activity (see `res/xml/method.xml`). */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Android 13+ applies the per-app language itself; older releases need it applied here.
        super.attachBaseContext(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) newBase else AppLanguage.wrap(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ImePreferences(this)
        setContent {
            PhahTaibunTheme {
                SettingsScreen(prefs)
            }
        }
    }
}

/**
 * Fuzzy-rule labels as (POJ, Tâi-lô) pairs: examples are spelled in the user's chosen system, as
 * POJ and Tâi-lô users each expect their own spelling.
 */
private val FUZZY_LABELS = mapOf(
    FuzzyRule.O_OO to (R.string.fuzzy_o_oo_poj to R.string.fuzzy_o_oo_tailo),
    FuzzyRule.NASAL to (R.string.fuzzy_nasal_poj to R.string.fuzzy_nasal_tailo),
    FuzzyRule.GLOTTAL_STOP to (R.string.fuzzy_glottal_stop to R.string.fuzzy_glottal_stop),
    FuzzyRule.ENTERING_TONE to (R.string.fuzzy_entering_tone to R.string.fuzzy_entering_tone),
    FuzzyRule.CODA_M_N to (R.string.fuzzy_coda_m_n to R.string.fuzzy_coda_m_n),
    FuzzyRule.CODA_N_NG to (R.string.fuzzy_coda_n_ng to R.string.fuzzy_coda_n_ng),
    FuzzyRule.ASPIRATION to (R.string.fuzzy_aspiration_poj to R.string.fuzzy_aspiration_tailo),
    FuzzyRule.VOICING to (R.string.fuzzy_voicing to R.string.fuzzy_voicing),
    FuzzyRule.UE_E to (R.string.fuzzy_ue_e_poj to R.string.fuzzy_ue_e_tailo),
    FuzzyRule.I_U to (R.string.fuzzy_i_u to R.string.fuzzy_i_u),
    FuzzyRule.INN_ENN to (R.string.fuzzy_inn_enn_poj to R.string.fuzzy_inn_enn_tailo),
    FuzzyRule.J_L to (R.string.fuzzy_j_l to R.string.fuzzy_j_l),
)

/** Settings tabs, in order. */
private enum class SettingsTab(@StringRes val label: Int) {
    GENERAL(R.string.settings_tab_general),
    FUZZY(R.string.settings_fuzzy),
    TRY(R.string.settings_try),
    ABOUT(R.string.settings_about),
}

@Composable
private fun SettingsScreen(prefs: ImePreferences) {
    var system by remember { mutableStateOf(prefs.romanSystem) }
    var mode by remember { mutableStateOf(prefs.outputMode) }
    var rules by remember { mutableStateOf(prefs.fuzzyRules) }
    // Saveable: a language change recreates the activity, which should stay on the same tab.
    var tab by rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }

    Scaffold(Modifier.fillMaxSize()) { padding ->
        Column(
            Modifier
                .padding(padding)
                // Edge-to-edge windows aren't resized for the IME; shrink the scroll viewport
                // ourselves so a focused field (the "Try it" box) can be scrolled above the keyboard.
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            Text(
                stringResource(R.string.ime_name),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 16.dp) {
                for (t in SettingsTab.entries) {
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(t.label)) })
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    SettingsTab.GENERAL -> {
                        Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium)

                        SetupSection()
                        HorizontalDivider()

                        LanguageSection()

                        Section(R.string.settings_roman_system, R.string.settings_roman_system_desc)
                        Choice(R.string.settings_poj, system == RomanSystem.POJ) {
                            system = RomanSystem.POJ; prefs.romanSystem = system
                        }
                        Choice(R.string.settings_tailo, system == RomanSystem.TAILO) {
                            system = RomanSystem.TAILO; prefs.romanSystem = system
                        }

                        Section(R.string.settings_output_mode, R.string.settings_output_mode_desc)
                        Choice(R.string.settings_hanlo, mode == OutputMode.HANLO) {
                            mode = OutputMode.HANLO; prefs.outputMode = mode
                        }
                        Choice(R.string.settings_roman, mode == OutputMode.ROMAN) {
                            mode = OutputMode.ROMAN; prefs.outputMode = mode
                        }
                    }

                    SettingsTab.FUZZY -> {
                        Text(
                            stringResource(R.string.settings_fuzzy_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        for (rule in FuzzyRule.entries) {
                            val checked = rule in rules
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(checked, role = Role.Checkbox) {
                                        rules = if (it) rules + rule else rules - rule
                                        prefs.fuzzyRules = rules
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                val (poj, tailo) = FUZZY_LABELS.getValue(rule)
                                Text(
                                    stringResource(if (system == RomanSystem.POJ) poj else tailo),
                                    Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }

                    SettingsTab.TRY -> {
                        var sample by rememberSaveable { mutableStateOf("") }
                        OutlinedTextField(sample, { sample = it }, Modifier.fillMaxWidth(), minLines = 4)
                    }

                    SettingsTab.ABOUT -> {
                        // HTML (kept as CDATA in the resources) so the sources and licenses are links.
                        val about = stringResource(R.string.settings_about_text)
                        val linkColor = MaterialTheme.colorScheme.primary
                        val text = remember(about, linkColor) {
                            AnnotatedString.fromHtml(
                                about,
                                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            )
                        }
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSection() {
    val context = LocalContext.current
    val imm = context.getSystemService(InputMethodManager::class.java)
    var enabled by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }
    // Re-check whenever we come back from system settings or the IME picker.
    LifecycleResumeEffect(Unit) {
        val id = imm.enabledInputMethodList
            .firstOrNull { it.serviceName == PhahTaibunInputMethodService::class.java.name }?.id
        enabled = id != null
        selected = id != null &&
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id
        onPauseOrDispose { }
    }
    Text(stringResource(R.string.settings_setup), style = MaterialTheme.typography.titleMedium)
    SetupStep(R.string.settings_enable, done = enabled) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
    SetupStep(R.string.settings_select, done = selected, available = enabled) {
        imm.showInputMethodPicker()
    }
}

/** UI languages offered in-app, by BCP 47 tag; an empty tag follows the system. */
private val LANGUAGES = listOf(
    "" to R.string.language_system,
    "en-US" to R.string.language_en,
    "zh-TW" to R.string.language_zh_tw,
    "nan-Hant-TW" to R.string.language_nan_hant_tw,
)

/**
 * Per-app UI language. System settings only list languages Android itself supports, which leaves
 * out Taigi (`nan`), and Android 12 and lower have no per-app language at all, so the choice is
 * offered here. Changing it recreates the activity.
 */
@Composable
private fun LanguageSection() {
    val context = LocalContext.current
    val current = AppLanguage.get(context)
    Section(R.string.settings_language, R.string.settings_language_desc)
    for ((tag, label) in LANGUAGES) {
        Choice(label, current == tag) {
            AppLanguage.set(context, tag)
            // Android 13+ recreates activities itself after a per-app language change.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) (context as? Activity)?.recreate()
        }
    }
}

@Composable
private fun SetupStep(@StringRes label: Int, done: Boolean, available: Boolean = true, onClick: () -> Unit) {
    if (done) {
        val doneText = stringResource(R.string.settings_done)
        OutlinedButton(onClick, Modifier.fillMaxWidth().semantics { stateDescription = doneText }) {
            Text("✓ " + stringResource(label))
        }
    } else {
        Button(onClick, Modifier.fillMaxWidth(), enabled = available) { Text(stringResource(label)) }
    }
}

@Composable
private fun Section(@StringRes title: Int, @StringRes description: Int) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Choice(@StringRes label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), Modifier.padding(start = 12.dp))
    }
}
