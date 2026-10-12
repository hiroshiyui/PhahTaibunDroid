package org.ghostsinthelab.app.phahtaibundroid.core.engine

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Boundary
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Syllable

/**
 * One syllable-sized piece of the raw input buffer.
 *
 * @property rawStart Offset of the piece in the raw buffer.
 * @property rawEnd Offset just past the piece, including a trailing tone digit and separators.
 * @property base Canonical Tâi-lô base; for [Kind.UNKNOWN], [Kind.INITIAL] and [Kind.WILDCARD], the lowercased raw text.
 * @property loose [base] collapsed under the active fuzzy rules.
 */
data class InputSyllable(
    val raw: String,
    val rawStart: Int,
    val rawEnd: Int,
    val base: String,
    val loose: String,
    val tone: Int,
    val boundary: Boundary,
    val kind: Kind,
    /** The user typed a separator (`-`, `--`, a space…) before this syllable, so [boundary] is theirs. */
    val separated: Boolean = false,
) {
    enum class Kind {
        /** A syllable known to the lexicon. */
        COMPLETE,

        /** The syllable still being typed at the end of the input; a prefix of known syllables. */
        PARTIAL,

        /** Letters that could not be segmented into known syllables. */
        UNKNOWN,

        /** An abbreviated syllable: its initial's first letter, maybe with its coda (see [SyllablePatterns]). */
        INITIAL,

        /** A syllable spelled with `?` for unknown letters (see [SyllablePatterns]). */
        WILDCARD,
    }

    fun toSyllable(): Syllable = Syllable(
        base = base,
        tone = tone,
        boundary = boundary,
        capitalized = raw.firstOrNull()?.isUpperCase() == true,
    )
}

/**
 * Splits raw keystrokes such as `taigi`, `tai5gi2`, `chhiuⁿ` or `am-gong--e` into syllables.
 *
 * Digits are tone numbers and end a syllable; `-` and `'` are explicit separators (`--` marks
 * a neutral tone). Unseparated letter runs are segmented against the lexicon's syllable inventory,
 * yielding several alternative segmentations ranked fewest-syllables first (e.g. `tai|gi` before
 * `ta|i|gi`). `?` is a wildcard within a syllable (`s?ah`).
 *
 * A run that cannot be segmented, or that has no vowels at all (`tg`, `ks`), is also read as
 * abbreviated syllables ([InputSyllable.Kind.INITIAL]), possibly mixed with full ones (`tgi`).
 */
class Segmenter(private val lexicon: Lexicon) {

    private data class Run(val text: String, val start: Int, val end: Int, val tone: Int, val boundary: Boundary, val separated: Boolean)

    fun segment(raw: String, maxResults: Int = MAX_SEGMENTATIONS): List<List<InputSyllable>> {
        val runs = splitRuns(raw)
        if (runs.isEmpty()) return emptyList()
        var combined: List<List<InputSyllable>> = listOf(emptyList())
        for ((index, run) in runs.withIndex()) {
            val isFinal = index == runs.lastIndex && run.end == raw.length && run.tone == Syllable.TONE_UNSPECIFIED &&
                !raw.endsWith('-') && !raw.endsWith('\'')
            val options = segmentRun(run, allowPartial = isFinal)
            combined = combined.flatMap { prefix -> options.map { prefix + it } }
                .sortedWith(
                    compareBy(
                        { s -> s.count { it.kind == InputSyllable.Kind.UNKNOWN } },
                        { s -> s.count { it.kind == InputSyllable.Kind.INITIAL } },
                        { it.size },
                        ::awkwardJoins,
                    )
                )
                .take(maxResults)
        }
        return combined
    }

    private fun splitRuns(raw: String): List<Run> {
        val runs = ArrayList<Run>()
        var i = 0
        var boundary = Boundary.HYPHEN
        var separated = false
        while (i < raw.length) {
            val c = raw[i]
            if (!c.isRunChar()) {
                if (c == '-' && raw.startsWith("--", i)) {
                    boundary = Boundary.NEUTRAL
                    separated = true
                    i += 2
                } else {
                    // Stray digits/separators: absorb into the previous run so offsets stay contiguous.
                    if (!c.isDigit()) separated = true
                    i++
                }
                if (runs.isNotEmpty()) runs[runs.lastIndex] = runs.last().copy(end = i)
                continue
            }
            val start = i
            while (i < raw.length && raw[i].isRunChar()) i++
            var tone = Syllable.TONE_UNSPECIFIED
            val text = raw.substring(start, i)
            if (i < raw.length && raw[i] in '1'..'9') {
                tone = raw[i] - '0'
                i++
            }
            runs += Run(text, start, i, tone, boundary, separated && runs.isNotEmpty())
            boundary = Boundary.HYPHEN
            separated = false
        }
        return runs
    }

