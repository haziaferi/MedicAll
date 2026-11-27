package com.hazia.medicall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corners are 10 dp everywhere but on pills and switches (SPEC:R116). */
val CORNER: Dp = 10.dp

/** A pill (a taken-dose pill, a badge), fully rounded. Switches keep Material's own track. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * Every size of Material 3's shape scale at [CORNER], so no component keeps Material's own corners. Material 3 1.9's
 * three expressive sizes are internal to it, used by none of its public components, and cannot be set.
 */
val MedicAllShapes: Shapes = RoundedCornerShape(CORNER).let { corner ->
    Shapes(extraSmall = corner, small = corner, medium = corner, large = corner, extraLarge = corner)
}

/**
 * Space and sizes (SPEC:R116, SPEC:R122). Spacing follows a 4 dp grid: [grid] steps, 20 dp screen margins, 16 dp card
 * padding and 48 dp targets for primary actions. The band's padding and its accent double rule, and the focus ring,
 * are drawn to these sizes.
 */
object Space {
    val grid: Dp = 4.dp

    /** [n] steps of the 4 dp grid. */
    fun steps(n: Int): Dp = grid * n

    val screenMargin: Dp = 20.dp
    val cardPadding: Dp = 16.dp

    /** The least size of a primary action's touch target. */
    val target: Dp = 48.dp

    /** The band's padding: 12 dp above and below its text, 20 dp at the sides. */
    val bandVertical: Dp = 12.dp
    val bandHorizontal: Dp = 20.dp

    /** The accent's double rule under the band: a 3 dp line 4 dp below the band, then a 1 dp line 2 dp beyond it. */
    val ruleInner: Dp = 3.dp
    val ruleInnerGap: Dp = 4.dp
    val ruleOuter: Dp = 1.dp
    val ruleOuterGap: Dp = 2.dp

    /** The focus ring: 3 dp wide, 2 dp off its control (SPEC:R122). */
    val focusRing: Dp = 3.dp
    val focusRingGap: Dp = 2.dp

    /** Icons are drawn on a 24 dp grid (SPEC:R117). */
    val icon: Dp = 24.dp
}
