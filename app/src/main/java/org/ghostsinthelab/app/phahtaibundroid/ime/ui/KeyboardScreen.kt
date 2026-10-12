package org.ghostsinthelab.app.phahtaibundroid.ime.ui

import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ghostsinthelab.app.phahtaibundroid.R
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibundroid.ime.KeyAction
import org.ghostsinthelab.app.phahtaibundroid.ime.KeyboardLayer
import org.ghostsinthelab.app.phahtaibundroid.ime.KeyboardState
import kotlin.math.abs

private val KEY_HEIGHT = 50.dp
private val BAR_HEIGHT = 44.dp

/** Tone number keys, with a hint showing each tone's mark. */
private val TONE_HINTS = listOf("1" to "a", "2" to "á", "3" to "à", "4" to "ah", "5" to "â",
    "6" to "ǎ", "7" to "ā", "8" to "a̍h", "9" to "a̋", "0" to "")

@Composable
fun KeyboardScreen(state: KeyboardState, onKey: (KeyAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        if (!state.directInput) CandidateArea(state, onKey)
        when (state.layer) {
            KeyboardLayer.LETTERS -> LetterKeys(state, onKey)
            KeyboardLayer.SYMBOLS -> SymbolKeys(state, onKey)
        }
    }
}

private fun modeLabel(mode: OutputMode, system: RomanSystem) = when {
    mode == OutputMode.HANLO -> "漢"
    system == RomanSystem.POJ -> "POJ"
    else -> "Tâi-lô"
}

/**
 * The input buffer and candidate strip. Tapping the buffer, or swiping up/down anywhere on
 * this area, toggles between Hàn-lô and romanization output.
 */
