package org.ghostsinthelab.app.phahtaibundroid.dictbuilder

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import java.io.File
import java.text.Normalizer

/**
 * A word-segmented parallel corpus: each sentence is a list of Hàn-lô tokens with their
 * romanized readings, aligned one to one.
 */
internal typealias Sentence = List<Pair<String, String>>

/**
 * Reads iCorpus 臺華平行新聞語料庫 漢字臺羅版 (https://github.com/Taiwanese-Corpus/icorpus_ka1_han3-ji7):
 * line n of [hanji] holds the space-separated Hàn-lô words of a sentence and line n of [tailo]
 * their numeric Tâi-lô readings. Lines whose token counts differ (a handful) are skipped, and
 * repeated sentences (e.g. a headline repeated as the lead, or a sign-off) are counted once.
 */
internal fun readAlignedCorpus(hanji: File, tailo: File): List<Sentence> {
    val seen = HashSet<String>()
    val sentences = ArrayList<Sentence>()
    hanji.bufferedReader(Charsets.UTF_8).use { h ->
        tailo.bufferedReader(Charsets.UTF_8).use { t ->
            while (true) {
                val hl = h.readLine() ?: break
                val tl = t.readLine() ?: break
                val hs = Normalizer.normalize(hl, Normalizer.Form.NFC).split(' ', '　').filter { it.isNotEmpty() }
                val ts = tl.split(' ', '　').filter { it.isNotEmpty() }
                if (hs.isEmpty() || hs.size != ts.size) continue
                if (!seen.add("$hl\n$tl")) continue
                sentences += hs.zip(ts)
            }
        }
    }
    return sentences
}

/**
 * Counts how often lexicon entries (unigrams) and pairs of consecutive entries (bigrams) occur in
 * a corpus.
 *
 * A corpus token matches an entry with the same reading (tones and syllables, ignoring case and
 * boundaries) and the same Hàn-lô, allowing a few common character variants ([VARIANTS]). A token
 * without such an entry, if written in Han characters one per syllable, is split into the fewest
 * entries that spell it (e.g. 民視新聞 → 民視 + 新聞). Tokens that still don't match are
 * collected as [Result.unknown] and break the chain of bigrams, as does punctuation.
 */
internal class CorpusCounter(private val entries: List<LexiconEntry>) {
    private val byReading = HashMap<String, MutableList<Int>>(entries.size)

    init {
        for ((i, e) in entries.withIndex()) {
            val key = lexiconReadingKey(e.romanization) ?: continue
            byReading.getOrPut(key) { ArrayList(2) } += i
        }
    }

    class Result(
        /** Occurrences per entry index. */
        val unigrams: IntArray,
        /** Occurrences per (first, second) entry index pair, packed into a Long. */
        val bigrams: Map<Long, Int>,
        /** Unmatched (Hàn-lô, reading key) tokens and their counts. */
        val unknown: Map<Pair<String, String>, Int>,
        val tokens: Int,
        val wordTokens: Int,
        val exactTokens: Int,
        val splitTokens: Int,
    )

    fun count(sentences: List<Sentence>): Result {
        val unigrams = IntArray(entries.size)
        val bigrams = HashMap<Long, Int>()
        val unknown = HashMap<Pair<String, String>, Int>()
        var tokens = 0
        var wordTokens = 0
        var exact = 0
        var split = 0
        for (sentence in sentences) {
            var previous = -1
            for ((hanlo, reading) in sentence) {
                tokens++
                val syllables = corpusReadingSyllables(reading)
                if (syllables == null) { // punctuation, numbers, foreign words
                    previous = -1
                    continue
                }
                wordTokens++
                val pieces = matchExact(hanlo, syllables)?.let { exact++; listOf(it) }
                    ?: split(hanlo, syllables)?.also { split++ }
                if (pieces == null) {
                    unknown.merge(hanlo to syllables.joinToString("-"), 1, Int::plus)
                    previous = -1
                    continue
                }
                for (p in pieces) {
                    unigrams[p]++
                    if (previous >= 0) bigrams.merge(pairKey(previous, p), 1, Int::plus)
                    previous = p
                }
            }
        }
        return Result(unigrams, bigrams, unknown, tokens, wordTokens, exact, split)
    }

    /**
     * The entry for a corpus word. Of variant spellings (臺灣/台灣), the one the dictionaries
     * weight highest gets the count, so orthography follows the dictionaries and frequency the
     * corpus.
     */
    private fun matchExact(hanlo: String, syllables: List<String>): Int? {
        val candidates = byReading[syllables.joinToString("-")] ?: return null
        val normalized = normalizeVariants(hanlo)
        return candidates
            .filter { normalizeVariants(entries[it].hanlo) == normalized }
            .maxWithOrNull(compareBy({ entries[it].weight }, { entries[it].hanlo == hanlo }))
    }

    /** Splits a Han-character word into the fewest entries, or `null` if impossible. */
    fun split(hanlo: String, syllables: List<String>): List<Int>? {
        val chars = hanlo.codePoints().toArray()
        val n = chars.size
        if (n < 2 || n != syllables.size || !chars.all { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }) {
            return null
        }
        fun text(i: Int, j: Int) = String(chars, i, j - i)
        val best = arrayOfNulls<List<Int>>(n + 1).also { it[0] = emptyList() }
        for (j in 1..n) {
            for (i in maxOf(0, j - MAX_PIECE)..j - 1) {
                if (i == 0 && j == n) continue // the whole word is what failed to match
                val head = best[i] ?: continue
                val piece = matchExact(text(i, j), syllables.subList(i, j)) ?: continue
                if (best[j] == null || head.size + 1 < best[j]!!.size) best[j] = head + piece
            }
        }
        return best[n]
    }

    companion object {
        private const val MAX_PIECE = 8

        /** Character variants treated as equal when matching corpus words to entries. */
        private val VARIANTS = mapOf('臺' to '台', '裏' to '裡', '着' to '著', '爲' to '為', '眞' to '真')

        fun pairKey(first: Int, second: Int) = (first.toLong() shl 32) or second.toLong()

        fun normalizeVariants(s: String): String =
            if (s.none { it in VARIANTS }) s else String(CharArray(s.length) { VARIANTS[s[it]] ?: s[it] })

        /** `Tai5-uan5` → `tai5-uan5`; neutral-tone and space boundaries are ignored. */
        fun lexiconReadingKey(numeric: String): String? =
            numeric.lowercase().split('-', ' ').filter { it.isNotEmpty() }.ifEmpty { null }?.joinToString("-")

        /** Canonical lowercase numeric syllables of a corpus reading, or `null` if not Taigi. */
        fun corpusReadingSyllables(reading: String): List<String>? {
            if (reading.none { it.isLetter() }) return null
            val parsed = Romanization.parseWord(reading) ?: return null
            return parsed.map { it.withImplicitTone().toNumeric().lowercase() }
        }
    }
}
