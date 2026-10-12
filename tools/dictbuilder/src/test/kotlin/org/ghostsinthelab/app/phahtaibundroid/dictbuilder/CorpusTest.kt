package org.ghostsinthelab.app.phahtaibundroid.dictbuilder

import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Bigrams
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.LexiconEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CorpusTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val entries = listOf(
        LexiconEntry("臺灣", null, "Tai5-uan5", 12),
        LexiconEntry("台灣", null, "Tai5-uan5", 2),
        LexiconEntry("人", null, "lang5", 4),
        LexiconEntry("民視", null, "bin5-si7", 2),
        LexiconEntry("新聞", null, "sin1-bun5", 2),
        LexiconEntry("這", null, "tsit4", 2),
    )

    private fun sentence(vararg tokens: String): Sentence = tokens.map { it.substringBefore('/') to it.substringAfter('/') }

    @Test
    fun countsWordsAndPairs() {
        val result = CorpusCounter(entries).count(listOf(
            sentence("台灣/tai5-uan5", "人/lang5"),
            sentence("民視新聞/bin5-si7-sin1-bun5", "，/，", "人/lang5", "的/e5", "人/lang5"),
        ))
        // Variants go to the spelling the dictionaries prefer; 民視新聞 is split into entries.
        assertEquals(listOf(1, 0, 3, 1, 1, 0), result.unigrams.toList())
        assertEquals(1, result.bigrams[CorpusCounter.pairKey(0, 2)])
        assertEquals(1, result.bigrams[CorpusCounter.pairKey(3, 4)])
        // Punctuation and unknown words break the chain.
        assertEquals(2, result.bigrams.size)
        assertEquals(mapOf(("的" to "e5") to 1), result.unknown)
        assertEquals(6, result.wordTokens)
        assertEquals(4, result.exactTokens)
        assertEquals(1, result.splitTokens)
    }

    @Test
    fun readsAlignedLinesOnce() {
        val hanji = tmp.newFile("hanji.txt").apply { writeText("臺灣 人\n臺灣 人\n民視 新聞 ，\n壞 行\n") }
        val tailo = tmp.newFile("tailo.txt").apply { writeText("tai5-uan5 lang5\ntai5-uan5 lang5\nbin5-si7 sin1-bun5 ，\nphainn2\n") }
        val sentences = readAlignedCorpus(hanji, tailo)
        assertEquals(2, sentences.size) // duplicate and misaligned lines skipped
        assertEquals(listOf("民視" to "bin5-si7", "新聞" to "sin1-bun5", "，" to "，"), sentences[1])
    }

    @Test
    fun reweightsAndAddsFrequentWords() {
        val corpus = buildList {
            repeat(10) { add(sentence("臺灣/tai5-uan5", "人/lang5")) }
            repeat(10) { add(sentence("的/e5", "人/lang5")) }
            repeat(3) { add(sentence("臺灣的/tai5-uan5-e5", "人/lang5")) } // 臺灣 + 的: not a word
            repeat(4) { add(sentence("這馬/tsit4-ma2", "，/，")) } // 這 + 馬, but 這 is never seen alone
            repeat(3) { add(sentence("馬/ma2", "，/，")) }
            repeat(2) { add(sentence("罕/han2")) } // too rare
        }
        val (result, pairs) = applyCorpus(entries, corpus, log = {})
        val byWord = result.associateBy { it.hanlo }
        assertEquals(12 + 13 * Bigrams.CORPUS_WEIGHT, byWord.getValue("臺灣").weight)
        assertEquals(2, byWord.getValue("台灣").weight) // no corpus count: the dictionary prior stays
        assertEquals(13 * Bigrams.CORPUS_WEIGHT, byWord.getValue("的").weight)
        assertTrue("這馬" in byWord)
        assertFalse("臺灣的" in byWord)
        assertFalse("罕" in byWord)
        val pairCounts = pairs.associate { "${it.first.hanlo} ${it.second.hanlo}" to it.count }
        assertEquals(10, pairCounts["臺灣 人"])
        assertEquals(13, pairCounts["的 人"])
        assertEquals(3, pairCounts["臺灣 的"])
    }
}
