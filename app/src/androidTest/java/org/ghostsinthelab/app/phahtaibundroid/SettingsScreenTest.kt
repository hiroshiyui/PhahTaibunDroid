package org.ghostsinthelab.app.phahtaibundroid

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibundroid.settings.ImePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The settings screen in [MainActivity] writes what the user picks to [ImePreferences]. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    // Taken before the activity starts, so it holds the settings as they were before the test.
    private val snapshot = SettingsSnapshot()

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val prefs get() = ImePreferences(compose.activity)

    private fun string(@StringRes id: Int) = compose.activity.getString(id)

    /** A radio choice; matched with its role, as a section title may share the label. */
    private fun choice(@StringRes label: Int) = compose.onNode(hasText(string(label)) and isSelectable())

    @After
    fun tearDown() {
        snapshot.restore()
    }

    @Test
    fun togglingFuzzyRulePersists() {
        val rule = FuzzyRule.ASPIRATION
        val wasOn = rule in prefs.fuzzyRules
        val label = if (prefs.romanSystem == RomanSystem.POJ) R.string.fuzzy_aspiration_poj else R.string.fuzzy_aspiration_tailo
        val row = compose.onNode(hasText(string(label)) and isToggleable()).performScrollTo()
        if (wasOn) row.assertIsOn() else row.assertIsOff()

        row.performClick()
        if (wasOn) row.assertIsOff() else row.assertIsOn()
        assertEquals(!wasOn, rule in prefs.fuzzyRules)

        row.performClick()
        assertEquals(wasOn, rule in prefs.fuzzyRules)
    }

    @Test
    fun fuzzyLabelsFollowTheChosenSystem() {
        choice(R.string.settings_poj).performScrollTo().performClick()
        compose.onNode(hasText(string(R.string.fuzzy_aspiration_poj)) and isToggleable()).performScrollTo()
        compose.onAllNodes(hasText(string(R.string.fuzzy_aspiration_tailo))).assertCountEquals(0)
        choice(R.string.settings_tailo).performScrollTo().performClick()
        compose.onNode(hasText(string(R.string.fuzzy_ue_e_tailo)) and isToggleable()).performScrollTo()
        compose.onAllNodes(hasText(string(R.string.fuzzy_ue_e_poj))).assertCountEquals(0)
    }

    @Test
    fun choosingRomanizationAndOutputPersists() {
        choice(R.string.settings_poj).performScrollTo().performClick().assertIsSelected()
        assertEquals(RomanSystem.POJ, prefs.romanSystem)
        choice(R.string.settings_tailo).performScrollTo().performClick().assertIsSelected()
        assertEquals(RomanSystem.TAILO, prefs.romanSystem)

        choice(R.string.settings_roman).performScrollTo().performClick().assertIsSelected()
        assertEquals(OutputMode.ROMAN, prefs.outputMode)
        choice(R.string.settings_hanlo).performScrollTo().performClick().assertIsSelected()
        assertEquals(OutputMode.HANLO, prefs.outputMode)
    }
}
