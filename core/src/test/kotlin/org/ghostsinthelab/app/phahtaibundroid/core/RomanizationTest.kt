package org.ghostsinthelab.app.phahtaibundroid.core

import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Boundary
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem.POJ
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem.TAILO
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Romanization
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.Syllable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomanizationTest {
    private fun numeric(text: String) = Romanization.parseWord(text)!!.joinToString("-") { it.toNumeric() }

    @Test
    fun parsesBothSystemsToCanonicalTailo() {
        assertEquals("Tai5-gi2", numeric("Tâi-gí"))
        assertEquals("tshiunn7", numeric("chhiūⁿ"))
        assertEquals("tshiunn7", numeric("tshiūnn"))
        assertEquals("hue", numeric("hoe")) // unmarked: tone unspecified
        assertEquals("sing", numeric("seng"))
        assertEquals("tik8", numeric("te̍k"))
        assertEquals("oo", numeric("o͘"))
        assertEquals("boo7", numeric("bō͘"))
        assertEquals("uainnh", numeric("oaiⁿh"))
        assertEquals("tsua7", numeric("chōa"))
    }

    @Test
    fun parsesNumericInput() {
        assertEquals("tai5-gi2", numeric("tai5-gi2"))
        assertEquals("tshinn1", numeric("chhinn1"))
        assertEquals("oo2", numeric("ou2"))
    }

    @Test
    fun keepsBoundaries() {
        val word = Romanization.parseWord("am-gōng--ê")!!
        assertEquals(listOf(Boundary.HYPHEN, Boundary.HYPHEN, Boundary.NEUTRAL), word.map { it.boundary })
        assertEquals(Boundary.SPACE, Romanization.parseWord("ài tshing-khì")!![1].boundary)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Romanization.parseWord("揹"))
        assertNull(Romanization.parseWord("ka(lé)"))
        assertNull(Romanization.parseWord("xyz"))
    }

    @Test
    fun rendersTailoToneMarks() {
        assertEquals("Tâi-gí", render("Tai5-gi2", TAILO))
        assertEquals("tsuí", render("tsui2", TAILO))
        assertEquals("tshiú", render("tshiu2", TAILO))
        assertEquals("kuè", render("kue3", TAILO))
        assertEquals("sóo", render("soo2", TAILO))
        assertEquals("ǹg", render("ng3", TAILO))
        assertEquals("m̄", render("m7", TAILO))
        assertEquals("tsia̍h", render("tsiah8", TAILO))
        assertEquals("am-gōng--ê", render("am-gong7--e5", TAILO))
    }

    @Test
    fun rendersPojSpellingAndToneMarks() {
        assertEquals("Tâi-gí", render("Tai5-gi2", POJ))
        assertEquals("chúi", render("tsui2", POJ))
        assertEquals("chhiú", render("tshiu2", POJ))
        assertEquals("hôa", render("hua5", POJ))
        assertEquals("hōe", render("hue7", POJ))
        assertEquals("koân", render("kuan5", POJ))
        assertEquals("koe̍h", render("kueh8", POJ))
        assertEquals("koāi", render("kuai7", POJ))
        assertEquals("só͘", render("soo2", POJ))
        assertEquals("chhiūⁿ", render("tshiunn7", POJ))
        assertEquals("seng", render("sing1", POJ))
        assertEquals("te̍k", render("tik8", POJ))
        assertEquals("nn̄g", render("nng7", POJ))
        assertEquals("hiaⁿh", render("hiannh4", POJ))
    }

    @Test
    fun roundTripsBetweenSystems() {
        for (word in listOf("Tâi-gí", "chhiūⁿ", "koe̍h", "só͘", "seng-lí", "hôa", "kóng-ōe")) {
            assertEquals(word, Romanization.render(Romanization.parseWord(word)!!, POJ))
        }
    }

    /** Cases where Romanization disagreed with the KeSi converter, with the convention we follow. */
    @Test
    fun kesiCrossCheck() {
        // numeric input → Tâi-lô, POJ
        val rendered = listOf(
            // Tâi-lô `ere` marks the second e (MOE handbook: erê 鞋); KeSi agrees, we used to mark the first.
            // POJ writes ir/er/ere as ṳ/o̤/o̤e (iTaigi's POJ), marking o̤ in an open o̤e; KeSi keeps êre.
            Triple("ere5", "erê", "ô̤e"),
            Triple("kere1-nng6", "kere-nňg", "ko̤e-nňg"),
            Triple("tsere8", "tsere̍", "cho̤̍e"),
            Triple("sere3", "serè", "sò̤e"),
            Triple("sir2", "sír", "sṳ́"),
            Triple("sirh8", "si̍rh", "sṳ̍h"),
            Triple("ker1", "ker", "ko̤"),
            Triple("kher2", "khér", "khó̤"),
            // POJ marks the a of `au` even before a final h (台華線頂對照典 tha̍uh); KeSi writes thau̍h.
            Triple("thauh8", "tha̍uh", "tha̍uh"),
            Triple("haunnh8", "ha̍unnh", "ha̍uⁿh"),
            // POJ `oa`/`oe` mark the o only when open (optionally nasal); KeSi agrees.
            Triple("khuannh8", "khua̍nnh", "khoa̍ⁿh"),
            Triple("kueh8", "kue̍h", "koe̍h"),
            Triple("uainnh8", "ua̍innh", "oa̍iⁿh"),
            // o͘ with a tone: the mark goes on the o, before U+0358 (NFC order).
            Triple("ooh8", "o̍oh", "o̍͘h"),
            // Tone 9: double acute in Tâi-lô, breve in POJ.
            Triple("kang9", "ka̋ng", "kăng"),
        )
        for ((numeric, tailo, poj) in rendered) {
            assertEquals(numeric, tailo, render(numeric, TAILO))
            assertEquals(numeric, poj, render(numeric, POJ))
        }
        // typed or dictionary spelling → canonical numeric
        val parsed = listOf(
            // Nasal o has no o/o͘ contrast: Tâi-lô `onn`, POJ `oⁿ`; `o͘ⁿ`/`oonn` normalize to it (as KeSi does).
            "hō͘ⁿ" to "honn7",
            "hoonn3" to "honn3",
            // POJ ṳ / o̤ (台華線頂對照典, ChhoeTaigi) are Tâi-lô ir / er; KeSi passes them through unparsed.
            "sṳ́" to "sir2",
            "khṳ̀" to "khir3",
            "Ṳ" to "Ir",
            "o̤" to "er",
            // ASCII POJ for them (iTaigi's POJ input): ur / or / ore.
            "sur2" to "sir2",
            "khor2" to "kher2",
            "kore1" to "kere1",
            "pe̍h-ōe-jī" to "peh8-ue7-ji7",
        )
        for ((text, numeric) in parsed) assertEquals(text, numeric, numeric(text))
    }

    @Test
    fun implicitTones() {
        assertEquals(1, Syllable("tai").withImplicitTone().tone)
        assertEquals(4, Syllable("ah").withImplicitTone().tone)
    }

    @Test
    fun fuzzyRules() {
        assertEquals("o", FuzzyRule.loosen("oo", setOf(FuzzyRule.O_OO)))
        assertEquals("tshi", FuzzyRule.loosen("tshinn", setOf(FuzzyRule.NASAL)))
        assertEquals("ts", FuzzyRule.loosen("tsh", setOf(FuzzyRule.ASPIRATION)).take(2))
        assertEquals("gi", FuzzyRule.loosen("gu", setOf(FuzzyRule.I_U)))
        assertEquals("gin", FuzzyRule.loosen("gin", setOf(FuzzyRule.I_U)))
        assertEquals("lit", FuzzyRule.loosen("jit", setOf(FuzzyRule.J_L)))
        assertEquals("ta", FuzzyRule.loosen("tak", setOf(FuzzyRule.ENTERING_TONE)))
        assertEquals("ta", FuzzyRule.loosen("tat", setOf(FuzzyRule.ENTERING_TONE)))
        assertEquals("hia", FuzzyRule.loosen("hiannh", setOf(FuzzyRule.NASAL, FuzzyRule.ENTERING_TONE)))
        assertEquals("m", FuzzyRule.loosen("m", setOf(FuzzyRule.ENTERING_TONE)))
        assertEquals("kan", FuzzyRule.loosen("kam", setOf(FuzzyRule.CODA_M_N)))
        assertEquals("hm", FuzzyRule.loosen("hm", setOf(FuzzyRule.CODA_M_N)))
        assertEquals("sin", FuzzyRule.loosen("sing", setOf(FuzzyRule.CODA_N_NG)))
        assertEquals("ng", FuzzyRule.loosen("ng", setOf(FuzzyRule.CODA_N_NG)))
        assertEquals("kam", FuzzyRule.loosen("kam", setOf(FuzzyRule.CODA_N_NG)))
        assertEquals("ki", FuzzyRule.loosen("gi", setOf(FuzzyRule.VOICING)))
        assertEquals("pah", FuzzyRule.loosen("bah", setOf(FuzzyRule.VOICING)))
        assertEquals("ngo", FuzzyRule.loosen("ngo", setOf(FuzzyRule.VOICING)))
    }

    @Test
    fun respellsPinyinInitials() {
        assertEquals("tage", Romanization.respellPinyinInitial("dage"))
        assertEquals("Tai", Romanization.respellPinyinInitial("Dai"))
        assertEquals("tsui", Romanization.respellPinyinInitial("zui"))
        assertEquals("tsua", Romanization.respellPinyinInitial("zhua"))
        assertEquals("tshit", Romanization.respellPinyinInitial("cit"))
        assertEquals("tshiu", Romanization.respellPinyinInitial("qiu"))
        assertEquals("sio", Romanization.respellPinyinInitial("xio"))
        // POJ ch/chh and real Taigi initials are untouched.
        assertEquals("chhit", Romanization.respellPinyinInitial("chhit"))
        assertEquals("gi", Romanization.respellPinyinInitial("gi"))
    }

    private fun render(numeric: String, system: org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem) =
        Romanization.render(Romanization.parseWord(numeric)!!, system)
}
