package org.ghostsinthelab.app.phahtaibundroid.core.lexicon

import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule

/**
 * Turns compiled numeric romanization into loose index keys without creating a string per
 * syllable: each syllable's letters are packed into a `Long` (5 bits a letter) and looked up in a
 * small open-addressing table of the (about a thousand) distinct syllables.
 */
internal class SyllableTable(private val rules: Set<FuzzyRule>) {
    private var codes = LongArray(INITIAL_CAPACITY)
    private var bases = arrayOfNulls<String>(INITIAL_CAPACITY)
    private var looses = arrayOfNulls<String>(INITIAL_CAPACITY)
    private var size = 0

    /** Syllables too long to pack, looked up by string instead (none in practice). */
    private val long = HashMap<String, String>()

    /**
     * The loose key of [numeric] (e.g. `Tai5-gi2` → `tai gi`), built in [builder]; `null` if it
     * contains anything but letters, tone digits and `-`/space separators.
     */
    fun looseKey(numeric: String, builder: StringBuilder): String? {
        builder.setLength(0)
        var code = 0L
        var length = 0
        var start = 0
        fun flush(end: Int) {
            if (length == 0) return
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(if (length <= MAX_PACKED) loose(code) else longLoose(numeric, start, end))
            code = 0L
            length = 0
        }
        for ((i, c) in numeric.withIndex()) {
            val lower = when (c) {
                in 'a'..'z' -> c
                in 'A'..'Z' -> c.lowercaseChar()
                in '0'..'9' -> continue
                '-', ' ' -> {
                    flush(i)
                    continue
                }
                else -> return null
            }
            if (length == 0) start = i
            code = code shl BITS or (lower - 'a' + 1).toLong()
            length++
        }
        flush(numeric.length)
        return if (builder.isEmpty()) null else builder.toString()
    }

    /** Canonical base → loose form, for every syllable seen. */
    fun canonicalToLoose(): Map<String, String> {
        val map = HashMap<String, String>(size + long.size)
        for (i in codes.indices) if (codes[i] != 0L) map[bases[i]!!] = looses[i]!!
        for ((base, loose) in long) map[base.lowercase()] = loose
        return map
    }

    private fun loose(code: Long): String {
        var slot = slot(code, codes)
        if (codes[slot] == code) return looses[slot]!!
        if ((size + 1) * 2 > codes.size) {
            grow()
            slot = slot(code, codes)
        }
        val base = decode(code)
        codes[slot] = code
        bases[slot] = base
        looses[slot] = FuzzyRule.loosen(base, rules)
        size++
        return looses[slot]!!
    }

    private fun longLoose(numeric: String, start: Int, end: Int): String {
        val base = numeric.substring(start, end).filter { it.isLetter() }.lowercase()
        return long.getOrPut(base) { FuzzyRule.loosen(base, rules) }
    }

    private fun grow() {
        val oldCodes = codes
        val oldBases = bases
        val oldLooses = looses
        codes = LongArray(oldCodes.size * 2)
        bases = arrayOfNulls(codes.size)
        looses = arrayOfNulls(codes.size)
        for (i in oldCodes.indices) {
            if (oldCodes[i] == 0L) continue
            val slot = slot(oldCodes[i], codes)
            codes[slot] = oldCodes[i]
            bases[slot] = oldBases[i]
            looses[slot] = oldLooses[i]
        }
    }

    private companion object {
        const val BITS = 5
        const val MAX_PACKED = 12
        const val INITIAL_CAPACITY = 4096

        /** The slot holding [code] in [table], or the empty slot where it belongs. */
        fun slot(code: Long, table: LongArray): Int {
            val mask = table.size - 1
            var i = (java.lang.Long.hashCode(code * -0x61c8864680b583ebL) and mask)
            while (table[i] != 0L && table[i] != code) i = (i + 1) and mask
            return i
        }

        fun decode(code: Long): String {
            val chars = StringBuilder()
            var c = code
            while (c != 0L) {
                chars.append('a' + ((c and 31L).toInt() - 1))
                c = c ushr BITS
            }
            return chars.reverse().toString()
        }
    }
}

/** A growable list of ints, without boxing. */
internal class IntList {
    private var values = IntArray(4)
    private var size = 0

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun toArray(): IntArray = values.copyOf(size)
}

/** Stable merge sort of [array] by [compare], without boxing the ints. */
internal inline fun mergeSort(array: IntArray, crossinline compare: (Int, Int) -> Int) {
    var from = array
    var to = IntArray(array.size)
    var width = 1
    while (width < array.size) {
        var lo = 0
        while (lo < array.size) {
            val mid = minOf(lo + width, array.size)
            val hi = minOf(lo + 2 * width, array.size)
            var i = lo
            var j = mid
            var k = lo
            while (i < mid && j < hi) to[k++] = if (compare(from[j], from[i]) < 0) from[j++] else from[i++]
            while (i < mid) to[k++] = from[i++]
            while (j < hi) to[k++] = from[j++]
            lo += 2 * width
        }
        val swap = from
        from = to
        to = swap
        width *= 2
    }
    if (from !== array) from.copyInto(array)
}
