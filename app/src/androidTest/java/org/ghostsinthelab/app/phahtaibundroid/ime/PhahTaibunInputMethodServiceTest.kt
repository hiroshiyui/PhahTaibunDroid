package org.ghostsinthelab.app.phahtaibundroid.ime

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.ghostsinthelab.app.phahtaibundroid.MainActivity
import org.ghostsinthelab.app.phahtaibundroid.R
import org.ghostsinthelab.app.phahtaibundroid.SettingsSnapshot
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibundroid.settings.ImePreferences
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end through the real input method: the IME is selected system-wide, the "Try it" field
 * in [MainActivity] is focused, and keys of the on-screen keyboard are tapped with UI Automator,
 * so input travels through the system exactly as a user's touches would.
 *
 * Everything goes through UI Automator rather than a Compose test rule: the keyboard runs in this
 * same process, and a Compose test rule would also take over the keyboard's compositions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PhahTaibunInputMethodServiceTest {
    // Taken before the activity starts, so it holds the settings as they were before the test.
    private val snapshot = SettingsSnapshot()

    @get:Rule
    val activity = ActivityScenarioRule(MainActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val prefs = ImePreferences(context)

    private fun string(@StringRes id: Int) = context.getString(id)

    @Before
    fun setUp() {
        // Read by the service when the field gets focus.
        prefs.outputMode = OutputMode.HANLO
        prefs.romanSystem = RomanSystem.TAILO
        // Rankings below assume the default fuzzy rules, whatever the device was left with.
        prefs.fuzzyRules = FuzzyRule.DEFAULTS
        showKeyboard()
        // The hint replaces the loading message once the lexicon is ready.
        assertTrue(
            "The lexicon did not load",
            device.wait(Until.hasObject(By.text(string(R.string.keyboard_toggle_hint))), LOAD_TIMEOUT),
        )
    }

    @After
    fun tearDown() {
        // Hide the keyboard, so it doesn't linger over whatever test runs next.
        device.pressBack()
        snapshot.restore()
    }

    @Test
    fun setupStepsShowDone() {
        scrollTo(By.text("✓ " + string(R.string.settings_enable)), Direction.UP)
        scrollTo(By.text("✓ " + string(R.string.settings_select)), Direction.UP)
    }

    @Test
    fun choosingCandidateCommitsHanlo() {
        type("taigi")
        button("台語").click()
        awaitFieldText("台語")
    }

    @Test
    fun spaceCommitsTopCandidate() {
        type("taigi")
        button("台語")
        keyDescribed(R.string.key_space).click()
        awaitFieldText("台語")
    }

    @Test
    fun tappingInputBufferSwitchesToRomanization() {
        type("taigi")
        button("台語")
        // The input buffer bar: the composition, romanized, next to the output mode label.
        poll("Input buffer") {
            device.findObjects(By.clickable(true).hasChild(By.text("漢"))).singleOrNull { it.childCount == 2 }
        }.click()
        // The space bar shows the new mode too.
        button("Tâi-lô")
        button("Tâi-gí").click()
        awaitFieldText("Tâi-gí")
        assertEquals(OutputMode.ROMAN, prefs.outputMode)
    }

    @Test
    fun backspaceEditsCompositionThenText() {
        type("taigi")
        awaitFieldText("tai-gi")
        repeat(5) { keyDescribed(R.string.key_delete).click() }
        // Composition emptied: nothing was committed.
        awaitFieldText("")
        type("a")
        keyDescribed(R.string.key_enter).click()
        awaitFieldText("a")
        // Nothing being composed: deletes from the editor.
        keyDescribed(R.string.key_delete).click()
        awaitFieldText("")
    }

    /**
     * Focuses the "Try it" field until the keyboard shows. Right after the IME has been selected
     * (or its process restarted) the first request may get lost while the system binds it.
     */
    private fun showKeyboard() {
        val space = By.desc(string(R.string.key_space))
        repeat(SHOW_ATTEMPTS) {
            field().click()
            if (device.wait(Until.hasObject(space), TIMEOUT)) return
        }
        throw AssertionError("Keyboard did not appear")
    }

    /** The "Try it" field, scrolled into view (the keyboard may have covered it). */
    private fun field(): UiObject2 = scrollTo(By.clazz(EDIT_TEXT), Direction.DOWN)

    private fun scrollTo(selector: BySelector, direction: Direction): UiObject2 {
        device.findObject(selector)?.let { return it }
        val scrollable = device.findObject(By.pkg(context.packageName).scrollable(true))
            ?: throw AssertionError("Settings screen not shown")
        return scrollable.scrollUntil(direction, Until.findObject(selector))
            ?: throw AssertionError("$selector not found")
    }

    /** Taps the letter keys spelling [text]. */
    private fun type(text: String) = text.forEach { button(it.toString()).click() }

    /**
     * A key or candidate labelled [label]: a clickable node whose only child is the label. Tone
     * keys also show a letter, as their hint, and the input buffer bar may show the same text as
     * a candidate; both have a second child.
     */
    private fun button(label: String): UiObject2 = poll("Button $label") {
        device.findObjects(By.clickable(true).hasChild(By.text(label))).singleOrNull { it.childCount == 1 }
    }

    private fun keyDescribed(@StringRes description: Int): UiObject2 =
        device.wait(Until.findObject(By.desc(string(description))), TIMEOUT)
            ?: throw AssertionError("Key ${string(description)} not found")

    /** Retries [find] until it returns something, as the keyboard window updates asynchronously. */
    private fun <T : Any> poll(what: String, find: () -> T?): T {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (true) {
            find()?.let { return it }
            if (System.currentTimeMillis() > deadline) throw AssertionError("$what not found")
            device.waitForIdle(POLL_INTERVAL)
        }
    }

    private fun awaitFieldText(expected: String) {
        var actual: String? = null
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            actual = field().text.orEmpty()
            if (actual == expected) return
            device.waitForIdle(POLL_INTERVAL)
        }
        assertEquals(expected, actual)
    }

    companion object {
        private const val TIMEOUT = 5_000L
        private const val LOAD_TIMEOUT = 30_000L
        private const val POLL_INTERVAL = 100L
        private const val SHOW_ATTEMPTS = 3
        private const val EDIT_TEXT = "android.widget.EditText"
        private const val IME_ID =
            "org.ghostsinthelab.app.phahtaibundroid/.ime.PhahTaibunInputMethodService"

        private var originalIme: String? = null

        private fun shell(command: String) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(command)
        }

        @JvmStatic
        @BeforeClass
        fun selectIme() {
            val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            originalIme = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            shell("ime enable $IME_ID")
            shell("ime set $IME_ID")
        }

        @JvmStatic
        @AfterClass
        fun restoreIme() {
            originalIme?.takeIf { it.isNotEmpty() && it != IME_ID }?.let { shell("ime set $it") }
        }
    }
}
