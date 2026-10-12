package org.ghostsinthelab.app.phahtaibundroid.core

import org.ghostsinthelab.app.phahtaibundroid.core.engine.CandidateEngine
import org.ghostsinthelab.app.phahtaibundroid.core.engine.Composer
import org.ghostsinthelab.app.phahtaibundroid.core.engine.OutputMode
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Bigrams
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.RomanSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class BigramTest {
    private val gua = LexiconEntry("我", null, "gua2", 40)
    private val guaForeign = LexiconEntry("外", null, "gua7", 60)
    private val si = LexiconEntry("是", null, "si7", 100)
    private val siFour = LexiconEntry("四", null, "si3", 120)
    private val taiuan = LexiconEntry("台灣", null, "Tai5-uan5", 60)
    private val lang = LexiconEntry("人", null, "lang5", 80)
    private val entries = listOf(
        gua, guaForeign, si, siFour, taiuan, lang,
        LexiconEntry("台", null, "tai5", 5),
        LexiconEntry("語", null, "gi2", 5),
        LexiconEntry("台語", null, "Tai5-gi2", 10),
        LexiconEntry("大員", null, "tai7-uan5", 2),
        LexiconEntry("tsuh", null, "tsuh4-tsuh4", 2), // makes `tsuh` a known syllable without a word of its own
    )
    private val bigrams = Bigrams.of(listOf(
        Bigrams.Pair(gua, si, 20),
        Bigrams.Pair(si, taiuan, 5),
        Bigrams.Pair(taiuan, lang, 25),
    ))
    private val engine = CandidateEngine(Lexicon(entries, bigrams = bigrams))
    private val plain = CandidateEngine(Lexicon(entries))

    @Test
    fun convertsLongInputAsOnePhrase() {
        val top = engine.candidates("guasitaioanlang").first()
        assertEquals("我是台灣人", top.hanlo)
        assertEquals("guá sī Tâi-uân lâng", top.tailo)
        assertEquals("góa sī Tâi-oân lâng", top.poj)
        assertEquals("guasitaioanlang".length, top.consumed)
        assertEquals(listOf(gua, si, taiuan, lang), top.entries)
        // Without word-pair counts the more frequent single words win.
        assertEquals("外四台灣人", plain.candidates("guasitaioanlang").first().hanlo)
    }

    @Test
    fun keepsSingleWordsAheadOfCompositions() {
        val candidates = engine.candidates("taigi")
        assertEquals("台語", candidates.first().hanlo)
        assertTrue(candidates.none { it.entries.size > 1 })
    }

    @Test
    fun keepsUnknownSyllablesAsRomanization() {
        val top = engine.candidates("guatsuh").first { it.entries.size > 1 }
        assertEquals("外tsuh", top.hanlo)
        assertEquals("guā tsuh", top.tailo)
    }

    @Test
    fun boostsWordsFollowingTheSelectedOne() {
        assertEquals("四", engine.candidates("si").first().hanlo)
        assertEquals("是", engine.candidates("si", context = gua).first().hanlo)
        assertEquals("四", engine.candidates("si", context = guaForeign).first().hanlo)

        val composer = Composer(engine)
        "guasi".forEach(composer::type)
        assertNull(composer.select(composer.candidates.indexOfFirst { it.hanlo == "我" }))
        assertEquals("是", composer.candidates.first().hanlo)
    }

    @Test
    fun selectedPhraseRendersInBothModes() {
        val hanlo = Composer(engine)
        "guasitaioanlang".forEach(hanlo::type)
        assertEquals("我是台灣人", hanlo.select(0))

        val roman = Composer(engine, mode = OutputMode.ROMAN)
        "guasitaioanlang".forEach(roman::type)
        assertEquals("guá sī Tâi-uân lâng", roman.select(0))

        val poj = Composer(engine, system = RomanSystem.POJ, mode = OutputMode.ROMAN)
        "guasitaioanlang".forEach(poj::type)
        assertEquals("góa sī Tâi-oân lâng", poj.select(0))
    }

    @Test
    fun readsAndWritesWordPairs() {
        val out = StringWriter()
        Bigrams.write(listOf(Bigrams.Pair(gua, si, 20), Bigrams.Pair(taiuan, lang, 3)), out)
        val text = "# comment\n" + out + "不在\tbo5-tsai7\t人\tlang5\t9\n"
        val read = Bigrams.read(text.byteInputStream(), entries)
        assertEquals(2, read.size) // the pair with an unknown word is dropped
        assertEquals(20, read.count(gua, si))
        assertEquals(3, read.count(taiuan, lang))
        assertEquals(0, read.count(si, gua))
    }
}
