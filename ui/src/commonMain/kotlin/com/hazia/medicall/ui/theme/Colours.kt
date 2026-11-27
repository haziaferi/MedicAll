package com.hazia.medicall.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.hazia.medicall.core.look.Palette

/**
 * Every colour of one palette in one mode, as SPEC.md records it: the palette's own colours (SPEC:R114), its state
 * colours, containers and disabled fill (SPEC:R122) and its three scale colours (SPEC:R121). This is the only place
 * MedicAll's colours are written (SPEC:R93); screens read them through [MedicAllTheme.colours] and Material's
 * [androidx.compose.material3.ColorScheme], which [colorSchemeOf] builds from them.
 */
@Immutable
data class MedicAllColours(
    val palette: Palette,
    val dark: Boolean,
    // SPEC:R114
    val ground: Color,
    val card: Color,
    val text: Color,
    val mutedText: Color,
    val main: Color,
    val onMain: Color,
    /** The double rule under the band; ornament only, it never carries text. */
    val accent: Color,
    /** A taken dose. */
    val taken: Color,
    /** Dividers and card edges only; a control's outline takes [mutedText]. */
    val line: Color,
    // SPEC:R122
    /** Links, and a secondary button's label and outline. */
    val link: Color,
    /** A 3 dp focus ring drawn 2 dp off its control ([Space.focusRing], [Space.focusRingGap]). */
    val focus: Color,
    /** A switch's on track, under a thumb in the [card] colour. */
    val switchOn: Color,
    /** Disabled labels, outlines and icons. */
    val disabled: Color,
    /** A disabled filled button, which keeps its shape. */
    val disabledFill: Color,
    /** Only real errors, always with the information icon and words. */
    val error: Color,
    /** The selected navigation item, with a filled icon and its label at weight 600. */
    val primaryContainer: Color,
    /** A selected chip or tab, with a check, and a tonal button. */
    val secondaryContainer: Color,
    /** A third container where Material asks for one. */
    val tertiaryContainer: Color,
    // SPEC:R121
    /** The three scale colours, one per tracker; past three, the colours repeat with another marker shape. */
    val scale: List<Color>,
) {
    init {
        require(scale.size == SCALE_COLOURS) { "a palette has $SCALE_COLOURS scale colours" }
    }

    /**
     * Step [step] of [steps] (from 1, the lowest) of scale colour [index] (SPEC:R121): the colour mixed with the
     * [card] in OKLab, from 35% of it at the lowest step to the full colour at the highest. Compose's [lerp] mixes
     * in OKLab.
     */
    fun scaleStep(index: Int, step: Int, steps: Int): Color {
        require(steps >= 1 && step in 1..steps) { "step $step of $steps" }
        val share = if (steps == 1) 1f else SCALE_LOWEST + (1f - SCALE_LOWEST) * (step - 1) / (steps - 1)
        return lerp(card, scale[index % SCALE_COLOURS], share)
    }

    companion object {
        const val SCALE_COLOURS = 3

        /** The lowest step's share of its scale colour (SPEC:R121). */
        const val SCALE_LOWEST = 0.35f
    }
}

/** The colours of [palette] in light or [dark] mode. */
fun coloursOf(palette: Palette, dark: Boolean): MedicAllColours = when (palette) {
    Palette.ALBARELLO -> if (dark) AlbarelloDark else AlbarelloLight
    Palette.PLUM_AND_OLIVE -> if (dark) PlumAndOliveDark else PlumAndOliveLight
    Palette.CAPSULE -> if (dark) CapsuleDark else CapsuleLight
    Palette.MURANO -> if (dark) MuranoDark else MuranoLight
}

