package com.hazia.medicall.ui.preview

import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
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
        val out = File(ROOT, "ui/build/theme-preview").apply { mkdirs() }
        val density = 2f
        for ((palette, dark) in PREVIEW_LOOKS) {
            val width = (PREVIEW_WIDTH.value * density).toInt()
            val scene = ImageComposeScene(width = width, height = (2300 * density).toInt(), density = Density(density))
            try {
                scene.setContent { Box(Modifier.width(PREVIEW_WIDTH)) { PaletteSample(palette, dark) } }
                // Resource fonts and icons load over a few frames.
                repeat(6) { scene.render(it * 50_000_000L) }
                val image: Image = scene.render(400_000_000L)
                val bytes = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
                File(out, "${palette.id}-${if (dark) "dark" else "light"}.png").writeBytes(bytes)
                assertTrue(bytes.size > 20_000, "$palette dark=$dark drew a page")
            } finally {
                scene.close()
            }
        }
    }
}
