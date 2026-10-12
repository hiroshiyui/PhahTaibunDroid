package org.ghostsinthelab.app.phahtaibundroid.ime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Bigrams
import org.ghostsinthelab.app.phahtaibundroid.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibundroid.core.romanization.FuzzyRule

/**
 * Process-wide lexicon. Parsing the ~88k-entry asset and its word-pair counts takes a noticeable
 * moment, so it is done once, off the main thread, and the index is rebuilt only when the fuzzy
 * rules change.
 */
object LexiconRepository {
    private const val ASSET = "lexicon.tsv"
    private const val BIGRAM_ASSET = "bigrams.tsv"
    private val mutex = Mutex()
    private var current: Lexicon? = null

    suspend fun get(context: Context, rules: Set<FuzzyRule>): Lexicon = mutex.withLock {
        current?.takeIf { it.rules == rules }?.let { return it }
        withContext(Dispatchers.Default) {
            val loaded = current ?: run {
                val assets = context.applicationContext.assets
                val entries = assets.open(ASSET).use(Lexicon::readEntries)
                val bigrams = assets.open(BIGRAM_ASSET).use { Bigrams.read(it, entries) }
                Lexicon(entries, rules, bigrams)
            }
            loaded.withRules(rules)
        }.also { current = it }
    }
}