    private fun segmentRun(run: Run, allowPartial: Boolean): List<List<InputSyllable>> {
        val t = run.text.lowercase()
        val spelled = segmentRun(run, t, allowPartial, abbreviate = false)
        val abbreviated = if (spelled.isEmpty() || (t.length > 1 && t.all { it in CONSONANTS })) {
            segmentRun(run, t, allowPartial, abbreviate = true)
        } else {
            emptyList()
        }
        // Unsegmentable letters stay available as such, for the romanization candidate.
        val unknown = if (spelled.isEmpty()) listOf(listOf(piece(run, 0, t.length, InputSyllable.Kind.UNKNOWN))) else emptyList()
        return spelled + abbreviated + unknown
    }

    private fun segmentRun(run: Run, t: String, allowPartial: Boolean, abbreviate: Boolean): List<List<InputSyllable>> {
        val n = t.length
        // Precompute every valid piece [i, j) and whether position i can reach the end.
        val pieces = Array(n) { ArrayList<Pair<Int, InputSyllable.Kind>>() }
        val reachable = BooleanArray(n + 1).also { it[n] = true }
        for (i in n - 1 downTo 0) {
            for (j in minOf(n, i + MAX_SYLLABLE_LENGTH) downTo i + 1) {
                if (!reachable[j]) continue
                val s = t.substring(i, j)
                val kind = (if (abbreviate) classifyAbbreviation(s) else null)
                    ?: classify(s, partialAllowed = allowPartial && j == n)
                    ?: (if (!abbreviate && i == 0 && j == n) classifyToned(s, run) else null)
                    ?: continue
                pieces[i] += j to kind
                reachable[i] = true
            }
        }
        if (!reachable[0]) return emptyList()

        // Fewest syllables needed from each position to the end.
        val minRest = IntArray(n + 1) { if (it == n) 0 else Int.MAX_VALUE / 2 }
        for (i in n - 1 downTo 0) for ((j, _) in pieces[i]) minRest[i] = minOf(minRest[i], minRest[j] + 1)

        // Enumerate segmentations of exactly `total` syllables for increasing totals, so that the
        // fewest-syllable ones are all found before the limit is reached in long input.
        val results = ArrayList<List<InputSyllable>>()
        var steps = 0
        fun dfs(i: Int, acc: MutableList<InputSyllable>, total: Int) {
            if (results.size >= MAX_SEGMENTATIONS || ++steps > MAX_SEARCH_STEPS) return
            if (i == n) {
                if (acc.size == total) results += acc.toList()
                return
            }
            for ((j, kind) in pieces[i]) {
                if (acc.size + 1 + minRest[j] > total) continue
                acc += piece(run, i, j, kind)
                dfs(j, acc, total)
                acc.removeAt(acc.lastIndex)
            }
        }
        var total = minRest[0]
        while (results.size < MAX_SEGMENTATIONS && total <= n) dfs(0, ArrayList(), total++)
        return results.sortedWith(
            compareBy(
                { s -> s.count { it.kind == InputSyllable.Kind.INITIAL } },
                { it.size },
                ::awkwardJoins,
                { s -> s.count { it.kind == InputSyllable.Kind.PARTIAL } },
            )
        )
    }

    /** [InputSyllable.Kind.INITIAL] if [s] (consonants only) can abbreviate a syllable. */
    private fun classifyAbbreviation(s: String): InputSyllable.Kind? =
        if (s.all { it in CONSONANTS } && SyllablePatterns.abbreviation(lexicon, s) != null) InputSyllable.Kind.INITIAL else null

    /**
     * A syllable the dictionary doesn't use, accepted when the user delimited it with a tone
     * (`hiunnh8`, `phoenn7`) and spelled it validly: someone who types tones knows the spelling.
     */
    private fun classifyToned(s: String, run: Run): InputSyllable.Kind? {
        val parsed = parseTyped(s) ?: return null
        val toned = run.tone != Syllable.TONE_UNSPECIFIED || parsed.tone != Syllable.TONE_UNSPECIFIED
        return if (toned && Romanization.isValidBase(parsed.base)) InputSyllable.Kind.COMPLETE else null
    }

