package org.ghostsinthelab.app.phahtaibundroid.core.lexicon

import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Syllable
import java.io.InputStream
import java.io.Writer
import java.util.concurrent.ConcurrentHashMap

/**
 * One dictionary word.
 *
 * @property hanlo Hàn-lô (漢羅) written form; any romanized parts are spelled in Tâi-lô.
 * @property hanloPoj The same form with romanized parts in POJ, or `null` if identical to [hanlo].
 * @property romanization Numeric Tâi-lô with boundaries, e.g. `Tai5-gi2`, `am-gong7--e5`.
 * @property weight Ranking weight (higher is more common).
 */
class LexiconEntry(
    val hanlo: String,
    val hanloPoj: String?,
    val romanization: String,
    val weight: Int,
) {
    /** Parsed on demand: only the handful of entries that become candidates need it. */
    fun syllables(): List<Syllable> = Romanization.parseWord(romanization).orEmpty()

    override fun toString() = "$hanlo($romanization)"
}

/**
 * Constraint on one syllable of a [Lexicon.lookupMatching] query.
 *
 * @property initials Letters the matching loose syllables may start with (their first letter).
 * @property matches Whether a loose syllable satisfies the constraint.
 */
class SyllablePattern(val initials: String, val matches: (String) -> Boolean)

/**
 * In-memory index over [LexiconEntry]s, keyed by sequences of fuzzy-collapsed ("loose") syllable
 * bases. Rebuild with [withRules] when the user changes fuzzy settings; parsed entries are reused.
 *
 * @property bigrams Word-pair counts between [entries] (see [Bigrams.read]).
 */
