package org.ghostsinthelab.app.phahtaibundroid.core

import org.ghostsinthelab.app.phahtaibundroid.core.engine.CandidateEngine
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Sanity checks against the real compiled lexicon shipped in the app's assets. */
class LexiconAssetTest {
    companion object {
        private lateinit var engine: CandidateEngine

        @BeforeClass
        @JvmStatic
        fun load() {
            val file = File("../app/src/main/assets/lexicon.tsv")
            assumeTrue("lexicon asset not built", file.isFile)
            val start = System.nanoTime()
            val lexicon = Lexicon(file.inputStream().use(Lexicon::readEntries))
            println("loaded ${lexicon.entries.size} entries in ${(System.nanoTime() - start) / 1_000_000} ms")
            engine = CandidateEngine(lexicon)
        }
    }

    private fun assertTopContains(raw: String, expected: String, within: Int = 5) {
        val top = engine.candidates(raw).take(within).map { it.hanlo ?: it.tailo }
        assertTrue("$raw → $top, expected $expected", expected in top)
    }

    @Test
    fun commonWords() {
        assertTopContains("taigi", "台語")
        assertTopContains("tai5gi2", "台語")
        assertTopContains("lihó", "你好")
        assertTopContains("liho", "你好")
        assertTopContains("chhit-tho", "𨑨迌")
        assertTopContains("tshittho", "𨑨迌")
        assertTopContains("kamsia", "感謝")
        assertTopContains("sinsenn", "先生")
        assertTopContains("sinsi", "先生") // nasal omitted
    }

    @Test
    fun pinyinHabits() {
        val loose = CandidateEngine(engine.lexicon.withRules(FuzzyRule.DEFAULTS + FuzzyRule.ENTERING_TONE + FuzzyRule.VOICING))
        val top = loose.candidates("dageho").take(3).map { it.hanlo ?: it.tailo }
        assertTrue("dageho → $top", "逐家好" in top)
        assertTopContains("dakkeho", "逐家好")
    }

    @Test
    fun nasalCodaMergers() {
        val mn = CandidateEngine(engine.lexicon.withRules(FuzzyRule.DEFAULTS + FuzzyRule.CODA_M_N))
        assertTrue("kansia", "感謝" in mn.candidates("kansia").take(3).map { it.hanlo })
        val nng = CandidateEngine(engine.lexicon.withRules(FuzzyRule.DEFAULTS + FuzzyRule.CODA_N_NG))
        assertTrue("singbun", "新聞" in nng.candidates("singbun").take(5).map { it.hanlo })
    }

    @Test
    fun prefersConsonantOnsetsAmongEqualSegmentations() {
        val best = engine.segment("lihokamsia").first().map { it.base }
        assertTrue("got $best", best == listOf("li", "ho", "kam", "sia"))
    }

    @Test
    fun neutralToneEntries() {
        for (raw in listOf("tnglai", "tng2lai5", "amgonge")) {
            val top = engine.candidates(raw).take(3).map { it.tailo }
            assertTrue("$raw → $top", top.any { "--" in it })
        }
        assertTrue(engine.candidates("tng--khi").first().tailo == "tńg--khì")
    }

    @Test
    fun abbreviatedInput() {
        assertTopContains("tg", "台語")
        assertTopContains("tgi", "台語", within = 1)
        assertTopContains("lh", "你好")
        assertTopContains("kms", "感謝", within = 12)
        assertTopContains("ks", "見笑")
    }

    @Test
    fun wildcardInput() {
        assertTopContains("s?ah", "煞")
        assertTopContains("tai-?", "臺灣")
        assertTopContains("?-gi", "台語", within = 12)
    }

    @Test
    fun patternInputIsFast() {
        for (raw in listOf("tg", "kms", "lhkms", "tgkslhtgkslh", "s?ah", "tai-?", "?-?", "?ah", "gua?iutaioan?anggongbekongtaigi")) {
            val start = System.nanoTime()
            repeat(10) { engine.candidates(raw) }
            val perCall = (System.nanoTime() - start) / 10 / 1_000_000
            println("$raw: $perCall ms per lookup → ${engine.candidates(raw).take(5).map { it.hanlo ?: it.tailo }}")
            assertTrue("$raw too slow: $perCall ms", perCall < 200)
        }
    }

    @Test
    fun longInputIsFast() {
        val start = System.nanoTime()
        repeat(20) { engine.candidates("guasiutaioanlanggongbekongtaigi") }
        val perCall = (System.nanoTime() - start) / 20 / 1_000_000
        println("long input: $perCall ms per lookup")
        assertTrue("too slow: $perCall ms", perCall < 200)
    }
}
