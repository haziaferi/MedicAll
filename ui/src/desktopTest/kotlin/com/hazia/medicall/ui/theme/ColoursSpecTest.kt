package com.hazia.medicall.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.atan2
import kotlin.math.hypot
import com.hazia.medicall.core.look.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The theme's colours against SPEC.md's tables (SPEC:R114, SPEC:R121, SPEC:R122). */
class ColoursSpecTest {

    private val looks = Palette.entries.flatMap { listOf(it to false, it to true) }

    private val palettes = Spec.colours(
        "R114",
        listOf("Palette", "Mode", "Ground", "Card", "Text", "Muted text", "Main", "On main", "Accent", "Taken", "Line"),
    )
    private val states = Spec.colours("R122", listOf("Palette", "Mode", "Link", "Focus", "Switch on", "Disabled", "Error"))
    private val containers = Spec.colours(
        "R122",
        listOf("Palette", "Mode", "Primary container", "Secondary container", "Tertiary container", "Disabled fill"),
    )
    private val scales = Spec.colours("R121", listOf("Palette", "Mode", "Scale 1", "Scale 2", "Scale 3"))

    /** Each of the theme's colours by the name SPEC.md's tables give it. */
    private fun named(c: MedicAllColours): Map<String, Color> = mapOf(
        "Ground" to c.ground, "Card" to c.card, "Text" to c.text, "Muted text" to c.mutedText, "Main" to c.main,
        "On main" to c.onMain, "Accent" to c.accent, "Taken" to c.taken, "Line" to c.line,
        "Link" to c.link, "Focus" to c.focus, "Switch on" to c.switchOn, "Disabled" to c.disabled, "Error" to c.error,
        "Primary container" to c.primaryContainer, "Secondary container" to c.secondaryContainer,
        "Tertiary container" to c.tertiaryContainer, "Disabled fill" to c.disabledFill,
        "Scale 1" to c.scale[0], "Scale 2" to c.scale[1], "Scale 3" to c.scale[2],
    )

    @Test
    fun everyPaletteAndModeIsInTheSpec_SPEC_R114() {
        for (table in listOf(palettes, states, containers, scales)) assertEquals(looks.toSet(), table.keys)
    }

    @Test
    fun everyColourIsTheSpecs_SPEC_R114_SPEC_R121_SPEC_R122() {
        for (look in looks) {
            val theme = coloursOf(look.first, look.second)
            assertEquals(look, theme.palette to theme.dark)
            val spec = palettes.getValue(look) + states.getValue(look) + containers.getValue(look) + scales.getValue(look)
            assertEquals(spec.keys, named(theme).keys, "the theme names every colour of the spec and no other")
            for ((name, colour) in spec) assertEquals(colour, named(theme).getValue(name), "$look $name")
        }
    }

    /** OKLCh chroma and hue in degrees, from Compose's own OKLab conversion. */
    private fun chromaHue(c: Color): Pair<Double, Double> {
        val lab = c.convert(ColorSpaces.Oklab)
        val a = lab.green.toDouble()
        val b = lab.blue.toDouble()
        return hypot(a, b) to (Math.toDegrees(atan2(b, a)) + 360) % 360
    }

    @Test
    fun onlyTheErrorColourIsRed_SPEC_R114() {
        // The conversion matches the spec's measurements: Capsule light's taken colour at hue 4.7.
        assertEquals(4.7, chromaHue(coloursOf(Palette.CAPSULE, false).taken).second, 0.1)
        for (look in looks) {
            for ((name, colour) in named(coloursOf(look.first, look.second))) {
                val (chroma, hue) = chromaHue(colour)
                val red = chroma > 0.04 && hue in 15.0..40.0
                assertEquals(name == "Error", red, "$look $name: hue ${"%.1f".format(hue)}, chroma ${"%.3f".format(chroma)}")
            }
        }
    }

    /**
     * SPEC:R122's role table, as SPEC.md holds it. If the spec's table changes, this copy and [expectedRoles] change
     * with it, in the same pull request.
     */
    private val roleTable = listOf(
        listOf("Material 3 role", "Takes"),
        listOf("primary, onPrimary", "main, on main"),
        listOf("secondary, onSecondary", "link, card"),
        listOf("tertiary, onTertiary", "accent, whichever of text and card reads better on it (the accent still carries no text, R114)"),
        listOf("background, onBackground", "ground, text"),
        listOf("surface and every surfaceContainer, surfaceBright, surfaceDim, surfaceTint", "card"),
        listOf("onSurface, onSurfaceVariant, surfaceVariant", "text, muted text, ground"),
        listOf("primaryContainer, secondaryContainer, tertiaryContainer", "the three containers"),
        listOf("onPrimaryContainer, onSecondaryContainer, onTertiaryContainer", "text"),
        listOf("inverseSurface, inverseOnSurface, inversePrimary", "text, card, the other mode's link"),
        listOf("outline, outlineVariant", "muted text, line"),
        listOf("error, onError, errorContainer, onErrorContainer", "error, card, card, error"),
        listOf("scrim", "text"),
    )

    @Test
    fun theRoleTableIsTheSpecs_SPEC_R122() {
        assertEquals(roleTable, Spec.table("R122", listOf("Material 3 role", "Takes")))
    }

