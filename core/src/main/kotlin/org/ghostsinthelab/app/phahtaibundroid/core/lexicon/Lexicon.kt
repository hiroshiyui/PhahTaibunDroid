package org.ghostsinthelab.app.phahtaibundroid.core.lexicon

import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Syllable
import java.io.BufferedReader
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
 */
class Lexicon(
    val entries: List<LexiconEntry>,
    val rules: Set<FuzzyRule> = FuzzyRule.DEFAULTS,
) {
    private val byKey = HashMap<String, MutableList<LexiconEntry>>(entries.size)
    private val sortedKeys: Array<String>
    private val syllableSet = HashSet<String>()
    private val sortedSyllables: Array<String>

    /** Canonical syllable bases and their loose forms, for wildcard matching. */
    private val canonicalToLoose: Map<String, String>

    /** Keys grouped by the first letters of their syllables (`tai gi` → `tg`), most common first. */
    private val byInitials = HashMap<String, Array<String>>()
    private val wildcardCache = ConcurrentHashMap<String, Set<String>>()

    init {
        val looseCache = HashMap<String, String>()
        for (entry in entries) {
            val bases = baseSequence(entry.romanization) ?: continue
            val loose = bases.map { b -> looseCache.getOrPut(b) { FuzzyRule.loosen(b, rules) } }
            syllableSet.addAll(loose)
            byKey.getOrPut(loose.joinToString(KEY_SEPARATOR)) { ArrayList(1) } += entry
        }
        for (list in byKey.values) list.sortByDescending { it.weight }
        sortedKeys = byKey.keys.toTypedArray().also { it.sort() }
        sortedSyllables = syllableSet.toTypedArray().also { it.sort() }
        canonicalToLoose = looseCache

        // Bucket keys by top weight (weights are small integers), most common first, then group.
        val byWeight = sortedKeys.groupBy { byKey.getValue(it)[0].weight }
        val grouped = HashMap<String, ArrayList<String>>()
        val initials = StringBuilder()
        for (weight in byWeight.keys.sortedDescending()) {
            for (key in byWeight.getValue(weight)) {
                initials.setLength(0)
                initials.append(key[0])
                for (i in 1 until key.length) if (key[i - 1] == ' ') initials.append(key[i])
                grouped.getOrPut(initials.toString()) { ArrayList() } += key
            }
        }
        for ((k, keys) in grouped) byInitials[k] = keys.toTypedArray()
    }

    fun withRules(rules: Set<FuzzyRule>): Lexicon = if (rules == this.rules) this else Lexicon(entries, rules)

    fun loosen(base: String): String = FuzzyRule.loosen(base, rules)

    /** Whether [looseBase] is a syllable occurring in the lexicon. */
    fun isSyllable(looseBase: String): Boolean = looseBase in syllableSet

    /** Whether some syllable in the lexicon starts with [loosePrefix]. */
    fun hasSyllablePrefix(loosePrefix: String): Boolean {
        val i = lowerBound(sortedSyllables, loosePrefix)
        return i < sortedSyllables.size && sortedSyllables[i].startsWith(loosePrefix)
    }

    /** Entries whose loose syllable sequence equals [looseBases], most common first. */
    fun lookup(looseBases: List<String>): List<LexiconEntry> =
        byKey[looseBases.joinToString(KEY_SEPARATOR)].orEmpty()

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
            val key = sortedKeys[i++]
            if (!key.startsWith(prefix)) break
            scanned++
            // Prefer completions with exactly as many syllables as typed.
            if (key.count { it == ' ' } + 1 != wanted) continue
            found += byKey.getValue(key)
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
            for (key in keys) {
                if (matched >= limit || scanned >= MATCH_SCAN_LIMIT) break
                scanned++
                val syllables = key.split(KEY_SEPARATOR)
                if (patterns.indices.all { patterns[it].matches(syllables[it]) }) {
                    found += byKey.getValue(key)
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
         */
        fun readEntries(input: InputStream): List<LexiconEntry> =
            input.bufferedReader(Charsets.UTF_8).use(::readEntries)

        fun readEntries(reader: BufferedReader): List<LexiconEntry> {
            val entries = ArrayList<LexiconEntry>(100_000)
            reader.forEachLine { line ->
                if (line.isEmpty() || line[0] == '#') return@forEachLine
                val f = line.split('\t')
                if (f.size < 3) return@forEachLine
                entries += LexiconEntry(f[0], f.getOrNull(3)?.ifEmpty { null }, f[1], f[2].toIntOrNull() ?: 1)
            }
            return entries
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
