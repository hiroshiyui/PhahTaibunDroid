package org.ghostsinthelab.app.phahtaibundroid.core.romanization

import java.text.Normalizer

enum class RomanSystem { POJ, TAILO }

/**
 * Parsing and rendering of POJ (白話字) and Tâi-lô (台羅) romanization.
 *
 * Parsing accepts either system, with tones given as Unicode diacritics (`Tâi-gí`, `Tâi-gí`) or
 * trailing digits (`tai5-gi2`), and ASCII stand-ins for special letters (`oo`/`ou` for `o͘`,
 * `nn` for `ⁿ`). POJ `ṳ`/`o̤` are read as Tâi-lô `ir`/`er`. Everything is normalized to Tâi-lô
 * [Syllable.base]s.
 */
object Romanization {
    private const val DOT_ABOVE_RIGHT = '͘' // POJ o͘
    private const val SUPERSCRIPT_N = 'ⁿ'
    private const val DIAERESIS_BELOW = '̤' // POJ ṳ (Tâi-lô ir) and o̤ (Tâi-lô er)

    private val MARK_TO_TONE = mapOf(
        '́' to 2, // acute
        '̀' to 3, // grave
        '̂' to 5, // circumflex
        '̌' to 6, // caron
        '̄' to 7, // macron
        '̍' to 8, // vertical line above
        '̋' to 9, // double acute (Tâi-lô)
        '̆' to 9, // breve (POJ)
    )
    private val TAILO_TONE_MARKS = mapOf(
        2 to '́', 3 to '̀', 5 to '̂', 6 to '̌', 7 to '̄', 8 to '̍', 9 to '̋',
    )
    private val POJ_TONE_MARKS = TAILO_TONE_MARKS + (9 to '̆')

    /** Characters silently dropped from dictionary text (zero-width space, BOM, middle dot). */
    private const val IGNORED = "​﻿·"

    /**
     * Loose phonotactic check on a canonical base. The lexicon's syllable inventory is the real
     * authority; this only rejects garbage when compiling dictionaries.
     */
    private val VALID_BASE = Regex(
        "^(?:tsh|ts|ph|th|kh|ng|[pmbtnlkghsj])?" +
            "(?:(?:[aeiou]|ir|er){1,3}(?:nn)?(?:ng|m|n|p|t|k|h)?|m|ng)h?$"
    )

    fun isValidBase(base: String): Boolean = VALID_BASE.matches(base)

    /**
     * Converts a lowercase ASCII spelling in either system into a canonical Tâi-lô base.
     * Assumes `o͘` has already been turned into `oo` and `ⁿ` into `nn`.
     */
    fun canonicalBase(spelling: String): String {
        var b = spelling.replace("ou", "oo")
        b = when {
            b.startsWith("chh") -> "tsh" + b.substring(3)
            b.startsWith("ch") -> "ts" + b.substring(2)
            else -> b
        }
        b = b.replace("oa", "ua").replace("oe", "ue")
        b = when {
            b.endsWith("eng") -> b.dropLast(3) + "ing"
            b.endsWith("ek") -> b.dropLast(2) + "ik"
            else -> b
        }
        // POJ always writes ⁿ before a final h; tolerate the reverse order in typed input.
        if (b.endsWith("hnn")) b = b.dropLast(3) + "nnh"
        // Nasal o has no o/o͘ contrast; Tâi-lô writes it `onn` (POJ `oⁿ`), so `o͘ⁿ`/`oonn` is the same.
        return b.replace("oonn", "onn")
    }

    /**
     * Rewrites a typed syllable's Mandarin-pinyin initial as the Taigi one it usually stands for:
     * `d` → `t`, `z`/`zh` → `ts`, `c`/`q` → `tsh`, `x` → `s`. None of these letters occur in POJ or
     * Tâi-lô (POJ `ch` is left alone), so the rewrite never turns valid input into something else.
     * Applied to typed input only, never to dictionary data.
     */
    fun respellPinyinInitial(text: String): String {
        if (text.isEmpty()) return text
        val lower = text.lowercase()
        val (length, replacement) = when {
            lower.startsWith("zh") -> 2 to "ts"
            lower[0] == 'z' -> 1 to "ts"
            lower[0] == 'c' && !lower.startsWith("ch") -> 1 to "tsh"
            lower[0] == 'q' -> 1 to "tsh"
            lower[0] == 'x' -> 1 to "s"
            lower[0] == 'd' -> 1 to "t"
            else -> return text
        }
        val initial = if (text[0].isUpperCase()) replacement.replaceFirstChar { it.uppercaseChar() } else replacement
        return initial + text.substring(length)
    }

    /**
     * Parses one syllable such as `Tâi`, `tai5`, `chhiⁿ`, `tshinn7` or `o͘`. Returns `null` if the
     * text contains anything other than letters, tone marks and a single trailing tone digit.
     * The syllable's tone is [Syllable.TONE_UNSPECIFIED] if no tone was written.
     */
    fun parseSyllable(text: String, boundary: Boundary = Boundary.HYPHEN): Syllable? {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val letters = StringBuilder()
        var tone = Syllable.TONE_UNSPECIFIED
        var capitalized = false
        for ((i, c) in decomposed.withIndex()) {
            when {
                c in IGNORED -> Unit
                c == DOT_ABOVE_RIGHT -> letters.append('o')
                c == SUPERSCRIPT_N -> letters.append("nn")
                c == DIAERESIS_BELOW -> {
                    val vowel = when (letters.lastOrNull()) {
                        'u' -> 'i'
                        'o' -> 'e'
                        else -> return null
                    }
                    letters.setCharAt(letters.lastIndex, vowel)
                    letters.append('r')
                }
                c in MARK_TO_TONE -> tone = MARK_TO_TONE.getValue(c)
                c == 'ı' -> letters.append('i')
                c in 'a'..'z' -> letters.append(c)
                c in 'A'..'Z' -> {
                    if (letters.isEmpty()) capitalized = true
                    letters.append(c.lowercaseChar())
                }
                c in '0'..'9' && i == decomposed.lastIndex && letters.isNotEmpty() -> {
                    val digit = c - '0'
                    if (digit != 0) tone = digit
                }
                else -> return null
            }
        }
        if (letters.isEmpty()) return null
        return Syllable(canonicalBase(letters.toString()), tone, boundary, capitalized)
    }

