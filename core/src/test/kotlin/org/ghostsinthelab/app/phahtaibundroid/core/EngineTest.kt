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
        LexiconEntry("轉", null, "tng2", 4),
        LexiconEntry("轉去", null, "tng2-khi3", 5),
        LexiconEntry("轉去", null, "tng2--khi3", 4),
        LexiconEntry("轉來", null, "tng2--lai5", 2),
        LexiconEntry("食", null, "tsiah8", 6),
        LexiconEntry("食飽", null, "tsiah8-pa2", 4),
        LexiconEntry("ah", null, "ah4", 3),
        LexiconEntry("你", null, "li2", 6),
        LexiconEntry("好", null, "ho2", 6),
        LexiconEntry("感謝", null, "kam2-sia7", 7),
        LexiconEntry("大細", null, "tua7-se3", 4),
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
        assertEquals("kóng-koh", composer.preview())
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
    fun romanModeHyphenatesPiecesTypedAsOneRun() {
        val composer = Composer(engine, mode = OutputMode.ROMAN)
        "lihokamsia".forEach(composer::type)
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "你" }))
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "好" }))
        assertEquals("lí-hó-kám-siā", composer.select(composer.candidates.indexOfFirst { it.hanlo == "感謝" }))
    }

    @Test
    fun romanModeKeepsTypedSeparators() {
        val composer = Composer(engine, mode = OutputMode.ROMAN)
        "Tsiahpa--ah".forEach(composer::type)
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "食飽" }))
        assertEquals("ah", composer.rawInput)
        assertEquals("Tsia̍h-pá--ah", composer.preview())
        composer.toggleMode()
        assertEquals("食飽ah", composer.preview())
        repeat(3) { composer.backspace() } // the selection comes back with its separator
        assertEquals("Tsiahpa--", composer.rawInput)
    }

    @Test
    fun hanloModeSeparatesAdjacentRomanizedPieces() {
        val composer = Composer(engine)
        "ahah".forEach(composer::type)
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "ah" }))
        assertEquals("ah-ah", composer.commitFirst())
    }

    @Test
    fun neutralToneEntriesMatchWithoutDoubleHyphen() {
        for (raw in listOf("tnglai", "tng-lai", "tng2lai5", "tng--lai")) {
            val top = top(raw)
            assertEquals(raw, "轉來", top.hanlo)
            assertEquals(raw, "tńg--lâi", top.tailo)
            assertEquals(raw, "tńg--lâi", top.poj)
        }
    }

    @Test
    fun typedDoubleHyphenPrefersNeutralTone() {
        assertEquals("tńg-khì", top("tngkhi").tailo)
        assertEquals("tńg--khì", top("tng--khi").tailo)
    }

    @Test
    fun typedDoubleHyphenOffersNeutralSuffixes() {
        val top = top("tsiahpa--ah")
        assertEquals("食飽矣", top.hanlo)
        assertEquals("tsia̍h-pá--ah", top.tailo)
        assertEquals("tsiah8-pa2--ah4", top.numeric)
        // Without the user's `--`, no guessed compounds.
        assertTrue(engine.candidates("tsiahpaah").none { it.hanlo == "食飽矣" })
    }

    @Test
    fun abbreviatesSyllablesToInitials() {
        assertEquals("台語", top("tg").hanlo)
        assertEquals("Tâi-gí", top("tg").tailo)
        assertEquals(2, top("tg").consumed)
        assertEquals("感謝", top("ks").hanlo)
        assertEquals("感謝", top("kms").hanlo) // initial plus coda
        assertEquals("台語", top("tgi").hanlo) // mixed with a spelled syllable
        assertEquals("台語", top("t-g").hanlo)
        assertEquals("台語", top("t5g2").hanlo)
        assertEquals("tg", engine.candidates("tg").first { it.hanlo == null }.tailo)
    }

    @Test
    fun abbreviationInitialsAreCoarse() {
        // `t` covers t/th/ts/tsh; POJ `ch` (= ts) is typed `c`, which excludes plain t.
        assertTrue(engine.candidates("ts").any { it.hanlo == "大細" })
        assertTrue(engine.candidates("ds").any { it.hanlo == "大細" })
        assertTrue(engine.candidates("cs").none { it.hanlo == "大細" })
        // A lone initial is too vague to look up.
        assertTrue(engine.candidates("tgx").none { it.hanlo == "台" })
    }

    @Test
    fun abbreviationsRankBelowSpelledWords() {
        val candidates = engine.candidates("ts")
        assertEquals("水", candidates.first().hanlo) // `ts` as a partially typed tsui
        assertTrue(candidates.indexOfFirst { it.hanlo == "大細" } > candidates.indexOfFirst { it.hanlo == "水" })
    }

    @Test
    fun composerSelectsAbbreviationsPieceByPiece() {
        val composer = Composer(engine)
        "tgks".forEach(composer::type)
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "台語" }))
        assertEquals("ks", composer.rawInput)
        assertEquals("台語感謝", composer.select(composer.candidates.indexOfFirst { it.hanlo == "感謝" }))
    }

    @Test
    fun wildcardMatchesPartOfASyllable() {
        assertEquals("講", top("k?ng").hanlo)
        assertEquals("水", top("ch?").hanlo) // POJ ch = ts
        assertEquals("水", top("?ui").hanlo)
        assertEquals("講", top("ko?ng").hanlo) // `?` may match nothing
        assertTrue(engine.candidates("k?v").none { it.hanlo != null })
        // Spelled-out matches rank above wildcard ones.
        assertTrue(top("kong").score > engine.candidates("k?ng").first { it.hanlo == "講" }.score)
    }

    @Test
    fun wildcardStandsForAWholeSyllable() {
        assertEquals("台語", top("tai-?").hanlo)
        assertEquals("台語", top("?-gi2").hanlo)
        assertEquals("講", top("kong?").hanlo) // `?` may match nothing…
        assertTrue(engine.candidates("kong?").any { it.hanlo == "講話" }) // …or a syllable of its own
    }

    @Test
    fun composerKeepsWildcardAfterSelectingAPiece() {
        val composer = Composer(engine)
        "tai-?".forEach(composer::type)
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "台" }))
        assertEquals("?", composer.rawInput)
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
