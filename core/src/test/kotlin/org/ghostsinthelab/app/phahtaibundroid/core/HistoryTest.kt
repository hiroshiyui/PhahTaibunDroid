package org.ghostsinthelab.app.phahtaibundroid.core

import org.ghostsinthelab.app.phahtaibundroid.core.engine.CandidateEngine
import org.ghostsinthelab.app.phahtaibundroid.core.engine.Composer
import org.ghostsinthelab.app.phahtaibundroid.core.engine.FrequencyHistory
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

/** Learning from the user's choices: single words and word pairs. */
class HistoryTest {
    private val gua = LexiconEntry("我", null, "gua2", 8)
    private val li = LexiconEntry("你", null, "li2", 8)
    private val si7 = LexiconEntry("是", null, "si7", 10)
    private val si5 = LexiconEntry("時", null, "si5", 2)
    private val lexicon = Lexicon(listOf(gua, li, si7, si5))

    private fun composer(history: FrequencyHistory) = Composer(CandidateEngine(lexicon, history), history = history)

    private fun Composer.pick(raw: String, hanlo: String): String? {
        raw.forEach(::type)
        return select(candidates.indexOfFirst { it.hanlo == hanlo })
    }

    /** Teaches 我 → 時 and 你 → 是, so both "si" words have been chosen equally often. */
    private fun trained(): FrequencyHistory {
        val history = FrequencyHistory()
        composer(history).run {
            "guasi".forEach(::type)
            assertNull(select(candidates.indexOfFirst { it.hanlo == "我" }))
            assertEquals("我時", select(candidates.indexOfFirst { it.hanlo == "時" }))
        }
        composer(history).run {
            "lisi".forEach(::type)
            assertNull(select(candidates.indexOfFirst { it.hanlo == "你" }))
            assertEquals("你是", select(candidates.indexOfFirst { it.hanlo == "是" }))
        }
        return history
    }

    @Test
    fun learnedPairRanksFirstAfterItsWord() {
        val history = trained()
        assertTrue(history.pairBoost(gua, si5) > 0)
        assertEquals(0, history.pairBoost(gua, si7))
        composer(history).run {
            "guasi".forEach(::type)
            select(candidates.indexOfFirst { it.hanlo == "我" })
            assertEquals("時", candidates.first().hanlo)
        }
        composer(history).run {
            "lisi".forEach(::type)
            select(candidates.indexOfFirst { it.hanlo == "你" })
            assertEquals("是", candidates.first().hanlo)
        }
    }

    @Test
    fun pairsCarryOverToTheNextComposition() {
        val history = trained()
        val composer = composer(history)
        assertEquals("我", composer.pick("gua", "我"))
        "si".forEach(composer::type)
        assertEquals("時", composer.candidates.first().hanlo)

        // After punctuation or a cursor move, the next word no longer follows 我.
        composer.reset()
        assertEquals("我", composer.pick("gua", "我"))
        composer.forgetContext()
        "si".forEach(composer::type)
        assertEquals("是", composer.candidates.first().hanlo)
    }

    @Test
    fun choosingAPairAcrossCompositionsTeachesIt() {
        val history = FrequencyHistory()
        val composer = composer(history)
        assertEquals("我", composer.pick("gua", "我"))
        assertEquals("時", composer.pick("si", "時"))
        assertTrue(history.pairBoost(gua, si5) > 0)
    }

    @Test
    fun pairsSurviveSaveAndLoad() {
        val history = trained()
        val saved = StringWriter().also(history::save).toString()
        assertTrue(saved, saved.lines().any { it == "1\tgua2\t我\tsi5\t時" })

        val loaded = FrequencyHistory().apply { load(StringReader(saved)) }
        assertEquals(history.pairBoost(gua, si5), loaded.pairBoost(gua, si5))
        assertEquals(history.boost("時", "si5"), loaded.boost("時", "si5"))
    }

    @Test
    fun oldHistoryFilesStillLoad() {
        val loaded = FrequencyHistory().apply { load(StringReader("3\tsi5\t時\n2\tgua2\t我\n")) }
        assertTrue(loaded.boost("時", "si5") > 0)
        assertEquals(0, loaded.pairBoost(gua, si5))
    }

    @Test
    fun pairsAreCapped() {
        val history = FrequencyHistory(maxPairs = 2)
        history.recordPair(gua, si5)
        history.recordPair(li, si7)
        history.recordPair(gua, si7)
        assertEquals(0, history.pairBoost(gua, si5)) // least recently used, dropped
        assertTrue(history.pairBoost(gua, si7) > 0)
    }
}
