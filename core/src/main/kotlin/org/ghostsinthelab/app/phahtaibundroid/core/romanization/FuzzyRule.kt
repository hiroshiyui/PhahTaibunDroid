package org.ghostsinthelab.app.phahtaibundroid.core.romanization

/**
 * Optional spelling merges for fuzzy matching. Each rule collapses distinctions in a canonical
 * base; matching happens on the collapsed ("loose") key, so input and dictionary forms that
 * collapse to the same key match. Exact (non-collapsed) matches still rank higher.
 *
 * POJ ⇄ Tâi-lô spelling differences are *not* fuzzy rules: they are always normalized away by
 * [Romanization.canonicalBase].
 */
enum class FuzzyRule(val defaultEnabled: Boolean) {
    /** `o` ⇄ `oo` (o͘): plain ASCII typists often omit the dot. */
    O_OO(true),

    /** Nasalization optional: `a` ⇄ `ann`. */
    NASAL(true),

    /** Final glottal stop optional: `a` ⇄ `ah`. */
    GLOTTAL_STOP(false),

    /**
     * Entering-tone (入聲) codas optional or mixed up: `ta` ⇄ `tap` ⇄ `tat` ⇄ `tak` ⇄ `tah`. Short
     * stops are easy to miss or mishear (`dageho` for `ta̍k-ke-hó`). Includes [GLOTTAL_STOP].
     */
    ENTERING_TONE(false),

    /** Nasal codas `-m` ⇄ `-n` (`kam` ⇄ `kan`). Syllabic `m` (`m̄`, `hm`) is unaffected. */
    CODA_M_N(false),

    /** Nasal codas `-n` ⇄ `-ng` (`sin` ⇄ `sing`), a common merger in Taiwan Mandarin. */
    CODA_N_NG(false),

    /** Aspiration ignored: `p`/`ph`, `t`/`th`, `k`/`kh`, `ts`/`tsh`. */
    ASPIRATION(false),

    /**
     * Voiced and plain initials merged: `b`/`p`, `g`/`k`. Mandarin pinyin writes Taigi's plain
     * `p` and `k` as `b` and `g`.
     */
    VOICING(false),

    /** Dialectal `ue` ⇄ `e` (kue/ke 過). */
    UE_E(false),

    /** Dialectal `i` ⇄ `u` / `ir` (gí/gú 語). */
    I_U(false),

    /** Dialectal `inn` ⇄ `enn` (tshinn/tshenn 生). */
    INN_ENN(false),

    /** Dialectal `j` ⇄ `l` (jit/lit 日). */
    J_L(false),
    ;

    /** Applies this rule to a canonical base. */
    fun apply(base: String): String = when (this) {
        O_OO -> base.replace("oo", "o")
        NASAL -> base.replace(NASAL_NN, "")
        GLOTTAL_STOP -> if (base.length > 1 && base.endsWith('h')) base.dropLast(1) else base
        ENTERING_TONE -> if (base.length > 1 && base.last() in "ptkh") base.dropLast(1) else base
        CODA_M_N -> base.replace(CODA_M, "n")
        CODA_N_NG -> base.replace(CODA_NG, "n")
        ASPIRATION -> ASPIRATED.find(base)?.let { base.removeRange(it.range.last, it.range.last + 1) } ?: base
        VOICING -> when (base.firstOrNull()) {
            'b' -> "p" + base.substring(1)
            'g' -> "k" + base.substring(1)
            else -> base
        }
        UE_E -> base.replace("ue", "e")
        I_U -> I_U_RIME.matchEntire(base)?.let { it.groupValues[1] + "i" } ?: base
        INN_ENN -> base.replace("inn", "enn")
        J_L -> if (base.startsWith('j')) "l" + base.substring(1) else base
    }

    companion object {
        val DEFAULTS: Set<FuzzyRule> = entries.filter { it.defaultEnabled }.toSet()

        private val NASAL_NN = Regex("(?<=[aeiou])nn")
        private val CODA_M = Regex("(?<=[aeiou])m$")
        private val CODA_NG = Regex("(?<=[aeiou])ng$")
        private val ASPIRATED = Regex("^(?:ph|th|kh|tsh)")
        private val I_U_RIME = Regex("^(tsh|ts|ph|th|kh|ng|[pmbtnlkghsj]?)(?:ir|u)$")

        /** Collapses a canonical base under the given rules, in declaration order. */
        fun loosen(base: String, rules: Set<FuzzyRule>): String =
            entries.fold(base) { b, rule -> if (rule in rules) rule.apply(b) else b }
    }
}
