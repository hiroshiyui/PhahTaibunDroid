package org.ghostsinthelab.app.phahtaibun.ime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ghostsinthelab.app.phahtaibun.core.lexicon.Lexicon
import org.ghostsinthelab.app.phahtaibun.core.romanization.FuzzyRule

/**
 * Process-wide lexicon. Parsing the ~86k-entry asset takes a noticeable moment, so it is done
 * once, off the main thread, and the index is rebuilt only when the fuzzy rules change.
 */
object LexiconRepository {
    private const val ASSET = "lexicon.tsv"
    private val mutex = Mutex()
    private var current: Lexicon? = null

    suspend fun get(context: Context, rules: Set<FuzzyRule>): Lexicon = mutex.withLock {
        current?.takeIf { it.rules == rules }?.let { return it }
        withContext(Dispatchers.Default) {
            val loaded = current ?: Lexicon(
                context.applicationContext.assets.open(ASSET).use(Lexicon::readEntries),
                rules,
            )
            loaded.withRules(rules)
        }.also { current = it }
    }
}
