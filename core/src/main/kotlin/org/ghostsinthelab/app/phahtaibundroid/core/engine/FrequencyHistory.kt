package org.ghostsinthelab.app.phahtaibundroid.core.engine

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import java.io.Reader
import java.io.Writer

/**
 * [UserHistory] counting how often each candidate was chosen, and which word the user chose right
 * after which. Persisted as TSV lines of `count<TAB>numeric<TAB>hanlo` (empty hanlo for
 * romanization picks) and, for word pairs, `count<TAB>numeric1<TAB>hanlo1<TAB>numeric2<TAB>hanlo2`.
 * Both are kept least-recently-used first and trimmed to their size limits.
 */
class FrequencyHistory(
    private val maxEntries: Int = 5000,
    private val maxPairs: Int = 5000,
) : UserHistory {
    private val counts = LinkedHashMap<String, Int>(16, 0.75f, true)
    private val pairs = LinkedHashMap<String, Int>(16, 0.75f, true)

    @Volatile
    var dirty = false
        private set

    @Synchronized
    override fun boost(hanlo: String?, numeric: String): Int {
        val n = counts[key(hanlo, numeric)] ?: return 0
        return BOOST_BASE + minOf(n, MAX_COUNTED) * BOOST_PER_USE
    }

    @Synchronized
    override fun record(hanlo: String?, numeric: String) {
        val k = key(hanlo, numeric)
        counts[k] = (counts[k] ?: 0) + 1
        if (counts.size > maxEntries) counts.remove(counts.keys.first())
        dirty = true
    }

    @Synchronized
    override fun pairBoost(previous: LexiconEntry, next: LexiconEntry): Int {
        val n = pairs[pairKey(previous, next)] ?: return 0
        return PAIR_BOOST_BASE + minOf(n, MAX_COUNTED) * PAIR_BOOST_PER_USE
    }

    @Synchronized
    override fun recordPair(previous: LexiconEntry, next: LexiconEntry) {
        val k = pairKey(previous, next)
        pairs[k] = (pairs[k] ?: 0) + 1
        if (pairs.size > maxPairs) pairs.remove(pairs.keys.first())
        dirty = true
    }

    @Synchronized
    fun load(reader: Reader) {
        reader.buffered().forEachLine { line ->
            val f = line.split('\t')
            val n = f[0].toIntOrNull() ?: return@forEachLine
            when (f.size) {
                3 -> counts[key(f[2].ifEmpty { null }, f[1])] = n
                5 -> pairs[key(f[2], f[1]) + PAIR_SEPARATOR + key(f[4], f[3])] = n
            }
        }
    }

    @Synchronized
    fun save(writer: Writer) {
        for ((k, n) in counts) {
            val (numeric, hanlo) = k.split(SEPARATOR, limit = 2)
            writer.append("$n\t$numeric\t$hanlo\n")
        }
        for ((k, n) in pairs) {
            val (first, second) = k.split(PAIR_SEPARATOR, limit = 2)
            val (numeric1, hanlo1) = first.split(SEPARATOR, limit = 2)
            val (numeric2, hanlo2) = second.split(SEPARATOR, limit = 2)
            writer.append("$n\t$numeric1\t$hanlo1\t$numeric2\t$hanlo2\n")
        }
        dirty = false
    }

    private fun key(hanlo: String?, numeric: String) = numeric + SEPARATOR + hanlo.orEmpty()

    private fun pairKey(previous: LexiconEntry, next: LexiconEntry) =
        key(previous.hanlo, previous.romanization) + PAIR_SEPARATOR + key(next.hanlo, next.romanization)

    private companion object {
        const val SEPARATOR = "\u0000"
        const val PAIR_SEPARATOR = "\u0001"

        /** A word chosen after the same word before should beat one merely chosen often. */
        const val PAIR_BOOST_BASE = 300
        const val PAIR_BOOST_PER_USE = 60
        const val BOOST_BASE = 200
        const val BOOST_PER_USE = 50
        const val MAX_COUNTED = 20
    }
}
