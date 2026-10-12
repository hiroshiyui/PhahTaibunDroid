package org.ghostsinthelab.app.phahtaibundroid.core.lexicon

import java.io.BufferedReader
import java.io.InputStream
import java.io.Writer
import java.util.IdentityHashMap

/**
 * Word-pair (bigram) counts between [LexiconEntry]s: how often entry `b` directly follows entry
 * `a` in running text. Used to rank multi-word conversions of long input and to boost words that
 * commonly follow the one just selected.
 *
 * Stored compactly: entries that occur in some pair get a small vocabulary id, and each pair is a
 * `Long` key in a sorted array searched by binary search.
 */
class Bigrams private constructor(
    private val vocabulary: IdentityHashMap<LexiconEntry, Int>,
    private val keys: LongArray,
    private val counts: IntArray,
) {
    val size: Int get() = keys.size

    /** How often [second] follows [first]; 0 if never seen. */
    fun count(first: LexiconEntry, second: LexiconEntry): Int {
        val a = vocabulary[first] ?: return 0
        val b = vocabulary[second] ?: return 0
        val i = keys.binarySearch(key(a, b))
        return if (i >= 0) counts[i] else 0
    }

    /** One pair of entries and its count. */
    class Pair(val first: LexiconEntry, val second: LexiconEntry, val count: Int)

    companion object {
        /**
         * What one corpus occurrence adds to an entry's [LexiconEntry.weight]. The dictionary
         * weight (2–3 per source listing the word, 1 per alternative reading) stays as a prior, so
         * words absent from the corpus keep their relative order and one corpus occurrence counts
         * about as much as one more dictionary listing. Corpus counts can thus be estimated from
         * weights, e.g. for the conditional probability of a pair.
         */
        const val CORPUS_WEIGHT = 20

        val NONE = Bigrams(IdentityHashMap(), LongArray(0), IntArray(0))

        private fun key(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()

        fun of(pairs: Collection<Pair>): Bigrams {
            val vocabulary = IdentityHashMap<LexiconEntry, Int>()
            val byKey = HashMap<Long, Int>(pairs.size * 2)
            for (p in pairs) {
                val a = vocabulary.getOrPut(p.first) { vocabulary.size }
                val b = vocabulary.getOrPut(p.second) { vocabulary.size }
                byKey.merge(key(a, b), p.count, Int::plus)
            }
            val keys = byKey.keys.toLongArray().also { it.sort() }
            return Bigrams(vocabulary, keys, IntArray(keys.size) { byKey.getValue(keys[it]) })
        }

        /**
         * Reads `hanlo1<TAB>numeric1<TAB>hanlo2<TAB>numeric2<TAB>count` lines (`#` starts a comment)
         * and resolves the words against [entries] by exact Hàn-lô and romanization. Pairs naming
         * words missing from [entries] are ignored, so a stale file degrades gracefully.
         */
        fun read(input: InputStream, entries: List<LexiconEntry>): Bigrams =
            input.bufferedReader(Charsets.UTF_8).use { read(it, entries) }

        fun read(reader: BufferedReader, entries: List<LexiconEntry>): Bigrams {
            // Collect the distinct words ("hanlo<TAB>numeric") first, then resolve them with one
            // pass over the entries.
            val wordIds = HashMap<String, Int>()
            val hanloForms = HashSet<String>()
            var keys = LongArray(1024)
            var counts = IntArray(1024)
            var size = 0
            fun word(line: String, start: Int, end: Int): Int {
                val text = line.substring(start, end)
                return wordIds.getOrPut(text) {
                    hanloForms += text.substring(0, text.indexOf('\t'))
                    wordIds.size
                }
            }
            reader.forEachLine { line ->
                if (line.isEmpty() || line[0] == '#') return@forEachLine
                val t1 = line.indexOf('\t')
                val t2 = line.indexOf('\t', t1 + 1)
                val t3 = line.indexOf('\t', t2 + 1)
                val t4 = line.indexOf('\t', t3 + 1)
                if (t1 < 0 || t2 < 0 || t3 < 0 || t4 < 0) return@forEachLine
                val count = line.substring(t4 + 1).toIntOrNull() ?: return@forEachLine
                if (size == keys.size) {
                    keys = keys.copyOf(size * 2)
                    counts = counts.copyOf(size * 2)
                }
                keys[size] = key(word(line, 0, t2), word(line, t2 + 1, t4))
                counts[size++] = count
            }
            if (size == 0) return NONE
            val vocabulary = IdentityHashMap<LexiconEntry, Int>(wordIds.size)
            for (e in entries) {
                if (e.hanlo !in hanloForms) continue
                val id = wordIds[e.hanlo + '\t' + e.romanization] ?: continue
                vocabulary.putIfAbsent(e, id)
            }
            val resolved = BooleanArray(wordIds.size)
            for (id in vocabulary.values) resolved[id] = true
            // Keep resolvable pairs, sorted by key for binary search (the file is nearly sorted).
            val order = (0 until size)
                .filter { resolved[(keys[it] ushr 32).toInt()] && resolved[keys[it].toInt()] }
                .sortedBy { keys[it] }
            return Bigrams(vocabulary, LongArray(order.size) { keys[order[it]] }, IntArray(order.size) { counts[order[it]] })
        }

        fun write(pairs: List<Pair>, out: Writer) {
            for (p in pairs) {
                out.append(p.first.hanlo).append('\t').append(p.first.romanization).append('\t')
                    .append(p.second.hanlo).append('\t').append(p.second.romanization).append('\t')
                    .append(p.count.toString()).append('\n')
            }
        }
    }
}
