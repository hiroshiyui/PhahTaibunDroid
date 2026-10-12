package org.ghostsinthelab.app.phahtaibundroid.core.engine

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.SyllablePattern
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import java.text.Normalizer

/**
 * Turns non-literal typed syllables into [SyllablePattern]s:
 *
 * - **Abbreviations** ([InputSyllable.Kind.INITIAL]): one letter per syllable standing for its
 *   initial, by the initial's first letter: `t` = t/th/ts/tsh, `k` = k/kh, `p` = p/ph,
 *   `n` = n/ng, and so on, so `tg` → Tâi-gí. POJ `ch`/`chh` and pinyin `z`/`c`/`q` are typed
 *   `c`, `z` or `q` (ts/tsh only); pinyin `d` and `x` mean `t` and `s`. The letter may be
 *   followed by the syllable's coda (`m n ng p t k h`), so `kms` → kám-siā.
 * - **Wildcards** ([InputSyllable.Kind.WILDCARD]): `?` stands for any run of letters, possibly
 *   none, within one syllable: `s?ah` → siah/suah/sah…, a lone `?` → any syllable.
 */
object SyllablePatterns {
    const val WILDCARD = '?'

    private const val INITIAL_LETTERS = "pbmtnlkghsjczqdx"
    private val CODAS = listOf("ng", "m", "n", "p", "t", "k", "h")
    private val INITIAL = Regex("^(?:tsh|ts|ph|th|kh|ng|[pmbtnlkghsj])")

    /**
     * Whether abbreviation [typed] names the initial of canonical [base] literally (`t` for `tai`
     * but not for `tshai`), the likelier reading when many words match.
     */
    fun isLiteralInitial(typed: String, base: String): Boolean =
        INITIAL.find(base)?.value == typed.take(1).lowercase()

    /** Pattern of a [InputSyllable.Kind.INITIAL] piece such as `t`, `c` or `km`, or `null`. */
    fun abbreviation(lexicon: Lexicon, text: String): SyllablePattern? {
        val s = text.lowercase()
        val letter = s.firstOrNull()?.takeIf { it in INITIAL_LETTERS } ?: return null
        val coda = s.substring(1)
        if (coda.isNotEmpty() && coda !in CODAS) return null
        val required = when (letter) {
            'c', 'z', 'q' -> "ts"
            'd' -> "t"
            'x' -> "s"
            else -> letter.toString()
        }
        // Loosen with a dummy rime so initial-merging rules (b → p, j → l…) apply to the letter.
        val prefix = lexicon.loosen(required + "a").dropLast(1)
        val looseCoda = if (coda.isEmpty()) "" else lexicon.loosen("a$coda").substring(1)
        return SyllablePattern(prefix.take(1)) { it.startsWith(prefix) && it.endsWith(looseCoda) }
    }

    /** Pattern of a [InputSyllable.Kind.WILDCARD] piece such as `s?ah`, or `null` if nothing matches. */
    fun wildcard(lexicon: Lexicon, text: String): SyllablePattern? {
        val matching = lexicon.syllablesMatching(globRegex(text))
        if (matching.isEmpty()) return null
        val initials = matching.mapTo(sortedSetOf()) { it[0] }.joinToString("")
        return SyllablePattern(initials) { it in matching }
    }

    /** The pattern a typed syllable stands for, for [Lexicon.lookupMatching]. */
    fun of(lexicon: Lexicon, syllable: InputSyllable): SyllablePattern? = when (syllable.kind) {
        InputSyllable.Kind.COMPLETE -> syllable.loose.let { loose -> SyllablePattern(loose.take(1)) { it == loose } }
        InputSyllable.Kind.PARTIAL -> syllable.loose.let { loose -> SyllablePattern(loose.take(1)) { it.startsWith(loose) } }
        InputSyllable.Kind.INITIAL -> abbreviation(lexicon, syllable.raw)
        InputSyllable.Kind.WILDCARD -> wildcard(lexicon, syllable.raw)
        InputSyllable.Kind.UNKNOWN -> null
    }

    /**
     * Regex for a typed glob: its letters are normalized like a typed syllable (POJ `ch`, `oa`,
     * `eng`, `ou`, `ⁿ`… and pinyin initials to Tâi-lô), and each `?` becomes `[a-z]*`.
     */
    internal fun globRegex(text: String): Regex {
        val letters = StringBuilder()
        for (c in Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)) {
            when (c) {
                in 'a'..'z', WILDCARD -> letters.append(c)
                'ⁿ' -> letters.append("nn")
                '͘' -> letters.append('o')
            }
        }
        val canonical = Romanization.canonicalBase(Romanization.respellPinyinInitial(letters.toString()))
        return Regex(canonical.split(WILDCARD).joinToString("[a-z]*") { Regex.escape(it) })
    }
}
