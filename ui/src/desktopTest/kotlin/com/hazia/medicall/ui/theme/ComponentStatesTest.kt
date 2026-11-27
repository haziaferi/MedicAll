package com.hazia.medicall.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.material3.Button
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hazia.medicall.core.look.Palette
import org.jetbrains.skia.Bitmap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** SPEC:R122's disabled and focus states, and SPEC:R114's control outline, on the components Material colours itself. */
class ComponentStatesTest {

    private val looks = Palette.entries.flatMap { listOf(it to false, it to true) }

    private class Captured(val chip: SelectableChipColors, val border: BorderStroke, val disabledBorder: BorderStroke, val field: TextFieldColors)

    @Test
    fun disabledChipsAndTextFieldsTakeTheDisabledColour_SPEC_R122() {
        for ((palette, dark) in looks) {
            val c = coloursOf(palette, dark)
            var captured: Captured? = null
            val scene = ImageComposeScene(100, 100)
            try {
                scene.setContent {
                    MedicAllTheme(palette, dark) {
                        captured = Captured(
                            MedicAllComponents.filterChipColours(),
                            MedicAllComponents.filterChipBorder(enabled = true, selected = false),
                            MedicAllComponents.filterChipBorder(enabled = false, selected = false),
                            MedicAllComponents.textFieldColours(),
                        )
                    }
                }
                scene.render()
            } finally {
                scene.close()
            }
            val k = assertNotNull(captured)
            val look = "$palette dark=$dark"
            // SelectableChipColors keeps its colours private in Material 3 1.9, so they are read by name.
            for (name in listOf("disabledLabelColor", "disabledLeadingIconColor", "disabledTrailingIconColor")) {
                assertEquals(c.disabled, chipColour(k.chip, name), "$look chip $name")
            }
            assertEquals(c.disabledFill, chipColour(k.chip, "disabledSelectedContainerColor"), "$look chip disabled selected")
            assertEquals(c.secondaryContainer, chipColour(k.chip, "selectedContainerColor"), "$look chip selected")
            assertEquals(c.mutedText, (k.border.brush as SolidColor).value, "$look chip outline (R114)")
            assertEquals(c.disabled, (k.disabledBorder.brush as SolidColor).value, "$look disabled chip outline")
            with(k.field) {
                listOf(
                    disabledTextColor, disabledIndicatorColor, disabledLabelColor, disabledPlaceholderColor,
                    disabledLeadingIconColor, disabledTrailingIconColor, disabledSupportingTextColor,
                    disabledPrefixColor, disabledSuffixColor,
                ).forEach { assertEquals(c.disabled, it, "$look text field") }
                assertEquals(c.mutedText, unfocusedIndicatorColor, "$look text field outline (R114)")
                assertEquals(c.main, focusedIndicatorColor, "$look focused text field outline (R114)")
            }
        }
    }

    private fun chipColour(colours: SelectableChipColors, name: String): Color {
        val field = SelectableChipColors::class.java.getDeclaredField(name).apply { isAccessible = true }
        return Color(field.getLong(colours).toULong())
    }

    /** The ring is 3 dp of the focus colour, 2 dp off the button, and only while the button has focus. */
    @Test
    fun aFocusedControlShowsTheRing_SPEC_R122() {
        for ((palette, dark) in listOf(Palette.ALBARELLO to false, Palette.CAPSULE to true)) {
            val c = coloursOf(palette, dark)
            for (focused in listOf(true, false)) {
                val (bitmap, button) = renderButton(palette, dark, focused)
                val px = 2 // density
                val x = button.center.x.toInt()
                val top = button.top.toInt()
                val ringMiddle = top - (Space.focusRingGap.value + Space.focusRing.value / 2).toInt() * px
                val gap = top - 1 * px
                val look = "$palette dark=$dark focused=$focused"
                assertClose(c.card, bitmap.getColor(x, gap), "$look gap")
                assertClose(if (focused) c.focus else c.card, bitmap.getColor(x, ringMiddle), "$look ring")
                // Nothing beyond the ring: 3 dp + 2 dp off the button, then the card again.
                assertClose(c.card, bitmap.getColor(x, top - 6 * px), "$look beyond the ring")
            }
        }
    }

