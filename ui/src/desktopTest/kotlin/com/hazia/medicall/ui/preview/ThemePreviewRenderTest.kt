package com.hazia.medicall.ui.preview

import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import com.hazia.medicall.core.look.Palette
import com.hazia.medicall.ui.theme.ROOT
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The debug preview draws every palette in both modes with the bundled fonts and icons. Each render is written to
 * ui/build/theme-preview/ for a person to look at; the test checks that every one drew something.
 */
class ThemePreviewRenderTest {

    @Test
    fun everyPaletteAndModeRenders_SPEC_R114() {
        for ((palette, dark) in PREVIEW_LOOKS) render(palette, dark, PREVIEW_WIDTH.value, 1f, 2300, "")
    }

    /**
     * SPEC:R115's hardest case: 200% system font size on a 320 dp column, in one light and one dark palette, written
     * as *-320dp-200.png for a person to check that nothing is cut or overlaps. A phone check still decides (R09).
     */
    @Test
    fun theLargestTextFitsANarrowPhone_SPEC_R115() {
        render(Palette.ALBARELLO, false, 320f, 2f, 9000, "-320dp-200")
        render(Palette.CAPSULE, true, 320f, 2f, 9000, "-320dp-200")
    }

    private fun render(palette: Palette, dark: Boolean, widthDp: Float, fontScale: Float, heightDp: Int, suffix: String) {
        val out = File(ROOT, "ui/build/theme-preview").apply { mkdirs() }
        val density = 2f
        run {
            val width = (widthDp * density).toInt()
            val scene = ImageComposeScene(
                width = width, height = (heightDp * density).toInt(), density = Density(density, fontScale),
            )
            try {
                scene.setContent { Box(Modifier.width(widthDp.dp)) { PaletteSample(palette, dark, focusSave = true) } }
                // Resource fonts and icons load over a few frames.
                repeat(6) { scene.render(it * 50_000_000L) }
                val image: Image = scene.render(400_000_000L)
                val bytes = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
                File(out, "${palette.id}-${if (dark) "dark" else "light"}$suffix.png").writeBytes(bytes)
                assertTrue(bytes.size > 20_000, "$palette dark=$dark drew a page")
            } finally {
                scene.close()
            }
        }
    }
}
