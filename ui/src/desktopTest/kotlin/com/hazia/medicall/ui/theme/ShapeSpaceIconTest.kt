package com.hazia.medicall.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shapes, space, icons and licence notices (SPEC:R116, SPEC:R117, SPEC:R115). */
class ShapeSpaceIconTest {

    @Test
    fun everyShapeSizeHas10dpCorners_SPEC_R116() {
        // Every public size, read from the class; internal ones (Material 3 1.9's expressive sizes) no public component reads.
        val sizes = Shapes::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == CornerBasedShape::class.java && it.name.startsWith("get") && '$' !in it.name }
            .associate { it.name.removePrefix("get") to it.invoke(MedicAllShapes) as CornerBasedShape }
        assertEquals(setOf("ExtraSmall", "Small", "Medium", "Large", "ExtraLarge"), sizes.keys)
        for ((name, shape) in sizes) assertEquals(RoundedCornerShape(10.dp), shape, name)
        assertEquals(10.dp, CORNER)
    }

    @Test
    fun spaceFollowsTheSpec_SPEC_R116() {
        assertEquals(4.dp, Space.grid)
        assertEquals(20.dp, Space.screenMargin)
        assertEquals(16.dp, Space.cardPadding)
        assertEquals(48.dp, Space.target)
        assertEquals(12.dp, Space.bandVertical)
        assertEquals(20.dp, Space.bandHorizontal)
        // R116's Values: the inner accent line 3 dp, 4 dp from the band; the outer 1 dp, 2 dp beyond the inner.
        assertEquals(listOf(3.dp, 4.dp, 1.dp, 2.dp), listOf(Space.ruleInner, Space.ruleInnerGap, Space.ruleOuter, Space.ruleOuterGap))
        // R122: a 3 dp focus ring 2 dp off its control.
        assertEquals(listOf(3.dp, 2.dp), listOf(Space.focusRing, Space.focusRingGap))
        // Spacing sits on the 4 dp grid.
        for (gap in listOf(Space.screenMargin, Space.cardPadding, Space.target, Space.bandVertical, Space.bandHorizontal, Space.steps(3))) {
            assertEquals(0f, gap.value % Space.grid.value, "$gap")
        }
    }

    private val drawables = File(ROOT, "ui/src/commonMain/composeResources/drawable")

    @Test
    fun everyCustomIconIsDrawn_SPEC_R117() {
        val designed = File(ROOT, "docs/design/icons").list()!!.filter { it.endsWith(".svg") }.map { it.removeSuffix(".svg") }.toSet()
        assertEquals(designed, MedicAllIcons.Own.all.keys)
        assertEquals(designed.map { "mc_" + it.replace('-', '_') + ".xml" }.toSet(), drawables.list()!!.filter { it.startsWith("mc_") }.toSet())
        assertEquals(
            MedicAllIcons.Phosphor.all.keys.map { "ph_" + it.replace('-', '_') + ".xml" }.toSet(),
            drawables.list()!!.filter { it.startsWith("ph_") }.toSet(),
        )
    }

    @Test
    fun iconsAreMasksTheThemeColours_SPEC_R117_SPEC_R93() {
        for (file in drawables.listFiles()!!) {
            val colours = Regex("""android:(?:fill|stroke)Color="([^"]*)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()
            assertEquals(setOf("#FF000000"), colours, "${file.name} is drawn in opaque black alone, which Icon replaces")
            assertTrue("width=\"24dp\"" in file.readText(), "${file.name} is on the 24 dp grid")
        }
    }

    @Test
    fun everyBundledWorkHasItsLicenceNotice_SPEC_R115_SPEC_R117() {
        val resources = File(ROOT, "ui/src/commonMain/composeResources")
        assertEquals(listOf("Fraunces", "Atkinson Hyperlegible Next", "Phosphor Icons"), ThirdPartyNotices.all.map { it.work })
        for (notice in ThirdPartyNotices.all) {
            val text = File(resources, notice.path).readText()
            when (notice.licence) {
                "SIL Open Font License 1.1" -> {
                    assertTrue("SIL OPEN FONT LICENSE Version 1.1" in text, notice.work)
                    assertTrue(notice.work in text, "the OFL text names ${notice.work}'s copyright")
                }
                "MIT License" -> assertTrue(text.startsWith("MIT License") && "Phosphor Icons" in text, notice.work)
                else -> error("unexpected licence ${notice.licence}")
            }
        }
        val licences = File(resources, "files/licences").list()!!.toSet()
        assertEquals(ThirdPartyNotices.all.map { it.path.substringAfterLast('/') }.toSet(), licences)
    }
}
