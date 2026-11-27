package com.hazia.medicall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The theme's type against SPEC.md's tables (SPEC:R115). */
class TypeSpecTest {

    /** Stand-ins for the faces, one per Fraunces optical size, so a test can tell which family a style took. */
    private val frauncesAt = mapOf(33 to FontFamily.Cursive, 26 to FontFamily.Serif, 21 to FontFamily.Monospace)
    private val atkinson = FontFamily.SansSerif
    private val type = medicAllType({ frauncesAt.getValue(it) }, atkinson)

    private val roleTable = Spec.table("R115", listOf("Role", "Face", "Size / line height (sp)", "Weight", "Used for"))

    @Test
    fun theSevenRolesAreTheSpecs_SPEC_R115() {
        val spec = roleTable.drop(1).associate { row ->
            val (size, line) = row[2].split("/").map { it.trim().toInt() }
            val face = when (row[1]) {
                "Fraunces" -> Face.FRAUNCES
                "Atkinson Hyperlegible Next" -> Face.ATKINSON_HYPERLEGIBLE_NEXT
                else -> error("unknown face ${row[1]}")
            }
            row[0].uppercase() to listOf(face, size, line, row[3].toInt())
        }
        assertEquals(spec, TypeRole.entries.associate { it.name to listOf(it.face, it.sizeSp, it.lineHeightSp, it.weight) })
    }

    @Test
    fun eachRolesStyleIsItsFaceSizeAndWeight_SPEC_R115() {
        for (role in TypeRole.entries) {
            val s = type.of(role)
            assertEquals(role.sizeSp.sp, s.fontSize, "$role")
            assertEquals(role.lineHeightSp.sp, s.lineHeight, "$role")
            assertEquals(FontWeight(role.weight), s.fontWeight, "$role")
            val family = if (role.face == Face.FRAUNCES) frauncesAt.getValue(role.sizeSp) else atkinson
            assertEquals(family, s.fontFamily, "$role takes its face, Fraunces at its own optical size")
        }
    }

    @Test
    fun noWeightIsAbove600_SPEC_R115() {
        assertTrue(TypeRole.entries.all { it.weight <= 600 })
        assertTrue(ATKINSON_WEIGHTS.all { it <= 600 } && FRAUNCES_WEIGHT <= 600)
        assertTrue(styles(type.material()).values.all { it.fontWeight!!.weight <= 600 })
    }

    @Test
    fun theMaterialMappingIsTheSpecs_SPEC_R115() {
        val table = Spec.table("R115", listOf("Material 3 role", "Takes", "Drawn by, for example"))
        val spec = table.drop(1).flatMap { row ->
            row[0].split(",").map { it.trim() to TypeRole.valueOf(row[1].uppercase()) }
        }.toMap()
        assertEquals(15, spec.size)
        assertEquals(spec, MedicAllType.MATERIAL_ROLES)
    }

    /**
     * Every public TextStyle a [Typography] holds, read from the class, so a role Material makes public later fails
     * here until it is set. Internal ones (a `$` in the getter's name, as Material 3 1.9's emphasized roles) are out
     * of every public component's reach.
     */
    private fun styles(typography: Typography): Map<String, TextStyle> =
        Typography::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java && it.name.startsWith("get") && '$' !in it.name }
            .associate { m -> m.name.removePrefix("get").replaceFirstChar { it.lowercase() } to m.invoke(typography) as TextStyle }

    @Test
    fun everyMaterialRoleTakesItsRole_SPEC_R115() {
        val material = styles(type.material())
        assertEquals(MedicAllType.MATERIAL_ROLES.keys, material.keys, "all fifteen public roles")
        for ((name, style) in material) {
            val role = MedicAllType.MATERIAL_ROLES.getValue(name)
            assertEquals(type.of(role), style, "$name takes ${role.name.lowercase()}")
        }
    }

    @Test
    fun theSelectedNavigationLabelIsAt600_SPEC_R122() {
        assertEquals(type.label.copy(fontWeight = FontWeight(600)), type.navigationLabel(selected = true))
        assertEquals(type.label, type.navigationLabel(selected = false))
        assertEquals(TypeRole.LABEL, MedicAllType.MATERIAL_ROLES.getValue("labelMedium"), "the navigation bar's role")
        assertTrue(600 in ATKINSON_WEIGHTS, "Atkinson is bundled at 600")
    }

    @Test
    fun tabularFiguresAreAvailable_SPEC_R115() {
        assertEquals("tnum", type.body.tabular().fontFeatureSettings)
        assertEquals(type.body.copy(fontFeatureSettings = null), type.body.tabular().copy(fontFeatureSettings = null))
    }

    private val fonts = File(ROOT, "ui/src/commonMain/composeResources/font")

    @Test
    fun theBundledFontsAreTheSpecs_SPEC_R115() {
        val atkinson = File(fonts, "atkinson_hyperlegible_next_variable.ttf").readBytes()
        val fraunces = File(fonts, "fraunces_variable.ttf").readBytes()
        // Atkinson Hyperlegible Next 2.001, which has tabular figures and a weight axis.
        assertTrue(atkinson.contains("Version 2.001".toByteArray(Charsets.UTF_16BE)), "Atkinson Hyperlegible Next 2.001")
        assertTrue(atkinson.contains("Atkinson Hyperlegible Next".toByteArray(Charsets.UTF_16BE)))
        assertTrue(atkinson.contains("tnum".toByteArray()) && atkinson.contains("wght".toByteArray()))
        // Fraunces with its optical size and weight axes, which the theme sets, and no tabular figures, which is why
        // R115 keeps tabular figures to Atkinson roles. A Fraunces that gains them fails here, for the owner to see.
        assertTrue(fraunces.contains("Fraunces".toByteArray(Charsets.UTF_16BE)))
        assertTrue(!fraunces.contains("tnum".toByteArray()), "Fraunces has no tnum (R115's evidence)")
        assertTrue(fraunces.contains("opsz".toByteArray()) && fraunces.contains("wght".toByteArray()))
        assertEquals(setOf("atkinson_hyperlegible_next_variable.ttf", "fraunces_variable.ttf"), fonts.list()!!.toSet())
    }

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { i -> part.indices.all { this[i + it] == part[it] } }
}
