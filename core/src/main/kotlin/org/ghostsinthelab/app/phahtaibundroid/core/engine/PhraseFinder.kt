package org.ghostsinthelab.app.phahtaibundroid.core.engine

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Bigrams
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Syllable
import kotlin.math.ln

/**
 * Finds the most likely sequence of words for a whole segmented input, so that long unseparated
 * input such as `guasiutaioanlang` converts to 我是台灣人 in one go.
 *
 * A Viterbi search over the lattice of lexicon entries matching syllable spans, scored by a
 * bigram language model: P(b | a) interpolates the word-pair count with the unigram probability
 * estimated from entry weights. Scores are `SCALE · ln P` plus the spelling, tone and user
 * history adjustments [CandidateEngine] also applies to single words.
 */
internal class PhraseFinder(
    private val lexicon: Lexicon,
    private val history: UserHistory,
) {
    private val bigrams: Bigrams = lexicon.bigrams
    private val totalWeight: Double = lexicon.entries.sumOf { it.weight.toLong() }.toDouble().coerceAtLeast(1.0)

    /** `SCALE · ln(totalWeight)`: added to a path score to compare it with a single entry's `SCALE · ln(weight)`. */
    val scoreOffset: Int = (SCALE * ln(totalWeight)).toInt()

    class Phrase(val entries: List<LexiconEntry>, val score: Int)

    private class State(val entry: LexiconEntry?, val score: Double, val previous: State?)

    private class Edge(val entry: LexiconEntry, val score: Double)

    /**
     * The best word sequence covering all of [input], or `null` if some span has no entry.
     * [context] is the word before the input, if known.
     */
    fun best(input: List<InputSyllable>, context: LexiconEntry?): Phrase? {
        val n = input.size
        if (n == 0 || input.any { it.kind == InputSyllable.Kind.UNKNOWN }) return null
        val beams = Array(n + 1) { ArrayList<State>(BEAM) }
        beams[0] += State(context, 0.0, null)
        for (i in 0 until n) {
            val from = beams[i]
            if (from.isEmpty()) continue
            for (j in i + 1..minOf(n, i + MAX_WORD_SYLLABLES)) {
                val edges = edges(input.subList(i, j)).ifEmpty {
                    // A syllable that is no word by itself is kept as romanization, so a phrase
                    // can still be formed; with weight 0 it costs a lot.
                    if (j == i + 1 && input[i].kind == InputSyllable.Kind.COMPLETE) listOf(Edge(romanEntry(input[i]), 0.0)) else emptyList()
                }
                if (edges.isEmpty()) continue
                val to = beams[j]
                for (state in from) for (edge in edges) {
                    val score = state.score + edge.score + SCALE * logProbability(state.entry, edge.entry)
                    insert(to, State(edge.entry, score, state))
                }
            }
        }
        val end = beams[n].firstOrNull() ?: return null
        val entries = ArrayList<LexiconEntry>()
        var s: State? = end
        while (s != null && s.previous != null) {
            entries += s.entry!!
            s = s.previous
        }
        entries.reverse()
        return Phrase(entries, end.score.toInt() + scoreOffset)
    }

    /** Extra score for [entry] when it follows [context] more often than its frequency predicts. */
    fun contextBonus(context: LexiconEntry, entry: LexiconEntry): Int {
        if (bigrams.count(context, entry) == 0) return 0
        val gain = logProbability(context, entry) - ln(unigram(entry))
        return (SCALE * gain).toInt().coerceIn(0, MAX_CONTEXT_BONUS)
    }

    private fun unigram(entry: LexiconEntry) = (entry.weight + 1) / totalWeight

    /** ln P([entry] | [previous]), interpolating bigram and unigram estimates. */
    private fun logProbability(previous: LexiconEntry?, entry: LexiconEntry): Double {
        val p = unigram(entry)
        if (previous == null) return ln(p)
        val pairs = bigrams.count(previous, entry)
        if (pairs == 0) return ln(UNIGRAM_SHARE * p)
        // Weights are dictionary prior + CORPUS_WEIGHT per corpus occurrence, so this is at most ~1.
        val conditional = (pairs * Bigrams.CORPUS_WEIGHT).toDouble() / maxOf(previous.weight, pairs * Bigrams.CORPUS_WEIGHT)
        return ln(UNIGRAM_SHARE * p + (1 - UNIGRAM_SHARE) * conditional)
    }

    /** Entries spelled like [span], with their spelling, tone and history adjustments, best first. */
    private fun edges(span: List<InputSyllable>): List<Edge> {
        val last = span.last()
        if (span.subList(0, span.size - 1).any { it.kind != InputSyllable.Kind.COMPLETE }) return emptyList()
        val entries = if (last.kind == InputSyllable.Kind.PARTIAL) {
            lexicon.lookupPrefix(span.dropLast(1).map { it.loose }, last.loose, EDGE_SCAN)
        } else {
            lexicon.lookup(span.map { it.loose })
        }
        if (entries.isEmpty()) return emptyList()
        val edges = ArrayList<Edge>(minOf(entries.size, EDGE_SCAN))
        for (entry in entries.take(EDGE_SCAN)) {
            val adjustment = adjustment(entry, span) ?: continue
            edges += Edge(entry, adjustment.toDouble())
        }
        // Edge scores omit the language model, which is applied per transition; rank with it here.
        edges.sortByDescending { it.score + SCALE * ln(unigram(it.entry)) }
        return if (edges.size > EDGE_KEEP) edges.subList(0, EDGE_KEEP) else edges
    }

    /** Spelling, tone and history adjustments, mirroring [CandidateEngine]; `null` if unusable. */
    private fun adjustment(entry: LexiconEntry, span: List<InputSyllable>): Int? {
        val syllables = numericSyllables(entry.romanization) ?: return null
        if (syllables.size != span.size) return null
        var score = 0
        for ((typed, actual) in span.zip(syllables)) {
            val (base, tone) = actual
            when {
                typed.kind == InputSyllable.Kind.PARTIAL -> if (base.startsWith(typed.base)) score += CandidateEngine.EXACT_SPELLING / 2
                typed.base == base -> score += CandidateEngine.EXACT_SPELLING
            }
            if (typed.tone != Syllable.TONE_UNSPECIFIED) {
                score += if (typed.tone == tone) CandidateEngine.TONE_MATCH else CandidateEngine.TONE_MISMATCH
            }
        }
        return score + history.boost(entry.hanlo, entry.romanization)
    }

    /** A pseudo-entry writing [syllable] in romanization, also in Hàn-lô. */
    private fun romanEntry(syllable: InputSyllable): LexiconEntry {
        val s = listOf(syllable.toSyllable())
        val tailo = Romanization.render(s, RomanSystem.TAILO)
        val poj = Romanization.render(s, RomanSystem.POJ)
        return LexiconEntry(tailo, poj.takeIf { it != tailo }, s[0].toNumeric(), 0)
    }

    /** Adds [state] to [beam] (best first), keeping only the best state per last word. */
    private fun insert(beam: ArrayList<State>, state: State) {
        if (beam.size == BEAM && beam.last().score >= state.score) return
        val same = beam.indexOfFirst { it.entry === state.entry }
        if (same >= 0) {
            if (beam[same].score >= state.score) return
            beam.removeAt(same)
        }
        var i = beam.size
        while (i > 0 && beam[i - 1].score < state.score) i--
        beam.add(i, state)
        if (beam.size > BEAM) beam.removeAt(beam.lastIndex)
    }

    companion object {
        /** Score points per unit of natural-log probability (as for weights in [CandidateEngine]). */
        const val SCALE = 30.0
        private const val UNIGRAM_SHARE = 0.6
        private const val MAX_CONTEXT_BONUS = 300
        private const val MAX_WORD_SYLLABLES = 6
        private const val BEAM = 6
        private const val EDGE_SCAN = 40
        private const val EDGE_KEEP = 8

        /**
         * Lowercase bases and tones of compiled numeric Tâi-lô such as `Tai5-gi2` or
         * `am-gong7--e5`, without full parsing; implicit tones are filled in.
         */
        internal fun numericSyllables(numeric: String): List<Pair<String, Int>>? {
            val result = ArrayList<Pair<String, Int>>(4)
            val sb = StringBuilder()
            var tone = Syllable.TONE_UNSPECIFIED
            fun flush() {
                if (sb.isEmpty()) return
                val base = sb.toString()
                result += base to if (tone != Syllable.TONE_UNSPECIFIED) tone else if (base.last() in "ptkh") 4 else 1
                sb.setLength(0)
                tone = Syllable.TONE_UNSPECIFIED
            }
            for (c in numeric) {
                when (c) {
                    in 'a'..'z' -> sb.append(c)
                    in 'A'..'Z' -> sb.append(c.lowercaseChar())
                    in '0'..'9' -> tone = c - '0'
                    '-', ' ' -> flush()
                    else -> return null
                }
            }
            flush()
            return result.ifEmpty { null }
        }
    }
}