@Composable
private fun CandidateArea(state: KeyboardState, onKey: (KeyAction) -> Unit) {
    val swipeThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val currentOnKey by rememberUpdatedState(onKey)
    Column(
        Modifier.pointerInput(Unit) {
            var dragged = 0f
            detectVerticalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = { if (abs(dragged) > swipeThreshold) currentOnKey(KeyAction.ToggleOutputMode) },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    dragged += amount
                },
            )
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clickable { onKey(KeyAction.ToggleOutputMode) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    !state.ready -> stringResource(R.string.keyboard_loading)
                    state.preview.isEmpty() -> stringResource(R.string.keyboard_toggle_hint)
                    else -> state.preview
                },
                modifier = Modifier.weight(1f),
                color = if (state.preview.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                fontSize = if (state.preview.isEmpty()) 12.sp else 16.sp,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            Text(
                text = modeLabel(state.mode, state.system),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        val listState = rememberLazyListState()
        LaunchedEffect(state.candidates) { listState.scrollToItem(0) }
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(state.candidates, key = { _, c -> c.index }) { position, candidate ->
                Box(
                    Modifier
                        .fillMaxHeight()
                        .clickable { onKey(KeyAction.SelectCandidate(candidate.index)) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = candidate.text,
                        fontSize = 20.sp,
                        fontWeight = if (position == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (position == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun LetterKeys(state: KeyboardState, onKey: (KeyAction) -> Unit) {
    fun label(c: Char) = if (state.shifted) c.uppercase() else c.toString()
    KeyRow {
        for ((digit, hint) in TONE_HINTS) {
            // `0` has no tone, but keeps the (empty) hint line so all digits share size and baseline.
            Key(digit, hint = hint, function = true) { onKey(KeyAction.Digit(digit[0])) }
        }
    }
    KeyRow { "qwertyuiop".forEach { c -> Key(label(c)) { onKey(KeyAction.Letter(c)) } } }
    KeyRow {
        Spacer(Modifier.weight(0.5f))
        "asdfghjkl".forEach { c -> Key(label(c)) { onKey(KeyAction.Letter(c)) } }
        Spacer(Modifier.weight(0.5f))
    }
    KeyRow {
        Key(if (state.shifted) "⬆" else "⇧", weight = 1.5f, function = true, description = stringResource(R.string.key_shift)) {
            onKey(KeyAction.Shift)
        }
        "zxcvbnm".forEach { c -> Key(label(c)) { onKey(KeyAction.Letter(c)) } }
        BackspaceKey(onKey)
    }
    BottomRow(state, onKey, layerLabel = "?123", layerDescription = R.string.key_layer_symbols)
}

@Composable
private fun SymbolKeys(state: KeyboardState, onKey: (KeyAction) -> Unit) {
    KeyRow { "1234567890".forEach { c -> Key(c.toString()) { onKey(KeyAction.Symbol(c.toString())) } } }
    KeyRow { listOf("@", "#", "$", "%", "&", "*", "+", "=", "(", ")").forEach { s -> Key(s) { onKey(KeyAction.Symbol(s)) } } }
    KeyRow { listOf("~", "_", "/", "\\", "|", "<", ">", "[", "]", "\"").forEach { s -> Key(s) { onKey(KeyAction.Symbol(s)) } } }
    KeyRow {
        listOf("'", ":", ";", "!", "?", "…", "、", "「", "」").forEach { s -> Key(s) { onKey(KeyAction.Symbol(s)) } }
        BackspaceKey(onKey)
    }
    BottomRow(state, onKey, layerLabel = "ABC", layerDescription = R.string.key_layer_letters)
}

@Composable
private fun BottomRow(state: KeyboardState, onKey: (KeyAction) -> Unit, layerLabel: String, @StringRes layerDescription: Int) {
    // A system-provided IME navigation bar (it gives the IME window captionBar insets) already
    // draws its own IME switcher, so only offer ours when that bar is absent, or when the system
    // explicitly asks for a custom one.
    val systemDrawsSwitcher = WindowInsets.captionBar.getBottom(LocalDensity.current) > 0
    val showSwitchKey = state.customSwitcherRequested || (state.offerSwitchKey && !systemDrawsSwitcher)
    KeyRow {
        Key(layerLabel, weight = 1.5f, function = true, description = stringResource(layerDescription)) {
            onKey(KeyAction.ToggleLayer)
        }
        if (showSwitchKey) {
            Key("🌐", function = true, description = stringResource(R.string.key_switch_ime)) {
                onKey(KeyAction.SwitchInputMethod)
            }
        }
        Key(",") { onKey(KeyAction.Symbol(",")) }
        Key(
            if (state.directInput) "" else modeLabel(state.mode, state.system),
            weight = 4f,
            description = stringResource(R.string.key_space),
        ) { onKey(KeyAction.Space) }
        Key("-") { onKey(KeyAction.Separator) }
        Key(".") { onKey(KeyAction.Symbol(".")) }
        val (enterLabel, enterDescription) = enterKey(state.enterAction)
        Key(enterLabel, weight = 1.5f, function = true, description = stringResource(enterDescription)) {
            onKey(KeyAction.Enter)
        }
    }
}

@Composable
private fun RowScope.BackspaceKey(onKey: (KeyAction) -> Unit) {
    Key("⌫", weight = 1.5f, function = true, repeat = true, description = stringResource(R.string.key_delete)) {
        onKey(KeyAction.Backspace)
    }
}

/** Symbol and spoken description for the enter key, by the editor's IME action. */
private fun enterKey(action: Int): Pair<String, Int> = when (action) {
    EditorInfo.IME_ACTION_GO -> "→" to R.string.key_go
    EditorInfo.IME_ACTION_SEARCH -> "🔍" to R.string.key_search
    EditorInfo.IME_ACTION_SEND -> "➤" to R.string.key_send
    EditorInfo.IME_ACTION_NEXT -> "⇥" to R.string.key_next
    EditorInfo.IME_ACTION_DONE -> "✓" to R.string.key_done
    else -> "⏎" to R.string.key_enter
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, content = content)
}

/**
 * A key. Fires on release, or repeatedly while held when [repeat] is set (backspace).
 * [description] is what screen readers say for keys whose label is a symbol.
 */
@Composable
private fun RowScope.Key(
    label: String,
    weight: Float = 1f,
    hint: String? = null,
    function: Boolean = false,
    repeat: Boolean = false,
    description: String? = null,
    onPress: () -> Unit,
) {
    val view = LocalView.current
    val currentOnPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(weight)
            .height(KEY_HEIGHT)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    pressed -> colors.primaryContainer
                    function -> colors.secondaryContainer
                    else -> colors.surfaceContainerHighest
                }
            )
            .semantics {
                role = Role.Button
                if (description != null) contentDescription = description
                onClick { currentOnPress(); true }
            }
            .pointerInput(repeat) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (repeat) {
                            currentOnPress()
                            val repeater = launch {
                                delay(REPEAT_DELAY_MS)
                                while (isActive) {
                                    currentOnPress()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                            waitForUpOrCancellation()
                            repeater.cancel()
                        } else if (waitForUpOrCancellation() != null) {
                            currentOnPress()
                        }
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (hint != null) {
            // Stacked rather than in a corner: keys are too narrow for hints like `a̍h` beside the label.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hint.ifEmpty { " " }, fontSize = 11.sp, lineHeight = 13.sp, color = colors.onSurfaceVariant, maxLines = 1)
                Text(label, fontSize = 18.sp, lineHeight = 20.sp, color = colors.onSurface, maxLines = 1)
            }
        } else {
            Text(label, fontSize = if (label.length > 3) 14.sp else 20.sp, color = colors.onSurface, maxLines = 1)
        }
    }
}

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L
