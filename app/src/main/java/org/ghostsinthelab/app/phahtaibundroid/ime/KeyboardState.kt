package org.ghostsinthelab.app.phahtaibundroid.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.ghostsinthelab.app.phahtaibundroid.core.engine.Composer
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem

enum class KeyboardLayer { LETTERS, SYMBOLS }

/** Everything the keyboard UI renders. Written by the service, observed by Compose. */
class KeyboardState {
    var ready by mutableStateOf(false)
    var preview by mutableStateOf("")
    var candidates by mutableStateOf(emptyList<Composer.DisplayCandidate>())
    var mode by mutableStateOf(OutputMode.HANLO)
    var system by mutableStateOf(RomanSystem.TAILO)
    var layer by mutableStateOf(KeyboardLayer.LETTERS)
    var shifted by mutableStateOf(false)

    /** Composition disabled: passwords, URLs, numbers and similar fields. */
    var directInput by mutableStateOf(false)
    var enterLabel by mutableStateOf("⏎")

    /** Switching to another input method makes sense (more than one is enabled). */
    var offerSwitchKey by mutableStateOf(false)

    /**
     * The system asked us to draw our own switcher (API 36+), because the IME navigation bar it
     * would otherwise draw one in has been hidden.
     */
    var customSwitcherRequested by mutableStateOf(false)
}

/** User actions on the keyboard. */
sealed interface KeyAction {
    data class Letter(val char: Char) : KeyAction
    data class Digit(val char: Char) : KeyAction
    data class Symbol(val text: String) : KeyAction
    data class SelectCandidate(val index: Int) : KeyAction
    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    data object Separator : KeyAction
    data object ToggleOutputMode : KeyAction
    data object ToggleLayer : KeyAction
    data object SwitchInputMethod : KeyAction
}
