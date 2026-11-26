package com.hazia.medicall.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hazia.medicall.core.look.Palette

/**
 * Material 3's colour scheme for [palette] in light or [dark] mode, every role set as SPEC:R122's role table says, so
 * Compose draws none in Material's own purple and red. Dynamic colour is never used: the palette is the person's
 * choice (SPEC:R114).
 *
 * Material 3 1.9's twelve "fixed" roles, used by its expressive components, take R122's containers too: each fixed
 * fill is its container, and the text on it is the text colour.
 */
fun colorSchemeOf(palette: Palette, dark: Boolean): ColorScheme {
    val c = coloursOf(palette, dark)
    val otherModeLink = coloursOf(palette, !dark).link
    return ColorScheme(
        primary = c.main,
        onPrimary = c.onMain,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.text,
        inversePrimary = otherModeLink,
        secondary = c.link,
        onSecondary = c.card,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.text,
        tertiary = c.accent,
        onTertiary = betterOn(c.accent, c.text, c.card),
        tertiaryContainer = c.tertiaryContainer,
        onTertiaryContainer = c.text,
        background = c.ground,
        onBackground = c.text,
        surface = c.card,
        onSurface = c.text,
        surfaceVariant = c.ground,
        onSurfaceVariant = c.mutedText,
        surfaceTint = c.card,
        inverseSurface = c.text,
        inverseOnSurface = c.card,
        error = c.error,
        onError = c.card,
        errorContainer = c.card,
        onErrorContainer = c.error,
        outline = c.mutedText,
        outlineVariant = c.line,
        scrim = c.text,
        surfaceBright = c.card,
        surfaceDim = c.card,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        surfaceContainerHighest = c.card,
        surfaceContainerLow = c.card,
        surfaceContainerLowest = c.card,
        primaryFixed = c.primaryContainer,
        primaryFixedDim = c.primaryContainer,
        onPrimaryFixed = c.text,
        onPrimaryFixedVariant = c.text,
        secondaryFixed = c.secondaryContainer,
        secondaryFixedDim = c.secondaryContainer,
        onSecondaryFixed = c.text,
        onSecondaryFixedVariant = c.text,
        tertiaryFixed = c.tertiaryContainer,
        tertiaryFixedDim = c.tertiaryContainer,
        onTertiaryFixed = c.text,
        onTertiaryFixedVariant = c.text,
    )
}

/** Whichever of [first] and [second] has the higher WCAG contrast on [fill] (SPEC:R122's onTertiary); [first] on a tie. */
internal fun betterOn(fill: Color, first: Color, second: Color): Color =
    if (contrast(second, fill) > contrast(first, fill)) second else first

/** WCAG 2 contrast ratio of two opaque colours, 1 to 21. */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
