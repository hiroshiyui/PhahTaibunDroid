package org.ghostsinthelab.app.phahtaibun.core.engine

import org.ghostsinthelab.app.phahtaibun.core.romanization.RomanSystem

/**
 * Composition state for one IME session: the raw keystroke buffer, candidates selected so far
 * (when the user converts the input piece by piece), and the output mode.
 *
 * Methods that finish a composition return the text to commit; the caller owns the editor.
 * Selected pieces are stored as candidates rather than strings, so toggling [mode] re-renders
 * the whole composition, including pieces already chosen.
 */
class Composer(
    var engine: CandidateEngine,
    var system: RomanSystem = RomanSystem.TAILO,
    var mode: OutputMode = OutputMode.HANLO,
    private val history: UserHistory = UserHistory.None,
) {
    private class Selection(val candidate: Candidate, val raw: String)

    /** A candidate as shown to the user; [index] refers to [candidates]. */
    data class DisplayCandidate(val text: String, val index: Int)

    private val raw = StringBuilder()
    private val selections = ArrayList<Selection>()

    var candidates: List<Candidate> = emptyList()
        private set

    val rawInput: String get() = raw.toString()
    val isComposing: Boolean get() = raw.isNotEmpty() || selections.isNotEmpty()

    fun type(c: Char) {
        raw.append(c)
        refresh()
    }

    /** Returns `false` if there was nothing to delete (the caller should delete in the editor). */
    fun backspace(): Boolean {
        when {
            raw.isNotEmpty() -> raw.setLength(raw.length - 1)
            selections.isNotEmpty() -> raw.insert(0, selections.removeAt(selections.lastIndex).raw)
            else -> return false
        }
        refresh()
        return true
    }

    /**
     * Selects [candidates]`[index]`. Returns the composition's text if this completed it, or
     * `null` if raw input remains to be converted.
     */
    fun select(index: Int): String? {
        val candidate = candidates.getOrNull(index) ?: return null
        history.record(candidate.hanlo, candidate.numeric)
        selections += Selection(candidate, raw.substring(0, candidate.consumed))
        raw.delete(0, candidate.consumed)
        while (raw.isNotEmpty() && !raw[0].isLetterOrDigit()) raw.deleteCharAt(0)
        if (raw.isEmpty()) return finish()
        refresh()
        return null
    }

    /** Selects the top candidate (space bar). */
    fun commitFirst(): String? = if (candidates.isEmpty()) finish() else select(0)

    /** Commits the composition as currently previewed, without converting the rest (enter). */
    fun commitPreview(): String = finish()

    /** Candidates to show, de-duplicated by their visible text (romanization often collides). */
    fun displayCandidates(): List<DisplayCandidate> {
        val seen = HashSet<String>()
        return candidates.mapIndexedNotNull { i, c ->
            val text = casedText(c, raw)
            if (seen.add(text)) DisplayCandidate(text, i) else null
        }
    }

    /** Selected pieces in the current mode, followed by the unconverted input as romanization. */
    fun preview(): String {
        val parts = selections.map { casedText(it.candidate, it.raw) }.toMutableList()
        if (raw.isNotEmpty()) {
            parts += candidates.firstOrNull { it.hanlo == null && it.consumed == raw.length }
                ?.roman(system) ?: raw.toString()
        }
        return parts.joinToString(if (mode == OutputMode.ROMAN) " " else "")
    }

    fun toggleMode() {
        mode = if (mode == OutputMode.HANLO) OutputMode.ROMAN else OutputMode.HANLO
    }

    /** Re-runs the engine, e.g. after the lexicon or fuzzy rules changed. */
    fun refresh() {
        candidates = if (raw.isEmpty()) emptyList() else engine.candidates(raw.toString())
    }

    fun reset() {
        raw.setLength(0)
        selections.clear()
        candidates = emptyList()
    }

    private fun finish(): String = preview().also { reset() }

    /** In romanization mode, an initial capital typed by the user is kept. */
    private fun casedText(candidate: Candidate, typed: CharSequence): String {
        val text = candidate.text(mode, system)
        val showsRoman = mode == OutputMode.ROMAN || candidate.hanlo == null
        val capitalize = showsRoman && typed.firstOrNull()?.isUpperCase() == true
        return if (capitalize) text.replaceFirstChar { it.uppercaseChar() } else text
    }
}
