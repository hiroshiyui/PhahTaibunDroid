package org.ghostsinthelab.app.phahtaibun.core.engine

import java.io.Reader
import java.io.Writer

/**
 * [UserHistory] counting how often each candidate was chosen. Persisted as TSV lines of
 * `count<TAB>numeric<TAB>hanlo` (empty hanlo for romanization picks).
 */
class FrequencyHistory(private val maxEntries: Int = 5000) : UserHistory {
    private val counts = LinkedHashMap<String, Int>(16, 0.75f, true)

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
    fun load(reader: Reader) {
        reader.buffered().forEachLine { line ->
            val f = line.split('\t')
            if (f.size == 3) f[0].toIntOrNull()?.let { counts[key(f[2].ifEmpty { null }, f[1])] = it }
        }
    }

    @Synchronized
    fun save(writer: Writer) {
        for ((k, n) in counts) {
            val (numeric, hanlo) = k.split(SEPARATOR, limit = 2)
            writer.append("$n\t$numeric\t$hanlo\n")
        }
        dirty = false
    }

    private fun key(hanlo: String?, numeric: String) = numeric + SEPARATOR + hanlo.orEmpty()

    private companion object {
        const val SEPARATOR = "\u0000"
        const val BOOST_BASE = 200
        const val BOOST_PER_USE = 50
        const val MAX_COUNTED = 20
    }
}