    /** Returns how [s] can be used as a syllable, or `null` if it cannot. */
    private fun classify(s: String, partialAllowed: Boolean): InputSyllable.Kind? {
        if (SyllablePatterns.WILDCARD in s) {
            return if (SyllablePatterns.wildcard(lexicon, s) != null) InputSyllable.Kind.WILDCARD else null
        }
        val parsed = parseTyped(s)
        if (parsed != null && lexicon.isSyllable(lexicon.loosen(parsed.base))) return InputSyllable.Kind.COMPLETE
        if (partialAllowed && partialVariants(s).any { lexicon.hasSyllablePrefix(lexicon.loosen(it)) }) {
            return InputSyllable.Kind.PARTIAL
        }
        return null
    }

    private fun piece(run: Run, i: Int, j: Int, kind: InputSyllable.Kind): InputSyllable {
        val raw = run.text.substring(i, j)
        val isLast = j == run.text.length
        val parsed = parseTyped(raw)
        val base = when (kind) {
            InputSyllable.Kind.COMPLETE -> parsed!!.base
            InputSyllable.Kind.PARTIAL -> partialVariants(raw.lowercase())
                .firstOrNull { lexicon.hasSyllablePrefix(lexicon.loosen(it)) } ?: raw.lowercase()
            InputSyllable.Kind.UNKNOWN, InputSyllable.Kind.INITIAL, InputSyllable.Kind.WILDCARD -> raw.lowercase()
        }
        return InputSyllable(
            raw = raw,
            rawStart = run.start + i,
            rawEnd = if (isLast) run.end else run.start + j,
            base = base,
            loose = lexicon.loosen(base),
            tone = if (isLast && run.tone != Syllable.TONE_UNSPECIFIED) run.tone else parsed?.tone ?: Syllable.TONE_UNSPECIFIED,
            boundary = if (i == 0) run.boundary else Boundary.HYPHEN,
            kind = kind,
            separated = i == 0 && run.separated,
        )
    }

    companion object {
        const val MAX_SEGMENTATIONS = 16
        private const val MAX_SEARCH_STEPS = 20_000

        /** Parses a typed syllable, accepting Mandarin-pinyin initials such as `d` for `t`. */
        private fun parseTyped(s: String) = Romanization.parseSyllable(Romanization.respellPinyinInitial(s))

        private const val VOWELS = "aeiou"
        private const val CONSONANTS = "bcdfghjklmnpqrstvwxyz"

        /**
         * Number of vowel-initial syllables right after a consonant-final one. Of equally long
         * segmentations, Taigi spelling prefers `li-ho-kam-sia` over `lih-ok-am-sia`.
         */
        internal fun awkwardJoins(syllables: List<InputSyllable>): Int =
            syllables.zipWithNext().count { (a, b) ->
                a.raw.last().lowercaseChar() !in VOWELS && b.raw.first().lowercaseChar() in VOWELS
            }

        /** Letters, plus combining marks so pre-marked input like `lí hó` or `o͘` stays in one run. */
        private fun Char.isRunChar() =
            isLetter() || this == SyllablePatterns.WILDCARD || Character.getType(this) == Character.NON_SPACING_MARK.toInt()
        private const val MAX_SYLLABLE_LENGTH = 8

        /**
         * Canonical spellings a partially typed syllable might be heading towards. POJ spellings
         * only become recognizable once complete (`che` may become `cheng` → `tsing`), so offer
         * the Tâi-lô forms of such endings too.
         */
        internal fun partialVariants(s: String): List<String> {
            // A lone `c` may still become POJ `ch`/`chh` (ts/tsh) rather than pinyin `c` (tsh).
            val p = if (s == "c") "ts" else Romanization.canonicalBase(Romanization.respellPinyinInitial(s))
            val variants = mutableListOf(p)
            when {
                p.endsWith("en") -> variants += p.dropLast(2) + "in" // eng → ing
                p.endsWith("e") -> variants += p.dropLast(1) + "i" // eng/ek → ing/ik
                p.endsWith("o") -> variants += p.dropLast(1) + "u" // oa/oe → ua/ue
            }
            return variants
        }
    }
}