    /**
     * Real use on a phone and on Windows: a mouse click focuses a button but shows no ring, Tab from it shows the ring
     * on the next one, the ring follows a palette change, and a tap beside a 40 dp button inside its 48 dp target
     * still presses it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theRingShowsForTheKeyboardNotTheMouse_SPEC_R122() {
        var palette by mutableStateOf(Palette.ALBARELLO)
        val bounds = arrayOfNulls<Rect>(2)
        val clicks = IntArray(2)
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(900, 200, density = Density(2f))
        var time = 0L
        fun frame(): Bitmap = Bitmap.makeFromImage(scene.render(50_000_000L * time++))
        fun ringAt(bitmap: Bitmap, i: Int): Int {
            val b = assertNotNull(bounds[i])
            return bitmap.getColor(b.center.x.toInt(), b.top.toInt() - 6) // 3 dp above: the ring's middle
        }
        try {
            scene.setContent {
                focusManager = LocalFocusManager.current
                MedicAllTheme(palette, dark = false) {
                    Row(Modifier.background(MedicAllTheme.colours.card).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in 0..1) FocusRingTarget { m ->
                            Button(onClick = { clicks[i]++ }, m.onGloballyPositioned { bounds[i] = it.boundsInRoot() }) { Text("Button $i") }
                        }
                    }
                }
            }
            repeat(3) { frame() }
            // Both buttons at Material's 40 dp, so each sits inside a 48 dp target.
            bounds.forEach { assertEquals(80f, assertNotNull(it).height, "a 40 dp button") }
            val first = assertNotNull(bounds[0])
            scene.sendPointerEvent(PointerEventType.Press, first.center, type = PointerType.Mouse)
            scene.sendPointerEvent(PointerEventType.Release, first.center, type = PointerType.Mouse)
            var bitmap = frame()
            assertEquals(1, clicks[0])
            assertClose(coloursOf(Palette.ALBARELLO, false).card, ringAt(bitmap, 0), "no ring after a mouse click")

            // What Tab does: the focus manager moves focus on.
            assertTrue(assertNotNull(focusManager).moveFocus(FocusDirection.Next), "focus moved")
            bitmap = frame()
            assertClose(coloursOf(Palette.ALBARELLO, false).focus, ringAt(bitmap, 1), "ring after Tab")
            assertClose(coloursOf(Palette.ALBARELLO, false).card, ringAt(bitmap, 0), "ring left the first button")

            palette = Palette.MURANO
            repeat(2) { frame() }
            assertClose(coloursOf(Palette.MURANO, false).focus, ringAt(frame(), 1), "ring after a palette change")

            val second = assertNotNull(bounds[1])
            val beside = Offset(second.center.x, second.top - 6f) // 3 dp above the button, inside its target
            scene.sendPointerEvent(PointerEventType.Press, beside, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, beside, type = PointerType.Touch)
            frame()
            assertEquals(1, clicks[1], "a tap beside the button presses it")
        } finally {
            scene.close()
        }
    }

    private fun renderButton(palette: Palette, dark: Boolean, focused: Boolean): Pair<Bitmap, Rect> {
        var bounds: Rect? = null
        val scene = ImageComposeScene(400, 200, density = Density(2f))
        try {
            scene.setContent {
                MedicAllTheme(palette, dark) {
                    val requester = remember { FocusRequester() }
                    if (focused) LaunchedEffect(Unit) { requester.requestFocus() }
                    Box(Modifier.background(MedicAllTheme.colours.card).padding(24.dp)) {
                        FocusRingTarget(Modifier.focusRequester(requester)) { m ->
                            Button(
                                onClick = {}, m.onGloballyPositioned { bounds = it.boundsInRoot() },
                                shape = MedicAllComponents.buttonShape, colors = MedicAllComponents.buttonColours(),
                            ) { Text("Save") }
                        }
                    }
                }
            }
            repeat(4) { scene.render(it * 50_000_000L) }
            val image = scene.render(300_000_000L)
            return Bitmap.makeFromImage(image) to assertNotNull(bounds)
        } finally {
            scene.close()
        }
    }

    private fun assertClose(expected: Color, argb: Int, what: String) {
        val e = expected.toArgb()
        val close = (0..2).all { abs((e shr (it * 8) and 0xFF) - (argb shr (it * 8) and 0xFF)) <= 3 }
        assertTrue(close, "$what: expected ${Integer.toHexString(e)}, drew ${Integer.toHexString(argb)}")
    }
}