    /**
     * Parses a word or phrase such as `Tâi-gí`, `tai5-gi2`, `am-gōng--ê` or `ài tshing-khì`.
     * Returns `null` if any syllable is unparseable or not phonotactically valid.
     */
    fun parseWord(text: String): List<Syllable>? {
        val syllables = mutableListOf<Syllable>()
        var boundary = Boundary.HYPHEN
        var i = 0
        val s = text.trim().replace('‑', '-').replace('‐', '-')
        if (s.isEmpty()) return null
        while (i < s.length) {
            val c = s[i]
            when {
                c == '-' -> {
                    boundary = if (s.startsWith("--", i)) Boundary.NEUTRAL else Boundary.HYPHEN
                    i += if (boundary == Boundary.NEUTRAL) 2 else 1
                }
                c.isWhitespace() -> {
                    if (boundary != Boundary.NEUTRAL) boundary = Boundary.SPACE
                    i++
                }
                else -> {
                    var end = i
                    while (end < s.length && s[end] != '-' && !s[end].isWhitespace()) end++
                    val syllable = parseSyllable(s.substring(i, end), boundary) ?: return null
                    if (!isValidBase(syllable.base)) return null
                    syllables += syllable
                    boundary = Boundary.HYPHEN
                    i = end
                }
            }
        }
        return syllables.ifEmpty { null }
    }

    /** Converts a canonical Tâi-lô base into the given system's spelling (without tone). */
    fun spell(base: String, system: RomanSystem): String {
        if (system == RomanSystem.TAILO) return base
        var b = when {
            base.startsWith("tsh") -> "chh" + base.substring(3)
            base.startsWith("ts") -> "ch" + base.substring(2)
            else -> base
        }
        b = when {
            b.endsWith("ing") -> b.dropLast(3) + "eng"
            b.endsWith("ik") -> b.dropLast(2) + "ek"
            else -> b
        }
        b = b.replace("ua", "oa").replace("ue", "oe")
        b = b.replace("oo", "o$DOT_ABOVE_RIGHT")
        // Nasalization: `nn` directly after a vowel. (The `nn` of e.g. `nng` is an initial + rime.)
        b = b.replace(Regex("(?<=[aeiou͘])nn"), SUPERSCRIPT_N.toString())
        return b
    }

    /** Renders one syllable with its tone mark in the given system. */
    fun render(syllable: Syllable, system: RomanSystem): String {
        val spelled = spell(syllable.base, system)
        val marks = if (system == RomanSystem.POJ) POJ_TONE_MARKS else TAILO_TONE_MARKS
        val mark = marks[syllable.tone]
        val withTone = if (mark == null) spelled else {
            val at = toneMarkIndex(spelled, system)
            spelled.substring(0, at + 1) + mark + spelled.substring(at + 1)
        }
        val cased = if (syllable.capitalized) withTone.replaceFirstChar { it.uppercaseChar() } else withTone
        return Normalizer.normalize(cased, Normalizer.Form.NFC)
    }

    /** Renders a word, joining syllables with their [Boundary] separators. */
    fun render(syllables: List<Syllable>, system: RomanSystem): String = buildString {
        syllables.forEachIndexed { i, s ->
            if (i > 0) append(s.boundary.text)
            append(render(s, system))
        }
    }

    /** Index of the letter that carries the tone mark in a spelled (system-specific) syllable. */
    internal fun toneMarkIndex(spelled: String, system: RomanSystem): Int {
        fun idx(c: Char) = spelled.indexOf(c).takeIf { it >= 0 }
        if (system == RomanSystem.POJ) {
            // Traditional POJ: open `oa`/`oe` (optionally nasal) mark the o; otherwise the a/e.
            Regex("o[ae]").find(spelled)?.let { m ->
                val after = spelled.substring(m.range.last + 1)
                return if (after.isEmpty() || after == "ⁿ") m.range.first else m.range.last
            }
            return idx('a') ?: idx('o') ?: idx('e') ?: idx('u') ?: idx('i')
                ?: syllabicNasalIndex(spelled)
        }
        idx('a')?.let { return it }
        spelled.indexOf("oo").takeIf { it >= 0 }?.let { return it }
        // The ere diphthong marks its second e (MOE Tâi-lô handbook: erê 鞋).
        spelled.indexOf("ere").takeIf { it >= 0 }?.let { return it + 2 }
        idx('e')?.let { return it }
        idx('o')?.let { return it }
        val i = spelled.indexOf('i')
        val u = spelled.indexOf('u')
        if (i >= 0 || u >= 0) return maxOf(i, u) // iu → u, ui → i
        return syllabicNasalIndex(spelled)
    }

    /** `ng` carries the mark on its n; `m` on the m. */
    private fun syllabicNasalIndex(spelled: String): Int {
        val ng = spelled.lastIndexOf("ng")
        if (ng >= 0) return ng
        return spelled.lastIndexOf('m').coerceAtLeast(0)
    }
}
