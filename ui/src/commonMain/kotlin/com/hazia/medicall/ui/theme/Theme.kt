package com.hazia.medicall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.hazia.medicall.core.look.LightDark
import com.hazia.medicall.core.look.Palette

internal val LocalMedicAllColours = staticCompositionLocalOf { coloursOf(Palette.DEFAULT, dark = false) }
private val LocalMedicAllType = staticCompositionLocalOf<MedicAllType> { error("MedicAllTheme is not set") }

/**
 * MedicAll's theme (SPEC:R93): [palette] in light or [dark] mode, Material 3 set from it in every colour role
 * ([colorSchemeOf]), every type role ([MedicAllType.material]) and every shape size ([MedicAllShapes]), with 48 dp
 * targets. A screen of a profile takes that profile's palette here; a screen that belongs to no profile takes the
 * device palette ([com.hazia.medicall.core.look.DeviceLook.palette]); [dark] comes from [isDark] (SPEC:R114).
 */
@Composable
fun MedicAllTheme(palette: Palette, dark: Boolean, content: @Composable () -> Unit) {
    val colours = coloursOf(palette, dark)
    val scheme = remember(palette, dark) { colorSchemeOf(palette, dark) }
    val type = rememberMedicAllType()
    val typography = remember(type) { type.material() }
    CompositionLocalProvider(
        LocalMedicAllColours provides colours,
        LocalMedicAllType provides type,
        LocalMinimumInteractiveComponentSize provides Space.target,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = MedicAllShapes, content = content)
    }
}

/** Whether screens are dark under the device's [lightDark] setting, following the system when it says so (SPEC:R114). */
@Composable
@ReadOnlyComposable
fun isDark(lightDark: LightDark): Boolean = lightDark.isDark(isSystemInDarkTheme())

/** The theme's own values beyond Material's: every colour of the palette, and the type by SPEC:R115's role names. */
object MedicAllTheme {
    val colours: MedicAllColours
        @Composable @ReadOnlyComposable get() = LocalMedicAllColours.current

    val type: MedicAllType
        @Composable @ReadOnlyComposable get() = LocalMedicAllType.current
}
