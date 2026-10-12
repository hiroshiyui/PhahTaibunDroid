package org.ghostsinthelab.app.phahtaibundroid.ime.ui

import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.ghostsinthelab.app.phahtaibundroid.R
import org.ghostsinthelab.app.phahtaibundroid.core.engine.Composer
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.ime.KeyAction
import org.ghostsinthelab.app.phahtaibundroid.ime.KeyboardLayer
import org.ghostsinthelab.app.phahtaibundroid.ime.KeyboardState
import org.ghostsinthelab.app.phahtaibundroid.ui.theme.PhahTaibunTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [KeyboardScreen] in isolation: which [KeyAction] each key emits and how the UI follows
 * [KeyboardState]. Strings are looked up from resources so the tests pass under any device locale.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = KeyboardState()
    private val actions = mutableListOf<KeyAction>()

    @Before
    fun setUp() {
        state.ready = true
        compose.setContent {
            PhahTaibunTheme {
                KeyboardScreen(state) { actions += it }
            }
        }
    }

    private fun string(@StringRes id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** A key labelled [label]. Keys aren't merged nodes, so match the clickable parent of the label. */
    private fun key(label: String): SemanticsNodeInteraction =
        compose.onNode(hasClickAction() and hasAnyChild(hasText(label)))

    private fun keyDescribed(@StringRes description: Int) =
        compose.onNodeWithContentDescription(string(description))

    @Test
    fun letterKeysEmitLetters() {
        "taigi".filter { it != 'a' }.forEach { key(it.toString()).performClick() }
        assertEquals("tigi".map { KeyAction.Letter(it) }, actions)
    }

    @Test
    fun touchGestureOnKeyEmitsOnRelease() {
        key("q").performTouchInput { click() }
        assertEquals(listOf(KeyAction.Letter('q')), actions)
    }

    @Test
    fun toneRowEmitsDigits() {
        key("2").performClick()
        key("8").performClick()
        key("0").performClick()
        assertEquals(listOf('2', '8', '0').map { KeyAction.Digit(it) }, actions)
    }

    @Test
    fun functionKeysEmitTheirActions() {
        keyDescribed(R.string.key_delete).performClick()
        keyDescribed(R.string.key_space).performClick()
        keyDescribed(R.string.key_enter).performClick()
        keyDescribed(R.string.key_shift).performClick()
        key("-").performClick()
        key(",").performClick()
        assertEquals(
            listOf(
                KeyAction.Backspace, KeyAction.Space, KeyAction.Enter, KeyAction.Shift,
                KeyAction.Separator, KeyAction.Symbol(","),
            ),
            actions,
        )
    }

    @Test
    fun shiftShowsUppercaseLabels() {
        state.shifted = true
        key("Q").performClick()
        // The service applies the shift state; the key itself always reports the base letter.
        assertEquals(listOf(KeyAction.Letter('q')), actions)
    }

    @Test
    fun layerKeySwitchesBetweenLettersAndSymbols() {
        keyDescribed(R.string.key_layer_symbols).performClick()
        assertEquals(listOf(KeyAction.ToggleLayer), actions)

        // The service owns the layer; mimic it.
        state.layer = KeyboardLayer.SYMBOLS
        key("q").assertDoesNotExist()
        key("ABC").assertExists()
        key("@").performClick()
        keyDescribed(R.string.key_layer_letters).performClick()
        assertEquals(listOf(KeyAction.ToggleLayer, KeyAction.Symbol("@"), KeyAction.ToggleLayer), actions)

        state.layer = KeyboardLayer.LETTERS
        key("?123").assertExists()
        key("q").assertExists()
    }

    @Test
    fun candidatesRenderAndEmitTheirIndex() {
        state.preview = "tâi-gí"
        state.candidates = listOf(
            Composer.DisplayCandidate("台語", 0),
            Composer.DisplayCandidate("刣魚", 2),
        )
        compose.onNodeWithText("台語").assertExists()
        compose.onNodeWithText("刣魚").performClick()
        assertEquals(listOf(KeyAction.SelectCandidate(2)), actions)
    }

    @Test
    fun tappingInputBufferTogglesOutputMode() {
        compose.onNodeWithText(string(R.string.keyboard_toggle_hint)).performClick()
        state.preview = "tâi-gí"
        compose.onNodeWithText("tâi-gí").performClick()
        assertEquals(listOf(KeyAction.ToggleOutputMode, KeyAction.ToggleOutputMode), actions)
    }

    @Test
    fun modeLabelFollowsOutputMode() {
        // Shown both in the input buffer bar and on the space bar.
        assertEquals(2, compose.onAllNodesWithTextCount("漢"))
        state.mode = OutputMode.ROMAN
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTextCount("漢"))
        assertEquals(2, compose.onAllNodesWithTextCount("Tâi-lô"))
    }

    private fun ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun loadingMessageShownUntilReady() {
        state.ready = false
        compose.onNodeWithText(string(R.string.keyboard_loading)).assertExists()
        state.ready = true
        compose.onNodeWithText(string(R.string.keyboard_toggle_hint)).assertExists()
    }

    @Test
    fun directInputHidesCandidateArea() {
        state.candidates = listOf(Composer.DisplayCandidate("台語", 0))
        compose.onNodeWithText("台語").assertExists()
        state.directInput = true
        compose.onNodeWithText("台語").assertDoesNotExist()
        compose.onNodeWithText(string(R.string.keyboard_toggle_hint)).assertDoesNotExist()
        key("q").assertExists()
    }

    @Test
    fun enterKeyDescribesEditorAction() {
        val expected = mapOf(
            EditorInfo.IME_ACTION_UNSPECIFIED to R.string.key_enter,
            EditorInfo.IME_ACTION_GO to R.string.key_go,
            EditorInfo.IME_ACTION_SEARCH to R.string.key_search,
            EditorInfo.IME_ACTION_SEND to R.string.key_send,
            EditorInfo.IME_ACTION_NEXT to R.string.key_next,
            EditorInfo.IME_ACTION_DONE to R.string.key_done,
        )
        for ((action, description) in expected) {
            state.enterAction = action
            keyDescribed(description).performClick()
        }
        assertEquals(List(expected.size) { KeyAction.Enter }, actions)
    }

    @Test
    fun switchKeyShownOnlyWhenOffered() {
        val switchKey = SemanticsMatcher.expectValue(
            SemanticsProperties.ContentDescription,
            listOf(string(R.string.key_switch_ime)),
        )
        compose.onNode(switchKey).assertDoesNotExist()
        state.customSwitcherRequested = true
        compose.onNode(switchKey).performClick()
        assertEquals(listOf(KeyAction.SwitchInputMethod), actions)
    }
}
