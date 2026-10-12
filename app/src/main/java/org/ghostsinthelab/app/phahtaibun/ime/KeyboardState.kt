package org.ghostsinthelab.app.phahtaibun.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.ghostsinthelab.app.phahtaibun.core.engine.Composer
import org.ghostsinthelab.app.phahtaibun.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibun.core.romanization.RomanSystem

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
    var showSwitchKey by mutableStateOf(false)
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
