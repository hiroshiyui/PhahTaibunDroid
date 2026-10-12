package org.ghostsinthelab.app.phahtaibundroid.core

import org.ghostsinthelab.app.phahtaibundroid.core.engine.CandidateEngine
import org.ghostsinthelab.app.phahtaibundroid.core.engine.Composer
import org.ghostsinthelab.app.phahtaibundroid.core.engine.FrequencyHistory
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val entries = listOf(
        LexiconEntry("台語", null, "Tai5-gi2", 10),
        LexiconEntry("台", null, "tai5", 5),
        LexiconEntry("語", null, "gi2", 5),
        LexiconEntry("語", null, "gu2", 2),
        LexiconEntry("大", null, "tua7", 8),
        LexiconEntry("鴨仔囝", null, "ah4-a2-kiann2", 3),
        LexiconEntry("烏", null, "oo1", 4),
        LexiconEntry("生", null, "tshinn1", 4),
        LexiconEntry("水", null, "tsui2", 9),
        LexiconEntry("愛suí", "愛súi", "ai3-sui2", 2),
        LexiconEntry("講", null, "kong2", 9),
        LexiconEntry("講話", null, "kong2-ue7", 6),
        LexiconEntry("閣", null, "koh4", 6),
        LexiconEntry("逐家好", null, "tak8-ke1-ho2", 5),
        LexiconEntry("罩", null, "ta3", 3),
    )
    private val lexicon = Lexicon(entries)
    private val engine = CandidateEngine(lexicon)

    private fun top(raw: String) = engine.candidates(raw).first()

    @Test
    fun convertsUnseparatedInput() {
        assertEquals("台語", top("taigi").hanlo)
        assertEquals("Tâi-gí", top("taigi").tailo)
    }

    @Test
    fun acceptsPojAndTailoSpellings() {
        assertEquals("水", top("chui").hanlo)
        assertEquals("水", top("tsui").hanlo)
        assertEquals("大", top("toa").hanlo)
        assertEquals("講話", top("kongoe").hanlo)
        assertEquals("講話", top("kongue").hanlo)
    }

    @Test
    fun fuzzyMatchesMissingDiacriticsAndNasals() {
        assertEquals("烏", top("o").hanlo) // o͘ typed as o
        assertEquals("生", top("chhi").hanlo) // nasal omitted
        assertEquals("鴨仔囝", top("ahakia").hanlo)
    }

    @Test
    fun tonesDisambiguate() {
        val withTones = engine.candidates("tai5gi2")
        assertEquals("台語", withTones.first().hanlo)
        // gu2 only matches fuzzily when I_U is on; with tones typed nothing should be lost.
        assertTrue(engine.candidates("gi3").first().score < engine.candidates("gi2").first().score)
    }

    @Test
    fun completesPartialSyllable() {
        assertEquals("台語", top("taig").hanlo)
    }

    @Test
    fun fuzzyRulesAreConfigurable() {
        assertNull(engine.candidates("gu").firstOrNull { it.hanlo == "語" && it.numeric == "gi2" })
        val dialect = CandidateEngine(lexicon.withRules(FuzzyRule.DEFAULTS + FuzzyRule.I_U))
        assertTrue(dialect.candidates("gu").any { it.numeric == "gi2" })
    }

    @Test
    fun acceptsPinyinInitials() {
        assertEquals("台語", top("dai5gi2").hanlo)
        assertEquals("水", top("zui").hanlo)
        assertEquals("tak-ke-ho", engine.segment("dakkeho").first().joinToString("-") { it.base })
    }

    @Test
    fun pinyinHabitRulesMatchLooseSpelling() {
        assertTrue(engine.candidates("dageho").none { it.hanlo == "逐家好" })
        val loose = CandidateEngine(lexicon.withRules(FuzzyRule.DEFAULTS + FuzzyRule.ENTERING_TONE + FuzzyRule.VOICING))
        val top = loose.candidates("dageho").first()
        assertEquals("逐家好", top.hanlo)
        assertEquals("ta̍k-ke-hó", top.tailo)
        // Mixed-up entering-tone codas match too.
        assertEquals("逐家好", loose.candidates("tatkeho").first().hanlo)
    }

    @Test
    fun alwaysOffersRomanization() {
        val candidates = engine.candidates("xyz")
        assertEquals(1, candidates.size)
        assertEquals("xyz", candidates.single().tailo)
        assertEquals("tâi-gí", engine.candidates("tai5gi2").first { it.hanlo == null }.tailo)
    }

    @Test
    fun composerSelectsPieceByPieceAndTogglesMode() {
        val composer = Composer(engine)
        "kongkoh".forEach(composer::type)
        val first = composer.candidates.indexOfFirst { it.hanlo == "講" }
        assertNull(composer.select(first))
        assertEquals("講koh", composer.preview())
        composer.toggleMode()
        assertEquals("kóng koh", composer.preview())
        composer.toggleMode()
        assertEquals("講閣", composer.commitFirst())
        assertTrue(!composer.isComposing)
    }

    @Test
    fun composerBackspaceUndoesSelection() {
        val composer = Composer(engine)
        "kongkoh".forEach(composer::type)
        composer.select(composer.candidates.indexOfFirst { it.hanlo == "講" })
        repeat(3) { composer.backspace() }
        assertEquals("", composer.rawInput)
        assertTrue(composer.backspace()) // pops the selection back into raw input
        assertEquals("kong", composer.rawInput)
        assertTrue(composer.backspace())
        assertEquals("kon", composer.rawInput)
    }

    @Test
    fun composerRendersPojHanlo() {
        val composer = Composer(engine, system = RomanSystem.POJ)
        "aisui".forEach(composer::type)
        assertEquals("愛súi", composer.commitFirst())
    }

    @Test
    fun romanModeDeduplicatesAndKeepsCapital() {
        val composer = Composer(engine, mode = OutputMode.ROMAN)
        "Gi".forEach(composer::type)
        val shown = composer.displayCandidates().map { it.text }
        assertEquals(shown.distinct(), shown)
        assertEquals("Gí", shown.first())
    }

    @Test
    fun historyPromotesChosenCandidates() {
        val history = FrequencyHistory()
        val ranked = CandidateEngine(lexicon, history)
        assertEquals("gi2", ranked.candidates("gi").first().numeric)
        history.record("語", "gu2")
        val withI_U = CandidateEngine(lexicon.withRules(FuzzyRule.DEFAULTS + FuzzyRule.I_U), history)
        assertEquals("gu2", withI_U.candidates("gi").first().numeric)
    }
}
