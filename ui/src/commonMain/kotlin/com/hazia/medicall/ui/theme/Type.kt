package com.hazia.medicall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.hazia.medicall.ui.resources.Res
import com.hazia.medicall.ui.resources.atkinson_hyperlegible_next_variable
import com.hazia.medicall.ui.resources.fraunces_variable
import org.jetbrains.compose.resources.Font

/** The two faces of SPEC:R115. */
enum class Face { FRAUNCES, ATKINSON_HYPERLEGIBLE_NEXT }

/**
 * The seven type roles of SPEC:R115, sizes and line heights in sp, so they follow the system font size. No weight is
 * above 600.
 */
enum class TypeRole(val face: Face, val sizeSp: Int, val lineHeightSp: Int, val weight: Int) {
    DISPLAY(Face.FRAUNCES, 33, 40, 500),
    TITLE(Face.FRAUNCES, 26, 32, 500),
    HEADING(Face.FRAUNCES, 21, 28, 500),
    BODY(Face.ATKINSON_HYPERLEGIBLE_NEXT, 17, 26, 400),
    LABEL(Face.ATKINSON_HYPERLEGIBLE_NEXT, 15, 20, 500),
    CAPTION(Face.ATKINSON_HYPERLEGIBLE_NEXT, 14, 20, 400),
    BUTTON(Face.ATKINSON_HYPERLEGIBLE_NEXT, 17, 24, 600),
}

/** The weights the app draws in: Atkinson Hyperlegible Next at 400, 500 and 600, Fraunces at 500 (SPEC:R115). */
val ATKINSON_WEIGHTS = listOf(400, 500, 600)
const val FRAUNCES_WEIGHT = 500

/** The selected navigation item's label weight (SPEC:R122). */
const val SELECTED_NAVIGATION_WEIGHT = 600

/**
 * MedicAll's text styles by SPEC:R115's role names. Screens take these (or Material's roles, which [material] maps to
 * them); a time, dose or count takes [tabular] on its style.
 */
@Immutable
data class MedicAllType(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
) {
    fun of(role: TypeRole): TextStyle = when (role) {
        TypeRole.DISPLAY -> display
        TypeRole.TITLE -> title
        TypeRole.HEADING -> heading
        TypeRole.BODY -> body
        TypeRole.LABEL -> label
        TypeRole.CAPTION -> caption
        TypeRole.BUTTON -> button
    }

    /**
     * A navigation item's label (SPEC:R122): Material's navigation bar draws it in labelMedium, the Label role, and
     * the selected item's label is set at weight 600. Screens pass it to the label's text.
     */
    fun navigationLabel(selected: Boolean): TextStyle =
        if (selected) label.copy(fontWeight = FontWeight(SELECTED_NAVIGATION_WEIGHT)) else label

    /**
     * Material 3's fifteen type roles, each taking one of the seven as SPEC:R115's table says, so Compose draws no
     * text in its default Roboto. Material 3 1.9's emphasized roles are internal to it, used by none of its public
     * components, and cannot be set.
     */
    fun material(): Typography {
        val m = MATERIAL_ROLES.mapValues { of(it.value) }
        return Typography(
            displayLarge = m.getValue("displayLarge"),
            displayMedium = m.getValue("displayMedium"),
            displaySmall = m.getValue("displaySmall"),
            headlineLarge = m.getValue("headlineLarge"),
            headlineMedium = m.getValue("headlineMedium"),
            headlineSmall = m.getValue("headlineSmall"),
            titleLarge = m.getValue("titleLarge"),
            titleMedium = m.getValue("titleMedium"),
            titleSmall = m.getValue("titleSmall"),
            bodyLarge = m.getValue("bodyLarge"),
            bodyMedium = m.getValue("bodyMedium"),
            bodySmall = m.getValue("bodySmall"),
            labelLarge = m.getValue("labelLarge"),
            labelMedium = m.getValue("labelMedium"),
            labelSmall = m.getValue("labelSmall"),
        )
    }

    companion object {
        /** SPEC:R115's mapping: each Material 3 type role and the role it takes. */
        val MATERIAL_ROLES: Map<String, TypeRole> = linkedMapOf(
            "displayLarge" to TypeRole.DISPLAY,
            "displayMedium" to TypeRole.DISPLAY,
            "displaySmall" to TypeRole.DISPLAY,
            "headlineLarge" to TypeRole.TITLE,
            "headlineMedium" to TypeRole.TITLE,
            "headlineSmall" to TypeRole.HEADING,
            "titleLarge" to TypeRole.TITLE,
            "titleMedium" to TypeRole.LABEL,
            "titleSmall" to TypeRole.LABEL,
            "bodyLarge" to TypeRole.BODY,
            "bodyMedium" to TypeRole.BODY,
            "bodySmall" to TypeRole.CAPTION,
            "labelLarge" to TypeRole.BUTTON,
            "labelMedium" to TypeRole.LABEL,
            "labelSmall" to TypeRole.CAPTION,
        )
    }
}

/** Tabular figures (`tnum`), for every time, dose or count, so digits line up in a column (SPEC:R115). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * The seven roles over the given faces: [fraunces] gives Fraunces at weight 500 for an optical size in points, which
 * each Fraunces role sets to its own size, as the browser did for the agreed mock-up; [atkinson] is Atkinson
 * Hyperlegible Next at 400, 500 and 600. Lines are spaced as on the web, the extra height split above and below.
 */
fun medicAllType(fraunces: (opticalSize: Int) -> FontFamily, atkinson: FontFamily): MedicAllType {
    fun style(role: TypeRole) = TextStyle(
        fontFamily = when (role.face) {
            Face.FRAUNCES -> fraunces(role.sizeSp)
            Face.ATKINSON_HYPERLEGIBLE_NEXT -> atkinson
        },
        fontWeight = FontWeight(role.weight),
        fontStyle = FontStyle.Normal,
        fontSize = role.sizeSp.sp,
        lineHeight = role.lineHeightSp.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    return MedicAllType(
        display = style(TypeRole.DISPLAY),
        title = style(TypeRole.TITLE),
        heading = style(TypeRole.HEADING),
        body = style(TypeRole.BODY),
        label = style(TypeRole.LABEL),
        caption = style(TypeRole.CAPTION),
        button = style(TypeRole.BUTTON),
    )
}

/**
 * The bundled faces (SPEC:R115), both variable fonts under the SIL Open Font License: Fraunces with its weight and
 * optical size set, since its file defaults to 900 and 9 pt, and Atkinson Hyperlegible Next 2.001 at each weight.
 */
@Composable
fun rememberMedicAllType(): MedicAllType {
    val fraunces = TypeRole.entries.filter { it.face == Face.FRAUNCES }.associate { role ->
        role.sizeSp to FontFamily(
            Font(
                Res.font.fraunces_variable,
                weight = FontWeight(FRAUNCES_WEIGHT),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(FRAUNCES_WEIGHT),
                    FontVariation.Setting("opsz", role.sizeSp.toFloat()),
                ),
            ),
        )
    }
    val atkinson = FontFamily(
        ATKINSON_WEIGHTS.map { w ->
            Font(
                Res.font.atkinson_hyperlegible_next_variable,
                weight = FontWeight(w),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(w)),
            )
        },
    )
    return remember(fraunces, atkinson) { medicAllType({ fraunces.getValue(it) }, atkinson) }
}