private val AlbarelloLight = MedicAllColours(
    Palette.ALBARELLO, dark = false,
    ground = Color(0xFFF5F4F0), card = Color(0xFFFCFBF8), text = Color(0xFF1B2A4A), mutedText = Color(0xFF455272),
    main = Color(0xFF2747A0), onMain = Color(0xFFFFFFFF), accent = Color(0xFFBF8103), taken = Color(0xFF2F7D6B),
    line = Color(0xFFDCD8CC),
    link = Color(0xFF1334C9), focus = Color(0xFF1B44EB), switchOn = Color(0xFF497AFF), disabled = Color(0xFF7281A4),
    disabledFill = Color(0xFFFEF4DE), error = Color(0xFF830021),
    primaryContainer = Color(0xFFC2D5FE), secondaryContainer = Color(0xFFE3ECFF), tertiaryContainer = Color(0xFFFFEACE),
    scale = listOf(Color(0xFFB04700), Color(0xFF7D6DE0), Color(0xFFB54F97)),
)

private val AlbarelloDark = MedicAllColours(
    Palette.ALBARELLO, dark = true,
    ground = Color(0xFF121827), card = Color(0xFF1A2236), text = Color(0xFFE6E9F0), mutedText = Color(0xFFB4BCD0),
    main = Color(0xFF94ABF2), onMain = Color(0xFF121827), accent = Color(0xFFE2B04A), taken = Color(0xFF6FC0AA),
    line = Color(0xFF2C3650),
    link = Color(0xFF9CB4FE), focus = Color(0xFF4158FF), switchOn = Color(0xFF6E88E1), disabled = Color(0xFF657E7F),
    disabledFill = Color(0xFF303742), error = Color(0xFFFFB097),
    primaryContainer = Color(0xFF313D64), secondaryContainer = Color(0xFF424859), tertiaryContainer = Color(0xFF2A2110),
    scale = listOf(Color(0xFF01A2A7), Color(0xFF998419), Color(0xFF5C7EDE)),
)

private val PlumAndOliveLight = MedicAllColours(
    Palette.PLUM_AND_OLIVE, dark = false,
    ground = Color(0xFFF1EEEA), card = Color(0xFFFBF9F6), text = Color(0xFF2A1D2B), mutedText = Color(0xFF5A4B5A),
    main = Color(0xFF6B3463), onMain = Color(0xFFFFFFFF), accent = Color(0xFF4F6FA8), taken = Color(0xFF8A8530),
    line = Color(0xFFDDD5CF),
    link = Color(0xFF67005E), focus = Color(0xFF87007B), switchOn = Color(0xFFB13DA3), disabled = Color(0xFF848F8B),
    disabledFill = Color(0xFFFFF1DC), error = Color(0xFF58010E),
    primaryContainer = Color(0xFFFFDAF8), secondaryContainer = Color(0xFFD2BFCE), tertiaryContainer = Color(0xFFB8D2FF),
    scale = listOf(Color(0xFF355097), Color(0xFF894610), Color(0xFF007D73)),
)

private val PlumAndOliveDark = MedicAllColours(
    Palette.PLUM_AND_OLIVE, dark = true,
    ground = Color(0xFF1A1519), card = Color(0xFF241D23), text = Color(0xFFEDE6EC), mutedText = Color(0xFFC2B5C0),
    main = Color(0xFFD49BC8), onMain = Color(0xFF1A1519), accent = Color(0xFF9DB4E0), taken = Color(0xFFC9C46A),
    line = Color(0xFF3A3039),
    link = Color(0xFFF8A1E7), focus = Color(0xFFD224BD), switchOn = Color(0xFFB059A1), disabled = Color(0xFF637379),
    disabledFill = Color(0xFF432C3B), error = Color(0xFFFEAEA8),
    primaryContainer = Color(0xFF4D2A4A), secondaryContainer = Color(0xFF584654), tertiaryContainer = Color(0xFF0D1A35),
    scale = listOf(Color(0xFF968725), Color(0xFF00998C), Color(0xFF7A79CC)),
)

