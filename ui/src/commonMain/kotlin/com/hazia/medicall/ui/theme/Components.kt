package com.hazia.medicall.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The colours SPEC:R122 gives components where Material's own defaults take another role: Material draws a disabled
 * button, chip or text field in its text colour at low alpha, an unselected chip's outline in the line colour, a
 * switch's on track in the main colour and the selected navigation item on the secondary container. Screens pass
 * these to the components, and put every focusable control but a text field in a [FocusRingTarget]; every other component reads
 * the colour scheme as [colorSchemeOf] sets it.
 */
object MedicAllComponents {
    /** A button's shape: 10 dp corners (SPEC:R116). Material's buttons are full pills by default, outside its shape scale. */
    val buttonShape: Shape = RoundedCornerShape(CORNER)

    /** A primary (filled) button: the main colour; disabled, the disabled fill under a disabled label, keeping its shape. */
    @Composable
    fun buttonColours(): ButtonColors = MedicAllTheme.colours.let { c ->
        ButtonDefaults.buttonColors(
            containerColor = c.main,
            contentColor = c.onMain,
            disabledContainerColor = c.disabledFill,
            disabledContentColor = c.disabled,
        )
    }

    /** A tonal button: the secondary container under the text colour. */
    @Composable
    fun tonalButtonColours(): ButtonColors = MedicAllTheme.colours.let { c ->
        ButtonDefaults.filledTonalButtonColors(
            containerColor = c.secondaryContainer,
            contentColor = c.text,
            disabledContainerColor = c.disabledFill,
            disabledContentColor = c.disabled,
        )
    }

    /** A secondary (outlined) button's label: the link colour; disabled, the disabled colour. */
    @Composable
    fun secondaryButtonColours(): ButtonColors = MedicAllTheme.colours.let { c ->
        ButtonDefaults.outlinedButtonColors(contentColor = c.link, disabledContentColor = c.disabled)
    }

    /** A secondary button's outline, in its label's colour. */
    @Composable
    fun secondaryButtonBorder(enabled: Boolean): BorderStroke = MedicAllTheme.colours.let { c ->
        BorderStroke(1.dp, if (enabled) c.link else c.disabled)
    }

    /**
     * A switch (SPEC:R114, SPEC:R122): on, the switch-on track under a thumb in the card colour; off, a track outlined
     * in the muted text colour; disabled, the disabled colour.
     */
    @Composable
    fun switchColours(): SwitchColors = MedicAllTheme.colours.let { c ->
        SwitchDefaults.colors(
            checkedThumbColor = c.card,
            checkedTrackColor = c.switchOn,
            checkedBorderColor = c.switchOn,
            checkedIconColor = c.switchOn,
            uncheckedThumbColor = c.mutedText,
            uncheckedTrackColor = c.card,
            uncheckedBorderColor = c.mutedText,
            uncheckedIconColor = c.card,
            disabledCheckedThumbColor = c.card,
            disabledCheckedTrackColor = c.disabled,
            disabledCheckedBorderColor = c.disabled,
            disabledCheckedIconColor = c.disabled,
            disabledUncheckedThumbColor = c.disabled,
            disabledUncheckedTrackColor = c.card,
            disabledUncheckedBorderColor = c.disabled,
            disabledUncheckedIconColor = c.card,
        )
    }

    /**
     * A filter chip (SPEC:R122): selected, on the secondary container in the text colour; unselected, outlined in the
     * muted text colour like every control (SPEC:R114), where Material takes the line colour; disabled, the disabled
     * colour, and a selected one on the disabled fill. Pass with [filterChipBorder].
     */
    @Composable
    fun filterChipColours(): SelectableChipColors = MedicAllTheme.colours.let { c ->
        FilterChipDefaults.filterChipColors(
            selectedContainerColor = c.secondaryContainer,
            selectedLabelColor = c.text,
            selectedLeadingIconColor = c.text,
            selectedTrailingIconColor = c.text,
            disabledLabelColor = c.disabled,
            disabledLeadingIconColor = c.disabled,
            disabledTrailingIconColor = c.disabled,
            disabledSelectedContainerColor = c.disabledFill,
        )
    }

    /** A filter chip's outline: the muted text colour, the disabled colour when disabled; a selected chip has none. */
    @Composable
    fun filterChipBorder(enabled: Boolean, selected: Boolean): BorderStroke = MedicAllTheme.colours.let { c ->
        FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = c.mutedText,
            disabledBorderColor = c.disabled,
        )
    }

    /**
     * An outlined text field (SPEC:R114, SPEC:R122): outlined in the muted text colour; disabled, its text, label,
     * outline, icons and supporting text in the disabled colour, where Material draws its text colour at 38% alpha.
     * Focused, Material's own outline (the main colour, 2 dp) shows the focus, so a text field takes no [focusRing].
     */
    @Composable
    fun textFieldColours(): TextFieldColors = MedicAllTheme.colours.let { c ->
        OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = c.mutedText,
            disabledTextColor = c.disabled,
            disabledBorderColor = c.disabled,
            disabledLabelColor = c.disabled,
            disabledPlaceholderColor = c.disabled,
            disabledLeadingIconColor = c.disabled,
            disabledTrailingIconColor = c.disabled,
            disabledSupportingTextColor = c.disabled,
            disabledPrefixColor = c.disabled,
            disabledSuffixColor = c.disabled,
        )
    }

    /**
     * A navigation item (SPEC:R117, SPEC:R122): selected, on the primary container in the text colour, its label in
     * [MedicAllType.navigationLabel] and its icon filled (SPEC:R117: Phosphor's fill weight, or a custom icon's filled
     * twin, added with phase 2b's navigation destinations); unselected, in the muted text colour.
     */
    @Composable
    fun navigationItemColours(): NavigationBarItemColors = MedicAllTheme.colours.let { c ->
        NavigationBarItemDefaults.colors(
            selectedIconColor = c.text,
            selectedTextColor = c.text,
            indicatorColor = c.primaryContainer,
            unselectedIconColor = c.mutedText,
            unselectedTextColor = c.mutedText,
            disabledIconColor = c.disabled,
            disabledTextColor = c.disabled,
        )
    }
}
