package com.hazia.medicall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import com.hazia.medicall.ui.preview.ThemePreview

/**
 * The theme preview (debug builds only): every palette in light and dark as the Compose theme draws them
 * (SPEC:R93, R114 to R122), for checking the theme on a phone's real densities and font sizes. Not a screen of the app.
 */
class ThemePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { ThemePreview() }
        }
    }
}