class Lexicon(
    val entries: List<LexiconEntry>,
    val rules: Set<FuzzyRule> = FuzzyRule.DEFAULTS,
    val bigrams: Bigrams = Bigrams.NONE,
) {
    // The index is a few flat arrays rather than a map of lists: with ~86k entries, per-key
    // objects and the garbage made while building them dominated the keyboard's memory.

    /** Distinct loose keys (space-joined loose syllables), sorted. */
    private val sortedKeys: Array<String>

    /** Entries grouped by key, in [sortedKeys] order, each group most common first. */
    private val ordered: Array<LexiconEntry>
    private val orderedList: List<LexiconEntry>

    /** The entries of `sortedKeys[k]` are `ordered[keyStart[k] until keyStart[k + 1]]`. */
    private val keyStart: IntArray

    private val syllableSet: Set<String>
    private val sortedSyllables: Array<String>

    /** Canonical syllable bases and their loose forms, for wildcard matching. */
    private val canonicalToLoose: Map<String, String>

    /**
     * Key indices grouped by the first letters of their syllables (`tai gi` → `tg`), most common
     * first. Only abbreviated and wildcard input needs it, so it is built on first use.
     */
    private val byInitials: Map<String, IntArray> by lazy(::buildInitialsIndex)
    private val wildcardCache = ConcurrentHashMap<String, Set<String>>()

    init {
        val syllables = SyllableTable(rules)
        val keys = arrayOfNulls<String>(entries.size)
        val builder = StringBuilder()
        var indexed = 0
        for ((i, entry) in entries.withIndex()) {
            keys[i] = syllables.looseKey(entry.romanization, builder)?.also { indexed++ }
        }
        // Sort entry indices by key, then by weight (most common first); stable for equal weights.
        val order = IntArray(indexed)
        var n = 0
        for (i in entries.indices) if (keys[i] != null) order[n++] = i
        mergeSort(order) { a, b ->
            val byKey = keys[a]!!.compareTo(keys[b]!!)
            if (byKey != 0) byKey else entries[b].weight.compareTo(entries[a].weight)
        }
        val uniqueKeys = ArrayList<String>()
        val starts = IntArray(indexed + 1)
        ordered = Array(indexed) { entries[order[it]] }
        for ((pos, i) in order.withIndex()) {
            val key = keys[i]!!
            if (uniqueKeys.isEmpty() || uniqueKeys.last() != key) {
                starts[uniqueKeys.size] = pos
                uniqueKeys += key
            }
        }
        starts[uniqueKeys.size] = indexed
        sortedKeys = uniqueKeys.toTypedArray()
        keyStart = starts.copyOf(sortedKeys.size + 1)
        orderedList = ordered.asList()
        canonicalToLoose = syllables.canonicalToLoose()
        syllableSet = canonicalToLoose.values.toHashSet()
        sortedSyllables = syllableSet.toTypedArray().also { it.sort() }
    }

    /** The entries of the key at index [k] of [sortedKeys]. */
    private fun group(k: Int): List<LexiconEntry> = orderedList.subList(keyStart[k], keyStart[k + 1])

    private fun buildInitialsIndex(): Map<String, IntArray> {
        // Keys by their most common entry's weight, most common first; ties keep key order.
        val byWeight = IntArray(sortedKeys.size) { it }
        mergeSort(byWeight) { a, b -> ordered[keyStart[b]].weight.compareTo(ordered[keyStart[a]].weight) }
        val grouped = HashMap<String, IntList>()
        val initials = StringBuilder()
        for (k in byWeight) {
            val key = sortedKeys[k]
            initials.setLength(0)
            initials.append(key[0])
            for (i in 1 until key.length) if (key[i - 1] == ' ') initials.append(key[i])
            grouped.getOrPut(initials.toString()) { IntList() }.add(k)
        }
        return grouped.mapValuesTo(HashMap(grouped.size)) { it.value.toArray() }
    }

    fun withRules(rules: Set<FuzzyRule>): Lexicon = if (rules == this.rules) this else Lexicon(entries, rules, bigrams)

    fun loosen(base: String): String = FuzzyRule.loosen(base, rules)

    /** Whether [looseBase] is a syllable occurring in the lexicon. */
    fun isSyllable(looseBase: String): Boolean = looseBase in syllableSet

    /** Whether some syllable in the lexicon starts with [loosePrefix]. */
    fun hasSyllablePrefix(loosePrefix: String): Boolean {
        val i = lowerBound(sortedSyllables, loosePrefix)
        return i < sortedSyllables.size && sortedSyllables[i].startsWith(loosePrefix)
    }

    /** Entries whose loose syllable sequence equals [looseBases], most common first. */
    fun lookup(looseBases: List<String>): List<LexiconEntry> {
        val k = sortedKeys.binarySearch(looseBases.joinToString(KEY_SEPARATOR))
        return if (k >= 0) group(k) else emptyList()
    }

    /**
     * Entries whose loose sequence starts with [completeBases] followed by a syllable starting
     * with [partial] (and possibly more syllables). Used to complete the syllable being typed.
     */
    fun lookupPrefix(completeBases: List<String>, partial: String, limit: Int): List<LexiconEntry> {
        val prefix = (completeBases + partial).joinToString(KEY_SEPARATOR)
        val wanted = completeBases.size + 1
        val found = ArrayList<LexiconEntry>()
        var i = lowerBound(sortedKeys, prefix)
        var scanned = 0
        while (i < sortedKeys.size && scanned < PREFIX_SCAN_LIMIT) {
            val k = i++
            val key = sortedKeys[k]
            if (!key.startsWith(prefix)) break
            scanned++
            // Prefer completions with exactly as many syllables as typed.
            if (key.count { it == ' ' } + 1 != wanted) continue
            found += group(k)
        }
        found.sortByDescending { it.weight }
        return if (found.size > limit) found.subList(0, limit) else found
    }

    /**
     * Loose syllables that [glob] matches, where [glob] is a regular expression over lowercase
     * letters tried against both the canonical and the loose form of every known syllable.
     */
    fun syllablesMatching(glob: Regex): Set<String> = wildcardCache[glob.pattern] ?: run {
        val found = canonicalToLoose.entries
            .filter { (canonical, loose) -> glob.matches(canonical) || glob.matches(loose) }
            .mapTo(HashSet()) { it.value }
        if (wildcardCache.size > WILDCARD_CACHE_SIZE) wildcardCache.clear()
        wildcardCache[glob.pattern] = found
        found
    }

    /**
     * Entries with exactly as many syllables as [patterns], each matching its pattern, most
     * common first. Candidate keys come from the initials index, so a query costs a scan over
     * the words sharing those first letters (bounded by a scan limit), not over the lexicon.
     */
    fun lookupMatching(patterns: List<SyllablePattern>, limit: Int): List<LexiconEntry> {
        if (patterns.isEmpty() || patterns.any { it.initials.isEmpty() }) return emptyList()
        var combos = listOf("")
        for (p in patterns) {
            combos = combos.flatMap { prefix -> p.initials.map { prefix + it } }
            if (combos.size > MAX_INITIALS_COMBOS) return emptyList()
        }
        val found = ArrayList<LexiconEntry>()
        var scanned = 0
        for (combo in combos) {
            val keys = byInitials[combo] ?: continue
            var matched = 0
            for (k in keys) {
                if (matched >= limit || scanned >= MATCH_SCAN_LIMIT) break
                scanned++
                val syllables = sortedKeys[k].split(KEY_SEPARATOR)
                if (patterns.indices.all { patterns[it].matches(syllables[it]) }) {
                    found += group(k)
                    matched++
                }
            }
        }
        found.sortByDescending { it.weight }
        return if (found.size > limit) found.subList(0, limit) else found
    }

    companion object {
        private const val KEY_SEPARATOR = " "
        private const val PREFIX_SCAN_LIMIT = 4000
        private const val MATCH_SCAN_LIMIT = 30_000
        private const val MAX_INITIALS_COMBOS = 512
        private const val WILDCARD_CACHE_SIZE = 256

        /**
         * Reads the compiled lexicon format: UTF-8 TSV lines of
         * `hanlo<TAB>numeric-tailo<TAB>weight[<TAB>hanlo-poj]`; `#` starts a comment line.
         * Fields are decoded straight from the file's bytes, without per-line strings or lists.
         */
        fun readEntries(input: InputStream): List<LexiconEntry> {
            val bytes = input.readBytes()
            val entries = ArrayList<LexiconEntry>(100_000)
            val fieldStart = IntArray(4)
            val fieldEnd = IntArray(4)
            var lineStart = 0
            while (lineStart < bytes.size) {
                var lineEnd = lineStart
                while (lineEnd < bytes.size && bytes[lineEnd] != NEWLINE) lineEnd++
                var end = lineEnd
                if (end > lineStart && bytes[end - 1] == CARRIAGE_RETURN) end--
                if (end > lineStart && bytes[lineStart] != COMMENT) {
                    var fields = 0
                    var start = lineStart
                    for (i in lineStart..end) {
                        if (i == end || bytes[i] == TAB) {
                            if (fields < 4) {
                                fieldStart[fields] = start
                                fieldEnd[fields] = i
                            }
                            fields++
                            start = i + 1
                        }
                    }
                    if (fields >= 3) {
                        fun text(f: Int) = String(bytes, fieldStart[f], fieldEnd[f] - fieldStart[f], Charsets.UTF_8)
                        val poj = if (fields >= 4 && fieldEnd[3] > fieldStart[3]) text(3) else null
                        entries += LexiconEntry(text(0), poj, text(1), parseWeight(bytes, fieldStart[2], fieldEnd[2]))
                    }
                }
                lineStart = lineEnd + 1
            }
            return entries
        }

        private const val NEWLINE = '\n'.code.toByte()
        private const val CARRIAGE_RETURN = '\r'.code.toByte()
        private const val TAB = '\t'.code.toByte()
        private const val COMMENT = '#'.code.toByte()

        /** A non-negative decimal weight, or 1 if the field isn't one. */
        private fun parseWeight(bytes: ByteArray, start: Int, end: Int): Int {
            if (start == end) return 1
            var value = 0
            for (i in start until end) {
                val digit = bytes[i] - '0'.code.toByte()
                if (digit !in 0..9 || value > (Int.MAX_VALUE - digit) / 10) return 1
                value = value * 10 + digit
            }
            return value
        }

        fun writeEntries(entries: List<LexiconEntry>, out: Writer) {
            for (e in entries) {
                out.append(e.hanlo).append('\t').append(e.romanization).append('\t').append(e.weight.toString())
                if (e.hanloPoj != null) out.append('\t').append(e.hanloPoj)
                out.append('\n')
            }
        }

        /**
         * Fast path for compiled numeric romanization: canonical bases without tones or case.
         * The compiled lexicon is already canonical, so this avoids full parsing at load time.
         */
        internal fun baseSequence(numeric: String): List<String>? {
            val bases = ArrayList<String>(4)
            val sb = StringBuilder()
            fun flush() {
                if (sb.isNotEmpty()) bases += sb.toString()
                sb.setLength(0)
            }
            for (c in numeric) {
                when (c) {
                    in 'a'..'z' -> sb.append(c)
                    in 'A'..'Z' -> sb.append(c.lowercaseChar())
                    in '0'..'9' -> Unit
                    '-', ' ' -> flush()
                    else -> return null
                }
            }
            flush()
            return bases.ifEmpty { null }
        }

        private fun lowerBound(array: Array<String>, key: String): Int {
            var lo = 0
            var hi = array.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (array[mid] < key) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
