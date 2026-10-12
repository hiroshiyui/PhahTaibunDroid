package org.ghostsinthelab.app.phahtaibundroid.core.romanization

/** How a syllable is joined to the one before it. */
enum class Boundary(val text: String) {
    HYPHEN("-"),
    SPACE(" "),

    /** `--` marks the following syllable as neutral tone (輕聲). */
    NEUTRAL("--"),
}

/**
 * A single Taigi syllable in canonical form.
 *
 * [base] is the toneless spelling in lowercase ASCII Tâi-lô (e.g. `tshiunn`, `ua`, `ing`), which
 * is the pivot representation: POJ input is converted into it when parsed, and both POJ and Tâi-lô
 * output are rendered from it. [tone] is 1–9, or [TONE_UNSPECIFIED] when the user typed no tone.
 */
data class Syllable(
    val base: String,
    val tone: Int = TONE_UNSPECIFIED,
    val boundary: Boundary = Boundary.HYPHEN,
    val capitalized: Boolean = false,
) {
    /** Checked syllables (入聲) end in a stop and take tones 4/8. */
    val isChecked: Boolean get() = base.last() in CHECKED_CODAS

    /** Fills in the implicit tone of an unmarked syllable: 4 if checked, otherwise 1. */
    fun withImplicitTone(): Syllable =
        if (tone != TONE_UNSPECIFIED) this else copy(tone = if (isChecked) 4 else 1)

    /** Tâi-lô numeric notation, e.g. `Tai5` or `ah4`. Boundaries are not included. */
    fun toNumeric(): String {
        val b = if (capitalized) base.replaceFirstChar { it.uppercaseChar() } else base
        return if (tone == TONE_UNSPECIFIED) b else "$b$tone"
    }

    companion object {
        const val TONE_UNSPECIFIED = 0
        private const val CHECKED_CODAS = "ptkh"
    }
}
