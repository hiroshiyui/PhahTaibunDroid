package org.ghostsinthelab.app.phahtaibun.core.engine

import org.ghostsinthelab.app.phahtaibun.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibun.core.romanization.Boundary
import org.ghostsinthelab.app.phahtaibun.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibun.core.romanization.Syllable

/**
 * One syllable-sized piece of the raw input buffer.
 *
 * @property rawStart Offset of the piece in the raw buffer.
 * @property rawEnd Offset just past the piece, including a trailing tone digit and separators.
 * @property base Canonical Tâi-lô base (meaningful only if [kind] is not [Kind.UNKNOWN]).
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
) {
    enum class Kind {
        /** A syllable known to the lexicon. */
        COMPLETE,

        /** The syllable still being typed at the end of the input; a prefix of known syllables. */
        PARTIAL,

        /** Letters that could not be segmented into known syllables. */
        UNKNOWN,
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
 * `ta|i|gi`).
 */
class Segmenter(private val lexicon: Lexicon) {

    private data class Run(val text: String, val start: Int, val end: Int, val tone: Int, val boundary: Boundary)

    fun segment(raw: String, maxResults: Int = MAX_SEGMENTATIONS): List<List<InputSyllable>> {
        val runs = splitRuns(raw)
        if (runs.isEmpty()) return emptyList()
        var combined: List<List<InputSyllable>> = listOf(emptyList())
        for ((index, run) in runs.withIndex()) {
            val isFinal = index == runs.lastIndex && run.end == raw.length && run.tone == Syllable.TONE_UNSPECIFIED &&
                !raw.endsWith('-') && !raw.endsWith('\'')
            val options = segmentRun(run, allowPartial = isFinal)
            combined = combined.flatMap { prefix -> options.map { prefix + it } }
                .sortedWith(compareBy({ s -> s.count { it.kind == InputSyllable.Kind.UNKNOWN } }, { it.size }, ::awkwardJoins))
                .take(maxResults)
        }
        return combined
    }

    private fun splitRuns(raw: String): List<Run> {
        val runs = ArrayList<Run>()
        var i = 0
        var boundary = Boundary.HYPHEN
        while (i < raw.length) {
            val c = raw[i]
            if (!c.isRunChar()) {
                if (c == '-' && raw.startsWith("--", i)) {
                    boundary = Boundary.NEUTRAL
                    i += 2
                } else {
                    // Stray digits/separators: absorb into the previous run so offsets stay contiguous.
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
            runs += Run(text, start, i, tone, boundary)
            boundary = Boundary.HYPHEN
        }
        return runs
    }

    private fun segmentRun(run: Run, allowPartial: Boolean): List<List<InputSyllable>> {
        val t = run.text.lowercase()
        val n = t.length
        // Precompute every valid piece [i, j) and whether position i can reach the end.
        val pieces = Array(n) { ArrayList<Pair<Int, InputSyllable.Kind>>() }
        val reachable = BooleanArray(n + 1).also { it[n] = true }
        for (i in n - 1 downTo 0) {
            for (j in minOf(n, i + MAX_SYLLABLE_LENGTH) downTo i + 1) {
                if (!reachable[j]) continue
                val kind = classify(t.substring(i, j), partialAllowed = allowPartial && j == n) ?: continue
                pieces[i] += j to kind
                reachable[i] = true
            }
        }
        if (!reachable[0]) return listOf(listOf(piece(run, 0, n, InputSyllable.Kind.UNKNOWN)))

        val results = ArrayList<List<InputSyllable>>()
        fun dfs(i: Int, acc: MutableList<InputSyllable>) {
            if (results.size >= MAX_SEGMENTATIONS) return
            if (i == n) {
                results += acc.toList()
                return
            }
            for ((j, kind) in pieces[i]) {
                acc += piece(run, i, j, kind)
                dfs(j, acc)
                acc.removeAt(acc.lastIndex)
            }
        }
        dfs(0, ArrayList())
        return results.sortedWith(
            compareBy({ it.size }, ::awkwardJoins, { s -> s.count { it.kind == InputSyllable.Kind.PARTIAL } })
        )
    }

    /** Returns how [s] can be used as a syllable, or `null` if it cannot. */
    private fun classify(s: String, partialAllowed: Boolean): InputSyllable.Kind? {
        val parsed = Romanization.parseSyllable(s)
        if (parsed != null && lexicon.isSyllable(lexicon.loosen(parsed.base))) return InputSyllable.Kind.COMPLETE
        if (partialAllowed && partialVariants(s).any { lexicon.hasSyllablePrefix(lexicon.loosen(it)) }) {
            return InputSyllable.Kind.PARTIAL
        }
        return null
    }

    private fun piece(run: Run, i: Int, j: Int, kind: InputSyllable.Kind): InputSyllable {
        val raw = run.text.substring(i, j)
        val isLast = j == run.text.length
        val parsed = Romanization.parseSyllable(raw)
        val base = when (kind) {
            InputSyllable.Kind.COMPLETE -> parsed!!.base
            InputSyllable.Kind.PARTIAL -> partialVariants(raw.lowercase())
                .firstOrNull { lexicon.hasSyllablePrefix(lexicon.loosen(it)) } ?: raw.lowercase()
            InputSyllable.Kind.UNKNOWN -> raw.lowercase()
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
        )
    }

    companion object {
        const val MAX_SEGMENTATIONS = 16

        private const val VOWELS = "aeiou"

        /**
         * Number of vowel-initial syllables right after a consonant-final one. Of equally long
         * segmentations, Taigi spelling prefers `li-ho-kam-sia` over `lih-ok-am-sia`.
         */
        internal fun awkwardJoins(syllables: List<InputSyllable>): Int =
            syllables.zipWithNext().count { (a, b) ->
                a.raw.last().lowercaseChar() !in VOWELS && b.raw.first().lowercaseChar() in VOWELS
            }

        /** Letters, plus combining marks so pre-marked input like `lí hó` or `o͘` stays in one run. */
        private fun Char.isRunChar() = isLetter() || Character.getType(this) == Character.NON_SPACING_MARK.toInt()
        private const val MAX_SYLLABLE_LENGTH = 8

        /**
         * Canonical spellings a partially typed syllable might be heading towards. POJ spellings
         * only become recognizable once complete (`che` may become `cheng` → `tsing`), so offer
         * the Tâi-lô forms of such endings too.
         */
        internal fun partialVariants(s: String): List<String> {
            val p = if (s == "c") "ts" else Romanization.canonicalBase(s)
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