    /** Every Material 3 colour role and what it takes, as [roleTable] says; then the fixed roles the table does not name. */
    private fun expectedRoles(c: MedicAllColours, otherModeLink: Color, onAccent: Color): Map<String, Color> = mapOf(
        "primary" to c.main, "onPrimary" to c.onMain,
        "secondary" to c.link, "onSecondary" to c.card,
        "tertiary" to c.accent, "onTertiary" to onAccent,
        "background" to c.ground, "onBackground" to c.text,
        "surface" to c.card, "surfaceContainer" to c.card, "surfaceContainerHigh" to c.card,
        "surfaceContainerHighest" to c.card, "surfaceContainerLow" to c.card, "surfaceContainerLowest" to c.card,
        "surfaceBright" to c.card, "surfaceDim" to c.card, "surfaceTint" to c.card,
        "onSurface" to c.text, "onSurfaceVariant" to c.mutedText, "surfaceVariant" to c.ground,
        "primaryContainer" to c.primaryContainer, "secondaryContainer" to c.secondaryContainer,
        "tertiaryContainer" to c.tertiaryContainer,
        "onPrimaryContainer" to c.text, "onSecondaryContainer" to c.text, "onTertiaryContainer" to c.text,
        "inverseSurface" to c.text, "inverseOnSurface" to c.card, "inversePrimary" to otherModeLink,
        "outline" to c.mutedText, "outlineVariant" to c.line,
        "error" to c.error, "onError" to c.card, "errorContainer" to c.card, "onErrorContainer" to c.error,
        "scrim" to c.text,
        // Not in R122's table: Material 3 1.9's fixed roles follow its container rule (see colorSchemeOf).
        "primaryFixed" to c.primaryContainer, "primaryFixedDim" to c.primaryContainer,
        "onPrimaryFixed" to c.text, "onPrimaryFixedVariant" to c.text,
        "secondaryFixed" to c.secondaryContainer, "secondaryFixedDim" to c.secondaryContainer,
        "onSecondaryFixed" to c.text, "onSecondaryFixedVariant" to c.text,
        "tertiaryFixed" to c.tertiaryContainer, "tertiaryFixedDim" to c.tertiaryContainer,
        "onTertiaryFixed" to c.text, "onTertiaryFixedVariant" to c.text,
    )

    /** Every colour role a [ColorScheme] has, read from the class, so a role Material adds later fails here until it is set. */
    private fun roles(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType && it.name.startsWith("get") }
            .associate { m ->
                val name = m.name.removePrefix("get").substringBefore('-').replaceFirstChar { it.lowercase() }
                name to Color((m.invoke(scheme) as Long).toULong())
            }

    @Test
    fun everyMaterialRoleIsSetFromTheSpec_SPEC_R122() {
        for ((palette, dark) in looks) {
            val c = coloursOf(palette, dark)
            val onAccent = if (contrast(c.card, c.accent) > contrast(c.text, c.accent)) c.card else c.text
            val expected = expectedRoles(c, coloursOf(palette, !dark).link, onAccent)
            val actual = roles(colorSchemeOf(palette, dark))
            assertEquals(48, actual.size, "Material 3 1.9 has 48 colour roles: ${actual.keys}")
            assertEquals(expected.keys, actual.keys, "every role is set, none left to Material")
            for ((role, colour) in expected) assertEquals(colour, actual.getValue(role), "$palette dark=$dark $role")
        }
    }

    @Test
    fun onTertiaryIsTheBetterOfTextAndCard_SPEC_R122() {
        for ((palette, dark) in looks) {
            val c = coloursOf(palette, dark)
            val on = colorSchemeOf(palette, dark).onTertiary
            val other = if (on == c.text) c.card else c.text
            assertTrue(on == c.text || on == c.card)
            assertTrue(contrast(on, c.accent) >= contrast(other, c.accent), "$palette dark=$dark")
        }
    }

    @Test
    fun contrastIsWcags() {
        assertEquals(21f, contrast(Spec.hexColour("#000000"), Spec.hexColour("#FFFFFF")), 0.01f)
        assertEquals(1f, contrast(Spec.hexColour("#777777"), Spec.hexColour("#777777")), 0.001f)
        // R114's evidence: text on ground 12.9 to 14.7:1; Albarello light's is in that range.
        val a = coloursOf(Palette.ALBARELLO, dark = false)
        assertTrue(contrast(a.text, a.ground) in 12.9f..14.7f, "${contrast(a.text, a.ground)}")
    }

    @Test
    fun scaleStepsRunFrom35PercentToFull_SPEC_R121() {
        for ((palette, dark) in looks) {
            val c = coloursOf(palette, dark)
            for (i in 0 until MedicAllColours.SCALE_COLOURS) {
                assertEquals(c.scale[i], c.scaleStep(i, 5, 5))
                val lowest = c.scaleStep(i, 1, 5)
                // The lowest step sits between the card and the colour, nearer the card.
                assertTrue(contrast(lowest, c.card) < contrast(c.scale[i], c.card), "$palette dark=$dark scale $i")
                assertTrue(lowest != c.card)
            }
            // Past three trackers the colours repeat (with another marker shape).
            assertEquals(c.scaleStep(0, 3, 5), c.scaleStep(3, 3, 5))
        }
    }

    @Test
    fun aNewProfileAndALockScreenOpenInAlbarello_SPEC_R114() {
        assertEquals(Palette.ALBARELLO, Palette.DEFAULT)
        assertEquals(Palette.ALBARELLO, com.hazia.medicall.core.look.DeviceLook.DEFAULT.palette)
    }
}