private val CapsuleLight = MedicAllColours(
    Palette.CAPSULE, dark = false,
    ground = Color(0xFFEEF1F3), card = Color(0xFFFFFFFF), text = Color(0xFF172026), mutedText = Color(0xFF45525A),
    main = Color(0xFF0E5A6B), onMain = Color(0xFFFFFFFF), accent = Color(0xFFE0A383), taken = Color(0xFFD9547C),
    line = Color(0xFFD5DBDF),
    link = Color(0xFF014351), focus = Color(0xFF0096B3), switchOn = Color(0xFF047288), disabled = Color(0xFF83887A),
    disabledFill = Color(0xFFFEF7E5), error = Color(0xFF910224),
    primaryContainer = Color(0xFFA8E9FC), secondaryContainer = Color(0xFFAEC9D1), tertiaryContainer = Color(0xFFFFE0D0),
    scale = listOf(Color(0xFFF66700), Color(0xFF0F8DFF), Color(0xFFC648AD)),
)

private val CapsuleDark = MedicAllColours(
    Palette.CAPSULE, dark = true,
    ground = Color(0xFF10171B), card = Color(0xFF18232A), text = Color(0xFFDDE4E7), mutedText = Color(0xFFAEBBC2),
    main = Color(0xFF5DB8CB), onMain = Color(0xFF10171B), accent = Color(0xFFE8B597), taken = Color(0xFFE8638D),
    line = Color(0xFF2A3740),
    link = Color(0xFF0ACFED), focus = Color(0xFF04DFFF), switchOn = Color(0xFF0BC1DD), disabled = Color(0xFF616558),
    disabledFill = Color(0xFF152738), error = Color(0xFFC8715D),
    primaryContainer = Color(0xFF003F52), secondaryContainer = Color(0xFF354C51), tertiaryContainer = Color(0xFF2E1C11),
    scale = listOf(Color(0xFF0091BC), Color(0xFFB39105), Color(0xFFAE78FF)),
)

private val MuranoLight = MedicAllColours(
    Palette.MURANO, dark = false,
    ground = Color(0xFFF7F3F4), card = Color(0xFFFFFFFF), text = Color(0xFF22262A), mutedText = Color(0xFF4C5359),
    main = Color(0xFF0B5E48), onMain = Color(0xFFFFFFFF), accent = Color(0xFFD2558F), taken = Color(0xFF3A5BA4),
    line = Color(0xFFE4DCDE),
    link = Color(0xFF005A44), focus = Color(0xFF009D90), switchOn = Color(0xFF037C5F), disabled = Color(0xFF91827E),
    disabledFill = Color(0xFFFEF7E5), error = Color(0xFF680017),
    primaryContainer = Color(0xFFA7DBC0), secondaryContainer = Color(0xFF8EBAA9), tertiaryContainer = Color(0xFFE7B5CE),
    scale = listOf(Color(0xFF3778EF), Color(0xFF00A1C2), Color(0xFFC26A02)),
)

private val MuranoDark = MedicAllColours(
    Palette.MURANO, dark = true,
    ground = Color(0xFF0F1A17), card = Color(0xFF172521), text = Color(0xFFE0E6E2), mutedText = Color(0xFFB5C2BC),
    main = Color(0xFF5CC4A2), onMain = Color(0xFF0F1A17), accent = Color(0xFFEE8DB8), taken = Color(0xFF6B90DE),
    line = Color(0xFF26372F),
    link = Color(0xFF0CD9A9), focus = Color(0xFF00FFEB), switchOn = Color(0xFF0EAF88), disabled = Color(0xFF6C7D7B),
    disabledFill = Color(0xFF1C3D39), error = Color(0xFFC77B73),
    primaryContainer = Color(0xFF124026), secondaryContainer = Color(0xFF304E43), tertiaryContainer = Color(0xFF512A46),
    scale = listOf(Color(0xFFCDBE54), Color(0xFF56DFDF), Color(0xFF83B9FF)),
)
