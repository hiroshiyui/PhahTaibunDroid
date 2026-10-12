package org.ghostsinthelab.app.phahtaibundroid.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.LocaleList
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ghostsinthelab.app.phahtaibundroid.core.engine.CandidateEngine
import org.ghostsinthelab.app.phahtaibundroid.core.engine.Composer
import org.ghostsinthelab.app.phahtaibundroid.core.engine.FrequencyHistory
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.engine.SyllablePatterns
import org.ghostsinthelab.app.phahtaibundroid.core.engine.UserHistory
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibundroid.ime.ui.KeyboardScreen
import org.ghostsinthelab.app.phahtaibundroid.settings.AppLanguage
import org.ghostsinthelab.app.phahtaibundroid.settings.ImePreferences
import org.ghostsinthelab.app.phahtaibundroid.ui.theme.PhahTaibunTheme
import java.io.File

/**
 * The Taigi input method. A thin adapter: keystrokes go to [Composer] (in `:core`), and its
 * preview / committed text is mirrored into the editor through the InputConnection.
 *
 * Implements [LifecycleOwner] and [SavedStateRegistryOwner] because Compose needs them on the
 * IME window's view tree.
 */
class PhahTaibunInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val state = KeyboardState()
    private lateinit var prefs: ImePreferences
    private val history = FrequencyHistory()
    private val historyFile by lazy { File(filesDir, "history.tsv") }

    /** Disabled in incognito fields so nothing typed there influences later suggestions. */
    private var learning = true
    private val gatedHistory = object : UserHistory {
        override fun boost(hanlo: String?, numeric: String) = history.boost(hanlo, numeric)
        override fun record(hanlo: String?, numeric: String) {
            if (learning) history.record(hanlo, numeric)
        }
        override fun pairBoost(previous: LexiconEntry, next: LexiconEntry) = history.pairBoost(previous, next)
        override fun recordPair(previous: LexiconEntry, next: LexiconEntry) {
            if (learning) history.recordPair(previous, next)
        }
    }

    /** Set when we commit text, so the editor's next selection update isn't taken for a cursor move. */
    private var ownEditPending = false

    private val historySaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** `null` until the lexicon has loaded. */
    private var composer: Composer? = null
    private var composingShown = false

    /**
     * Emulators report the host's keyboard as a hardware keyboard, which hides soft keyboards
     * unless "Show on-screen keyboard" is turned on in system settings. Always show ours there.
     */
    override fun onEvaluateInputViewShown(): Boolean = super.onEvaluateInputViewShown() || isEmulator

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        prefs = ImePreferences(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { historyFile.reader().use(history::load) }
            }
            loadEngine()
        }
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        viewLocales = AppLanguage.locales(this)
        return ComposeView(AppLanguage.wrap(this)).apply {
            setContent {
                PhahTaibunTheme {
                    KeyboardScreen(state, ::onKey)
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // The per-app language may have been changed (in our settings screen) since the view was built.
        if (AppLanguage.locales(this) != viewLocales) setInputView(onCreateInputView())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        state.directInput = isDirectInput(info)
        learning = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        state.enterAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        state.offerSwitchKey = offerSwitchingToNextInputMethod()
        state.layer = if (isNumericField(info)) KeyboardLayer.SYMBOLS else KeyboardLayer.LETTERS
        state.shifted = false
        state.mode = prefs.outputMode
        state.system = prefs.romanSystem
        // Settings may have changed while we were hidden.
        composer?.takeIf { it.engine.lexicon.rules != prefs.fuzzyRules }?.let {
            // Fuzzy rules changed: show the loading state until the index is rebuilt for them.
            state.ready = false
            lifecycleScope.launch { loadEngine() }
        }
        composer?.let {
            it.mode = state.mode
            it.system = state.system
            it.reset()
        }
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.finishComposingText()
        composer?.reset()
        composingShown = false
        render()
        saveHistory()
        // Also reached from super.onDestroy(); only pause a lifecycle that is actually resumed.
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor left the composing region (user tapped elsewhere): keep the text as is.
        val composer = composer ?: return
        if (composer.isComposing && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            currentInputConnection?.finishComposingText()
            composer.reset()
            composingShown = false
            render()
        } else if (!composer.isComposing && candidatesStart < 0) {
            // After our own commit the cursor moves with the text; any other move (a tap elsewhere,
            // a selection) means the next word doesn't follow the last one.
            if (ownEditPending && newSelStart == newSelEnd) ownEditPending = false else composer.forgetContext()
        }
    }

    override fun onCustomImeSwitcherButtonRequestedVisible(visible: Boolean) {
        state.customSwitcherRequested = visible
    }

    override fun onDestroy() {
        // super.onDestroy() finishes the input view, which needs a not-yet-destroyed lifecycle.
        super.onDestroy()
        saveHistory()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private suspend fun loadEngine() {
        val lexicon = LexiconRepository.get(this, prefs.fuzzyRules)
        val engine = CandidateEngine(lexicon, gatedHistory)
        composer?.let { it.engine = engine; it.refresh() }
            ?: run { composer = Composer(engine, state.system, state.mode, gatedHistory) }
        state.ready = true
        render()
    }

    private fun onKey(action: KeyAction) {
        val composer = composer?.takeUnless { state.directInput }
        when (action) {
            is KeyAction.Letter -> {
                val c = if (state.shifted) action.char.uppercaseChar() else action.char
                state.shifted = false
                if (composer != null) composer.type(c) else commit(c.toString())
            }
            is KeyAction.Digit ->
                // Digits right after a syllable are tone numbers; otherwise they're just digits.
                if (composer != null && composer.rawInput.lastOrNull()?.let { it.isLetter() || it == SyllablePatterns.WILDCARD } == true) {
                    composer.type(action.char)
                } else {
                    finishComposition()
                    commit(action.char.toString())
                    composer?.forgetContext()
                }
            is KeyAction.Symbol ->
                // While composing, `?` is a wildcard for unsure letters (`s?ah`), not punctuation.
                if (action.text == SyllablePatterns.WILDCARD.toString() && composer != null && composer.rawInput.isNotEmpty()) {
                    composer.type(SyllablePatterns.WILDCARD)
                } else {
                    finishComposition()
                    commit(if (state.mode == OutputMode.HANLO) FULL_WIDTH[action.text] ?: action.text else action.text)
                    composer?.forgetContext()
                }
            is KeyAction.SelectCandidate -> composer?.select(action.index)?.let(::commit)
            KeyAction.Separator ->
                if (composer != null && composer.rawInput.isNotEmpty()) {
                    composer.type('-')
                } else {
                    commit("-")
                    composer?.forgetContext()
                }
            KeyAction.Space ->
                if (composer != null && composer.isComposing) composer.commitFirst()?.let(::commit) else commit(" ")
            KeyAction.Enter ->
                if (composer != null && composer.isComposing) {
                    commit(composer.commitPreview())
                } else {
                    sendEnter()
                    composer?.forgetContext()
                }
            KeyAction.Backspace ->
                if (composer?.backspace() != true) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            KeyAction.Shift -> state.shifted = !state.shifted
            KeyAction.ToggleLayer -> state.layer =
                if (state.layer == KeyboardLayer.LETTERS) KeyboardLayer.SYMBOLS else KeyboardLayer.LETTERS
            KeyAction.ToggleOutputMode -> {
                state.mode = if (state.mode == OutputMode.HANLO) OutputMode.ROMAN else OutputMode.HANLO
                prefs.outputMode = state.mode
                this.composer?.mode = state.mode
            }
            KeyAction.SwitchInputMethod -> switchToNextInput()
        }
        render()
    }

    /** Converts whatever is being composed using the top candidates and commits it. */
    private fun finishComposition() {
        val composer = composer ?: return
        // Each step consumes at least one character of raw input, so this terminates.
        while (composer.isComposing) composer.commitFirst()?.let(::commit)
    }

    private fun commit(text: String) {
        ownEditPending = true
        currentInputConnection?.commitText(text, 1)
    }

    /** Mirrors the composer into the editor's composing region and the keyboard UI. */
    private fun render() {
        val composer = composer
        val preview = composer?.preview().orEmpty()
        val composing = composer?.isComposing == true
        // Also clear the region when backspace has just emptied the composition.
        if (composing || composingShown) currentInputConnection?.setComposingText(preview, 1)
        composingShown = composing
        state.preview = preview
        state.candidates = composer?.displayCandidates().orEmpty()
    }

    private fun sendEnter() {
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun saveHistory() {
        if (!history.dirty) return
        // Not lifecycleScope: a save started during onDestroy must not be cancelled with it.
        historySaveScope.launch {
            synchronized(historyFile) {
                runCatching {
                    val tmp = File(historyFile.path + ".tmp")
                    tmp.writer().use(history::save)
                    tmp.renameTo(historyFile)
                }
            }
        }
    }

    private fun isNumericField(info: EditorInfo) = when (info.inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        else -> false
    }

    private fun isDirectInput(info: EditorInfo): Boolean {
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
        return when (info.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> true
            else -> false
        }
    }

    /** Per-app language of the input view currently shown, or `null` if it follows the system. */
    private var viewLocales: LocaleList? = null

    // The InputMethodService versions of these exist from API 28; older releases only have the
    // (since deprecated) InputMethodManager calls taking the IME window token.
    private fun offerSwitchingToNextInputMethod(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return shouldOfferSwitchingToNextInputMethod()
        val token = windowToken ?: return false
        @Suppress("DEPRECATION")
        return inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
    }

    private fun switchToNextInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            @Suppress("DEPRECATION")
            inputMethodManager.switchToNextInputMethod(windowToken ?: return, false)
        }
    }

    private val inputMethodManager get() = getSystemService(InputMethodManager::class.java)
    private val windowToken get() = window?.window?.attributes?.token

    private companion object {
        val isEmulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            Build.BOARD.startsWith("goldfish_")

        /** Punctuation typed while writing Hàn-lô uses full-width forms. */
        val FULL_WIDTH = mapOf(
            "," to "，", "." to "。", "?" to "？", "!" to "！", ":" to "：", ";" to "；",
            "(" to "（", ")" to "）",
        )
    }
}
