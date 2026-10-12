package org.ghostsinthelab.app.phahtaibun.core.engine

import org.ghostsinthelab.app.phahtaibun.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibun.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibun.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibun.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibun.core.romanization.Syllable
import kotlin.math.ln

/** What the IME writes: Hàn-lô (漢字/漢羅) or pure romanization. */
enum class OutputMode { HANLO, ROMAN }

/**
 * A conversion candidate covering the first [consumed] characters of the raw input.
 *
 * Both written forms are kept so the display can flip between Hàn-lô and romanization without
 * recomputing candidates.
 */
class Candidate(
    /** Hàn-lô form, or `null` for a pure romanization candidate (no dictionary match). */
    val hanlo: String?,
    val hanloPoj: String?,
    val tailo: String,
    val poj: String,
    /** Numeric Tâi-lô, used as the identity for user history. */
    val numeric: String,
    val consumed: Int,
    val score: Int,
) {
    fun roman(system: RomanSystem) = if (system == RomanSystem.POJ) poj else tailo

    fun text(mode: OutputMode, system: RomanSystem): String = when {
        mode == OutputMode.ROMAN || hanlo == null -> roman(system)
        system == RomanSystem.POJ -> hanloPoj ?: hanlo
        else -> hanlo
    }

    override fun toString() = "${hanlo ?: tailo}[$numeric,$consumed,$score]"
}

/** Learns from the user's selections to re-rank candidates. */
interface UserHistory {
    fun boost(hanlo: String?, numeric: String): Int
    fun record(hanlo: String?, numeric: String)

    object None : UserHistory {
        override fun boost(hanlo: String?, numeric: String) = 0
        override fun record(hanlo: String?, numeric: String) = Unit
    }
}

/**
 * Turns raw keystrokes into ranked candidates.
 *
 * Every alternative segmentation is tried; for each, the longest syllable prefixes are looked
 * up first so whole-input matches outrank partial ones (which the user selects piece by piece).
 * A pure romanization candidate of the whole input is always included so out-of-dictionary
 * words can still be typed.
 */
class CandidateEngine(
    val lexicon: Lexicon,
    private val history: UserHistory = UserHistory.None,
) {
    private val segmenter = Segmenter(lexicon)

    fun segment(raw: String): List<List<InputSyllable>> = segmenter.segment(raw)

    fun candidates(raw: String, limit: Int = 80): List<Candidate> {
        val segmentations = segmenter.segment(raw)
        if (segmentations.isEmpty()) return emptyList()
        val best = HashMap<String, Candidate>()
        fun offer(c: Candidate) {
            val key = "${c.hanlo}\u0000${c.numeric}\u0000${c.consumed}"
            val existing = best[key]
            if (existing == null || existing.score < c.score) best[key] = c
        }

        val fewestSyllables = segmentations.minOf { it.size }
        for (seg in segmentations) {
            val segPenalty = (seg.size - fewestSyllables) * EXTRA_SYLLABLE_PENALTY
            for (k in seg.size downTo 1) {
                val prefix = seg.subList(0, k)
                if (prefix.any { it.kind == InputSyllable.Kind.UNKNOWN }) continue
                val last = prefix.last()
                val entries = if (last.kind == InputSyllable.Kind.PARTIAL) {
                    lexicon.lookupPrefix(prefix.dropLast(1).map { it.loose }, last.loose, PREFIX_LIMIT)
                } else {
                    lexicon.lookup(prefix.map { it.loose })
                }
                for (entry in entries) {
                    val coverage = if (k == seg.size) FULL_MATCH - segPenalty else PARTIAL_MATCH_PER_SYLLABLE * k
                    offer(fromEntry(entry, prefix, coverage) ?: continue)
                }
            }
        }
        offer(romanCandidate(raw, segmentations.first()))
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    private fun fromEntry(entry: LexiconEntry, input: List<InputSyllable>, coverage: Int): Candidate? {
        val syllables = entry.syllables()
        if (syllables.size != input.size) return null
        var score = coverage + (ln(1.0 + entry.weight) * WEIGHT_FACTOR).toInt()
        for ((typed, actual) in input.zip(syllables)) {
            when {
                typed.kind == InputSyllable.Kind.PARTIAL ->
                    if (actual.base.startsWith(typed.base)) score += EXACT_SPELLING / 2
                typed.base == actual.base -> score += EXACT_SPELLING
            }
            if (typed.tone != Syllable.TONE_UNSPECIFIED) {
                score += if (typed.tone == actual.withImplicitTone().tone) TONE_MATCH else TONE_MISMATCH
            }
        }
        val hanlo = entry.hanlo
        score += history.boost(hanlo, entry.romanization)
        return Candidate(
            hanlo = hanlo,
            hanloPoj = entry.hanloPoj,
            tailo = Romanization.render(syllables, RomanSystem.TAILO),
            poj = Romanization.render(syllables, RomanSystem.POJ),
            numeric = entry.romanization,
            consumed = input.last().rawEnd,
            score = score,
        )
    }

    /** The whole input rendered as typed, converting tone digits to marks. */
    private fun romanCandidate(raw: String, segmentation: List<InputSyllable>): Candidate {
        fun render(system: RomanSystem) = buildString {
            for ((i, s) in segmentation.withIndex()) {
                if (i > 0) append(s.boundary.text)
                if (s.kind == InputSyllable.Kind.COMPLETE) {
                    append(Romanization.render(s.toSyllable(), system))
                } else {
                    append(s.raw)
                }
            }
        }
        val numeric = segmentation.joinToString("-") { if (it.kind == InputSyllable.Kind.COMPLETE) it.toSyllable().toNumeric() else it.raw }
        return Candidate(
            hanlo = null,
            hanloPoj = null,
            tailo = render(RomanSystem.TAILO),
            poj = render(RomanSystem.POJ),
            numeric = numeric,
            consumed = raw.length,
            score = ROMAN_CANDIDATE + history.boost(null, numeric),
        )
    }

    companion object {
        private const val FULL_MATCH = 10_000
        private const val PARTIAL_MATCH_PER_SYLLABLE = 1_000
        private const val EXTRA_SYLLABLE_PENALTY = 300
        private const val ROMAN_CANDIDATE = 500
        private const val EXACT_SPELLING = 40
        private const val TONE_MATCH = 60
        private const val TONE_MISMATCH = -400
        private const val WEIGHT_FACTOR = 30.0
        private const val PREFIX_LIMIT = 30
    }
}
