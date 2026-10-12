package org.ghostsinthelab.app.phahtaibundroid.core

import org.ghostsinthelab.app.phahtaibundroid.core.engine.CandidateEngine
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
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
    fun prefersConsonantOnsetsAmongEqualSegmentations() {
        val best = engine.segment("lihokamsia").first().map { it.base }
        assertTrue("got $best", best == listOf("li", "ho", "kam", "sia"))
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
