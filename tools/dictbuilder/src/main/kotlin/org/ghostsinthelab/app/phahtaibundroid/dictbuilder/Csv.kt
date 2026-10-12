package org.ghostsinthelab.app.phahtaibundroid.dictbuilder

import java.io.File

/** Minimal RFC 4180 reader: quoted fields, doubled quotes, and newlines inside quotes. */
internal object Csv {
    fun read(file: File): List<List<String>> {
        val text = file.readText(Charsets.UTF_8)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                when {
                    c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                    c == '"' -> quoted = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    ',' -> { row += field.toString(); field.setLength(0) }
                    '\r' -> Unit
                    '\n' -> {
                        row += field.toString(); field.setLength(0)
                        rows += row; row = ArrayList()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }
}
