package org.ghostsinthelab.app.phahtaibundroid.core.engine

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Boundary
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Syllable
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
    /** The lexicon entries this candidate is made of (several for a multi-word phrase). */
    val entries: List<LexiconEntry> = emptyList(),
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

    /** Extra score for [next] right after [previous], from word pairs the user chose before. */
    fun pairBoost(previous: LexiconEntry, next: LexiconEntry): Int = 0

    /** Notes that the user chose [next] right after [previous]. */
    fun recordPair(previous: LexiconEntry, next: LexiconEntry) = Unit

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
 * words can still be typed. Abbreviated (`tg`) and wildcard (`s?ah`) syllables are matched by
 * pattern and rank below fully spelled matches.
 */
class CandidateEngine(
    val lexicon: Lexicon,
    private val history: UserHistory = UserHistory.None,
) {
    private val segmenter = Segmenter(lexicon)
    private val phrases = PhraseFinder(lexicon, history)

    fun segment(raw: String): List<List<InputSyllable>> = segmenter.segment(raw)

    /**
     * Ranked candidates for [raw]. [context] is the word selected just before (e.g. the last
     * piece of the composition); words that often follow it are boosted.
     */
    fun candidates(raw: String, limit: Int = 80, context: LexiconEntry? = null): List<Candidate> {
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
                val abbreviated = prefix.any { it.kind == InputSyllable.Kind.INITIAL }
                val wildcard = prefix.any { it.kind == InputSyllable.Kind.WILDCARD }
                // A single abbreviated syllable would match far too much to be useful.
                if (abbreviated && k < 2) continue
                val last = prefix.last()
                val entries = if (abbreviated || wildcard) {
                    val patterns = prefix.mapNotNull { SyllablePatterns.of(lexicon, it) }
                    if (patterns.size == k) lexicon.lookupMatching(patterns, PATTERN_FETCH) else emptyList()
                } else if (last.kind == InputSyllable.Kind.PARTIAL) {
                    lexicon.lookupPrefix(prefix.dropLast(1).map { it.loose }, last.loose, PREFIX_LIMIT)
                } else {
                    lexicon.lookup(prefix.map { it.loose })
                }
                val patternPenalty = (if (abbreviated) ABBREVIATION_PENALTY else 0) + (if (wildcard) WILDCARD_PENALTY else 0)
                val coverage = if (k == seg.size) {
                    FULL_MATCH - segPenalty - patternPenalty
                } else {
                    PARTIAL_MATCH_PER_SYLLABLE * k - patternPenalty / PARTIAL_PATTERN_DIVISOR
                }
                val found = entries.mapNotNull { fromEntry(it, prefix, coverage, context) }
                // Pattern matches are many and weights are coarse: keep only the best scored.
                val kept = if (abbreviated || wildcard) found.sortedByDescending { it.score }.take(PATTERN_LIMIT) else found
                kept.forEach(::offer)
            }
            neutralSuffixCandidates(seg, FULL_MATCH - segPenalty - RULE_PENALTY, context).forEach(::offer)
        }
        // Multi-word conversions of the whole input, unless much less likely than a single word.
        // Abbreviated and wildcard input stays with single-word matches.
        val phrases = segmentations
            .filter { seg -> seg.none { it.kind == InputSyllable.Kind.INITIAL || it.kind == InputSyllable.Kind.WILDCARD } }
            .take(PHRASE_SEGMENTATIONS).mapNotNull { seg ->
            phraseCandidate(seg, context, FULL_MATCH - (seg.size - fewestSyllables) * EXTRA_SYLLABLE_PENALTY)
        }.sortedByDescending { it.score }
        if (phrases.isNotEmpty()) {
            val bestWord = best.values.filter { it.entries.size == 1 && it.consumed == raw.length }.maxOfOrNull { it.score }
            val floor = maxOf(bestWord ?: Int.MIN_VALUE, phrases.first().score) - PHRASE_MARGIN
            phrases.filter { it.score >= floor }.take(MAX_PHRASES).forEach(::offer)
        }
        // Abbreviations have no romanization of their own; show those as typed.
        offer(romanCandidate(raw, segmentations.firstOrNull { seg -> seg.none { it.kind == InputSyllable.Kind.INITIAL } }))
        return best.values.sortedByDescending { it.score }.take(limit)
    }

    private fun fromEntry(entry: LexiconEntry, input: List<InputSyllable>, coverage: Int, context: LexiconEntry?): Candidate? {
        val syllables = entry.syllables()
        if (syllables.size != input.size) return null
        var score = coverage + (ln(1.0 + entry.weight) * WEIGHT_FACTOR).toInt()
        for ((i, pair) in input.zip(syllables).withIndex()) {
            val (typed, actual) = pair
            // A typed `--` asks for a neutral-tone entry; without one, entries compete normally.
            if (i > 0 && typed.boundary == Boundary.NEUTRAL) {
                score += if (actual.boundary == Boundary.NEUTRAL) NEUTRAL_MATCH else NEUTRAL_MISMATCH
            }
            when {
                typed.kind == InputSyllable.Kind.PARTIAL ->
                    if (actual.base.startsWith(typed.base)) score += EXACT_SPELLING / 2
                typed.kind == InputSyllable.Kind.INITIAL ->
                    if (SyllablePatterns.isLiteralInitial(typed.base, actual.base)) score += EXACT_SPELLING / 2
                typed.base == actual.base -> score += EXACT_SPELLING
            }
            if (typed.tone != Syllable.TONE_UNSPECIFIED) {
                score += if (typed.tone == actual.withImplicitTone().tone) TONE_MATCH else TONE_MISMATCH
            }
        }
        val hanlo = entry.hanlo
        score += history.boost(hanlo, entry.romanization)
        if (context != null) score += phrases.contextBonus(context, entry) + history.pairBoost(context, entry)
        return Candidate(
            hanlo = hanlo,
            hanloPoj = entry.hanloPoj,
            tailo = Romanization.render(syllables, RomanSystem.TAILO),
            poj = Romanization.render(syllables, RomanSystem.POJ),
            numeric = entry.romanization,
            consumed = input.last().rawEnd,
            score = score,
            entries = listOf(entry),
        )
    }

    /**
     * Rule-generated neutral-tone compounds for input with an explicit `--`: a dictionary word
     * followed by a common neutral-tone suffix (`tsiahpa--ah` → 食飽--矣), for combinations the
     * dictionary lacks. Only the user's own `--` triggers this, so ordinary input never sees
     * such guesses, and only the best-matching word is guessed (no homophone flood).
     */
    private fun neutralSuffixCandidates(seg: List<InputSyllable>, coverage: Int, context: LexiconEntry?): List<Candidate> {
        if (seg.any { it.kind != InputSyllable.Kind.COMPLETE }) return emptyList()
        val found = ArrayList<Candidate>()
        for (j in 1 until seg.size) {
            if (seg[j].boundary != Boundary.NEUTRAL) continue
            val tail = seg.subList(j, seg.size).map { it.loose }
            val (numeric, hanlo) = NEUTRAL_SUFFIXES.firstOrNull { (numeric, _) ->
                Lexicon.baseSequence(numeric)?.map(lexicon::loosen) == tail
            } ?: continue
            found += lexicon.lookup(seg.subList(0, j).map { it.loose }).take(RULE_HEADS).mapNotNull { head ->
                val entry = LexiconEntry(
                    hanlo = head.hanlo + hanlo,
                    hanloPoj = head.hanloPoj?.plus(hanlo),
                    romanization = "${head.romanization}--$numeric",
                    weight = head.weight,
                )
                fromEntry(entry, seg, coverage, context)
            }.maxByOrNull { it.score } ?: continue
        }
        return found
    }

    /**
     * The most likely multi-word conversion of the whole of [input] (see [PhraseFinder]), or
     * `null` if there is none or it is a single entry (already offered by itself). Words are
     * joined with spaces in romanization; in Hàn-lô only between two romanized words.
     */
    private fun phraseCandidate(input: List<InputSyllable>, context: LexiconEntry?, coverage: Int): Candidate? {
        if (input.size < 2) return null
        val phrase = phrases.best(input, context) ?: return null
        val words = phrase.entries
        if (words.size < 2) return null
        val syllables = words.map { it.syllables() }
        val hanlo = joinHanlo(words.map { it.hanlo })
        val hanloPoj = joinHanlo(words.map { it.hanloPoj ?: it.hanlo })
        val numeric = words.joinToString(" ") { it.romanization }
        // A typed `--` asks for neutral tone, which a break between two words can't give.
        val flat = syllables.flatMap { word -> word.mapIndexed { i, syllable -> syllable.takeIf { i > 0 } } }
        val neutral = input.indices.drop(1).filter { input[it].boundary == Boundary.NEUTRAL }
            .sumOf { if (flat.getOrNull(it)?.boundary == Boundary.NEUTRAL) NEUTRAL_MATCH else NEUTRAL_MISMATCH }
        return Candidate(
            hanlo = hanlo,
            hanloPoj = hanloPoj.takeIf { it != hanlo },
            tailo = syllables.joinToString(" ") { Romanization.render(it, RomanSystem.TAILO) },
            poj = syllables.joinToString(" ") { Romanization.render(it, RomanSystem.POJ) },
            numeric = numeric,
            consumed = input.last().rawEnd,
            score = coverage + phrase.score + neutral + history.boost(hanlo, numeric),
            entries = words,
        )
    }

    private fun joinHanlo(words: List<String>): String = buildString {
        for (w in words) {
            if (isNotEmpty() && last().isLatin() && w.first().isLatin()) append(' ')
            append(w)
        }
    }

    private fun Char.isLatin() = Character.UnicodeScript.of(code) == Character.UnicodeScript.LATIN ||
        Character.getType(this) == Character.NON_SPACING_MARK.toInt()

    /** The whole input rendered as typed, converting tone digits to marks. */
    private fun romanCandidate(raw: String, segmentation: List<InputSyllable>?): Candidate {
        fun render(system: RomanSystem) = if (segmentation == null) raw else buildString {
            for ((i, s) in segmentation.withIndex()) {
                if (i > 0) append(s.boundary.text)
                if (s.kind == InputSyllable.Kind.COMPLETE) {
                    append(Romanization.render(s.toSyllable(), system))
                } else {
                    append(s.raw)
                }
            }
        }
        val numeric = segmentation?.joinToString("-") { if (it.kind == InputSyllable.Kind.COMPLETE) it.toSyllable().toNumeric() else it.raw } ?: raw
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
        internal const val EXACT_SPELLING = 40
        internal const val TONE_MATCH = 60
        internal const val TONE_MISMATCH = -400
        private const val WEIGHT_FACTOR = 30.0
        private const val PREFIX_LIMIT = 30
        private const val NEUTRAL_MATCH = 60
        private const val NEUTRAL_MISMATCH = -300
        private const val RULE_PENALTY = 200
        private const val RULE_HEADS = 5

        /**
         * Productive neutral-tone suffixes (numeric Tâi-lô to Hàn-lô): directional complements,
         * `--ê`, the perfective/sentence-final `--ah`, and resultative/durative `--tio̍h`/`--leh`/`--tit`.
         */
        private val NEUTRAL_SUFFIXES = listOf(
            "lai5" to "來", "khi3" to "去",
            "khi2-lai5" to "起來", "tshut4-lai5" to "出來", "tshut4-khi3" to "出去",
            "loh8-lai5" to "落來", "loh8-khi3" to "落去", "jip8-lai5" to "入來", "jip8-khi3" to "入去",
            "tng2-lai5" to "轉來", "tng2-khi3" to "轉去", "kue3-lai5" to "過來", "kue3-khi3" to "過去",
            "e5" to "的", "ah4" to "矣", "tioh8" to "著", "leh4" to "咧", "tit4" to "得",
        )
        private const val PATTERN_LIMIT = 12
        private const val PATTERN_FETCH = 4 * PATTERN_LIMIT
        private const val ABBREVIATION_PENALTY = 3_000
        private const val WILDCARD_PENALTY = 1_000
        private const val PARTIAL_PATTERN_DIVISOR = 10
        private const val PHRASE_SEGMENTATIONS = 4
        private const val MAX_PHRASES = 2

        /** Phrases scoring this much below the best phrase or whole-input word are dropped. */
        private const val PHRASE_MARGIN = 150
    }
}
