package com.hazia.medicall.ui.theme

import androidx.compose.ui.graphics.Color
import com.hazia.medicall.core.look.Palette
import java.io.File

/** The repository's root, which Gradle passes to the tests ("medicall.root"). */
val ROOT: File = File(requireNotNull(System.getProperty("medicall.root")) { "run through Gradle: medicall.root is not set" })

/**
 * SPEC.md's tables, read the way a person reads them, so the tests compare the theme with the spec itself and not
 * with a copy of it.
 */
object Spec {
    private val lines: List<String> by lazy { File(ROOT, "SPEC.md").readLines() }

    /** The tables inside rule [rule] (as "R114"), each a list of rows of trimmed cells, header first, the rule row dropped. */
    fun tables(rule: String): List<List<List<String>>> {
        val start = lines.indexOfFirst { Regex("""^### [A-Z]-$rule · """).containsMatchIn(it) }
        require(start >= 0) { "no rule $rule in SPEC.md" }
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("### ") || lines[it].startsWith("## ") } ?: lines.size
        val tables = mutableListOf<List<List<String>>>()
        var current = mutableListOf<List<String>>()
        for (line in lines.subList(start + 1, end)) {
            if (line.startsWith("|")) {
                val cells = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                if (cells.all { it.matches(Regex("-+")) }) continue
                current += cells
            } else if (current.isNotEmpty()) {
                tables += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) tables += current
        return tables
    }

    /** The table of rule [rule] whose header row is [header]. */
    fun table(rule: String, header: List<String>): List<List<String>> =
        tables(rule).singleOrNull { it.first() == header }
            ?: error("SPEC.md's $rule has no table headed $header; it has ${tables(rule).map { it.first() }}")

    /** The palettes by the names SPEC.md gives them. */
    val PALETTES = mapOf(
        "Albarello" to Palette.ALBARELLO,
        "Plum & olive" to Palette.PLUM_AND_OLIVE,
        "Capsule" to Palette.CAPSULE,
        "Murano" to Palette.MURANO,
    )

    /**
     * A palette-and-mode table (its first two columns Palette and Mode) as colours by palette, mode (true for dark)
     * and column name, the header's own words.
     */
    fun colours(rule: String, header: List<String>): Map<Pair<Palette, Boolean>, Map<String, Color>> {
        val table = table(rule, header)
        require(header.take(2) == listOf("Palette", "Mode"))
        return table.drop(1).associate { row ->
            val palette = requireNotNull(PALETTES[row[0]]) { "unknown palette ${row[0]}" }
            val dark = when (row[1]) {
                "light" -> false
                "dark" -> true
                else -> error("unknown mode ${row[1]}")
            }
            (palette to dark) to header.drop(2).zip(row.drop(2)).associate { (name, hex) -> name to hexColour(hex) }
        }
    }

    fun hexColour(hex: String): Color {
        require(Regex("#[0-9A-F]{6}").matches(hex)) { "not a colour: $hex" }
        return Color((0xFF000000 or hex.substring(1).toLong(16)).toInt())
    }
}
