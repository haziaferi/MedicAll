package com.hazia.medicall.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hazia.medicall.core.look.Palette
import com.hazia.medicall.ui.theme.MedicAllColours
import com.hazia.medicall.ui.theme.MedicAllComponents
import com.hazia.medicall.ui.theme.MedicAllIcons
import com.hazia.medicall.ui.theme.MedicAllTheme
import com.hazia.medicall.ui.theme.PillShape
import com.hazia.medicall.ui.theme.Space
import com.hazia.medicall.ui.theme.ThirdPartyNotices
import com.hazia.medicall.ui.theme.TypeRole
import com.hazia.medicall.ui.theme.tabular
import org.jetbrains.compose.resources.painterResource

/** Every palette in both modes, as they come out of the theme, in the order SPEC:R114 lists them. */
val PREVIEW_LOOKS: List<Pair<Palette, Boolean>> = Palette.entries.flatMap { listOf(it to false, it to true) }

private val PALETTE_NAMES = mapOf(
    Palette.ALBARELLO to "Albarello",
    Palette.PLUM_AND_OLIVE to "Plum & olive",
    Palette.CAPSULE to "Capsule",
    Palette.MURANO to "Murano",
)

/**
 * The debug build's theme preview (SPEC:R93): every palette in light and dark, drawn through the theme alone, with
 * the licence notices at the end. A tool for checking the theme, not a screen of the app; its layout and words are
 * not agreed screens (SPEC:R95).
 */
@Composable
fun ThemePreview() {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(PREVIEW_LOOKS) { (palette, dark) -> PaletteSample(palette, dark) }
        item { MedicAllTheme(Palette.DEFAULT, dark = false) { Notices() } }
    }
}

/** One palette in one mode: the band, every colour, the scales, the type, components and icons. */
@Composable
fun PaletteSample(palette: Palette, dark: Boolean) {
    MedicAllTheme(palette, dark) {
        val c = MedicAllTheme.colours
        Column(Modifier.fillMaxWidth().background(c.ground)) {
            Band("${PALETTE_NAMES.getValue(palette)} · ${if (dark) "dark" else "light"}")
            Column(
                Modifier.padding(horizontal = Space.screenMargin, vertical = Space.steps(4)),
                verticalArrangement = Arrangement.spacedBy(Space.steps(4)),
            ) {
                Section("Colours") { Swatches(c) }
                Section("Scale colours, 35% to full") { Scales(c) }
                Section("Type") { TypeSamples() }
                Section("Components") { Components() }
                Section("Icons") { IconSamples() }
            }
        }
    }
}

/** The band (SPEC:R116): the main colour, 12 by 20 dp padding, the title in the Title role, the accent double rule. */
@Composable
private fun Band(title: String) {
    val c = MedicAllTheme.colours
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MedicAllTheme.type.title,
            color = c.onMain,
            modifier = Modifier.fillMaxWidth().background(c.main)
                .padding(horizontal = Space.bandHorizontal, vertical = Space.bandVertical),
        )
        Spacer(Modifier.height(Space.ruleInnerGap))
        Box(Modifier.fillMaxWidth().height(Space.ruleInner).background(c.accent))
        Spacer(Modifier.height(Space.ruleOuterGap))
        Box(Modifier.fillMaxWidth().height(Space.ruleOuter).background(c.accent))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = MedicAllTheme.colours
    Column(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.medium)
            .border(1.dp, c.line, MaterialTheme.shapes.medium).padding(Space.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Space.steps(3)),
    ) {
        Text(title, style = MedicAllTheme.type.heading, color = c.text)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(c: MedicAllColours) {
    val named = listOf(
        "ground" to c.ground, "card" to c.card, "text" to c.text, "muted text" to c.mutedText, "main" to c.main,
        "on main" to c.onMain, "accent" to c.accent, "taken" to c.taken, "line" to c.line, "link" to c.link,
        "focus" to c.focus, "switch on" to c.switchOn, "disabled" to c.disabled, "disabled fill" to c.disabledFill,
        "error" to c.error, "primary container" to c.primaryContainer,
        "secondary container" to c.secondaryContainer, "tertiary container" to c.tertiaryContainer,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.steps(2)), verticalArrangement = Arrangement.spacedBy(Space.steps(2))) {
        named.forEach { (name, colour) ->
            Column(Modifier.width(Space.steps(24))) {
                Box(Modifier.fillMaxWidth().height(Space.steps(8)).background(colour, MaterialTheme.shapes.small)
                    .border(1.dp, c.line, MaterialTheme.shapes.small))
                Text(name, style = MedicAllTheme.type.caption, color = c.mutedText)
            }
        }
    }
}

@Composable
private fun Scales(c: MedicAllColours) {
    val steps = 5
    Column(verticalArrangement = Arrangement.spacedBy(Space.steps(2))) {
        repeat(MedicAllColours.SCALE_COLOURS) { index ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.steps(1)), verticalAlignment = Alignment.CenterVertically) {
                Text("Scale ${index + 1}", style = MedicAllTheme.type.label, color = c.text, modifier = Modifier.width(Space.steps(18)))
                for (step in 1..steps) {
                    Box(Modifier.size(Space.steps(5 + step)).background(c.scaleStep(index, step, steps), PillShape))
                }
            }
        }
    }
}

@Composable
private fun TypeSamples() {
    val c = MedicAllTheme.colours
    val samples = listOf(
        TypeRole.DISPLAY to "MedicAll",
        TypeRole.TITLE to "Medicines",
        TypeRole.HEADING to "This week",
        TypeRole.BODY to "Take with food, one hour before bed.",
        TypeRole.LABEL to "Dose",
        TypeRole.CAPTION to "Last taken yesterday",
        TypeRole.BUTTON to "Save",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.steps(1))) {
        samples.forEach { (role, words) ->
            Text("$words · ${role.name.lowercase()} ${role.sizeSp}/${role.lineHeightSp}", style = MedicAllTheme.type.of(role), color = c.text)
        }
        Text("Tabular figures", style = MedicAllTheme.type.label, color = c.mutedText)
        listOf("08:30   12.5 mg   ×1", "21:10   0.25 mg   ×11").forEach {
            Text(it, style = MedicAllTheme.type.body.tabular(), color = c.text)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Components() {
    val c = MedicAllTheme.colours
    var on by remember { mutableStateOf(true) }
    var chip by remember { mutableStateOf(true) }
    var field by remember { mutableStateOf("12.5") }
    Column(verticalArrangement = Arrangement.spacedBy(Space.steps(3))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.steps(2)), verticalArrangement = Arrangement.spacedBy(Space.steps(2))) {
            val shape = MedicAllComponents.buttonShape
            Button(onClick = {}, shape = shape, colors = MedicAllComponents.buttonColours()) { Text("Save") }
            OutlinedButton(onClick = {}, shape = shape, colors = MedicAllComponents.secondaryButtonColours(), border = MedicAllComponents.secondaryButtonBorder(true)) { Text("Cancel") }
            FilledTonalButton(onClick = {}, shape = shape, colors = MedicAllComponents.tonalButtonColours()) { Text("Tonal") }
            Button(onClick = {}, enabled = false, shape = shape, colors = MedicAllComponents.buttonColours()) { Text("Disabled") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.steps(2)), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = chip, onClick = { chip = !chip }, label = { Text("Selected") }, leadingIcon = if (chip) {
                { Icon(painterResource(MedicAllIcons.Phosphor.Check), null, Modifier.size(18.dp)) }
            } else null)
            FilterChip(selected = false, onClick = {}, label = { Text("Not selected") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.steps(4)), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = on, onCheckedChange = { on = it }, colors = MedicAllComponents.switchColours())
            Switch(checked = !on, onCheckedChange = { on = !it }, colors = MedicAllComponents.switchColours())
            Switch(checked = true, onCheckedChange = null, enabled = false, colors = MedicAllComponents.switchColours())
        }
        OutlinedTextField(field, { field = it }, label = { Text("Dose (mg)") }, textStyle = MedicAllTheme.type.body.tabular())
        OutlinedTextField(
            "", {}, label = { Text("Name") }, isError = true,
            supportingText = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.steps(1))) {
                    Icon(painterResource(MedicAllIcons.Phosphor.Info), null, Modifier.size(16.dp), tint = c.error)
                    Text("A profile needs a name.")
                }
            },
        )
        Surface(color = c.card, shape = MaterialTheme.shapes.medium) {
            NavigationBar(containerColor = c.card) {
                NavigationBarItem(true, {}, { Icon(painterResource(MedicAllIcons.Own.Medicine), null) },
                    label = { Text("Medicines", style = MedicAllTheme.type.navigationLabel(true)) },
                    colors = MedicAllComponents.navigationItemColours())
                NavigationBarItem(false, {}, { Icon(painterResource(MedicAllIcons.Phosphor.CalendarBlank), null) },
                    label = { Text("Calendar", style = MedicAllTheme.type.navigationLabel(false)) },
                    colors = MedicAllComponents.navigationItemColours())
                NavigationBarItem(false, {}, { Icon(painterResource(MedicAllIcons.Phosphor.Gear), null) },
                    label = { Text("Settings", style = MedicAllTheme.type.navigationLabel(false)) },
                    colors = MedicAllComponents.navigationItemColours())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.steps(2)), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Space.steps(10), Space.steps(5)).background(c.taken, PillShape))
            Text("taken", style = MedicAllTheme.type.caption, color = c.mutedText)
            Box(Modifier.size(Space.steps(10), Space.steps(5)).border(1.5.dp, c.mutedText, PillShape))
            Text("not yet", style = MedicAllTheme.type.caption, color = c.mutedText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconSamples() {
    val c = MedicAllTheme.colours
    Column(verticalArrangement = Arrangement.spacedBy(Space.steps(3))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.steps(3)), verticalArrangement = Arrangement.spacedBy(Space.steps(2))) {
            MedicAllIcons.Own.all.forEach { (name, icon) ->
                val tint = if (icon == MedicAllIcons.Own.DoseTaken) c.taken else c.text
                Icon(painterResource(icon), name, Modifier.size(Space.icon), tint = tint)
            }
        }
        HorizontalDivider(color = c.line)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.steps(3)), verticalArrangement = Arrangement.spacedBy(Space.steps(2))) {
            MedicAllIcons.Phosphor.all.forEach { (name, icon) ->
                Icon(painterResource(icon), name, Modifier.size(Space.icon), tint = c.text)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.main, MaterialTheme.shapes.small).padding(Space.steps(2)),
            horizontalArrangement = Arrangement.spacedBy(Space.steps(3)),
        ) {
            listOf(MedicAllIcons.Phosphor.ArrowLeft, MedicAllIcons.Phosphor.MagnifyingGlass, MedicAllIcons.Phosphor.DotsThreeVertical).forEach {
                Icon(painterResource(it), null, Modifier.size(Space.icon), tint = c.onMain)
            }
            Text("on the band", style = MedicAllTheme.type.caption, color = c.onMain)
        }
    }
}

/** The licence notices of the bundled fonts and icons, as the app carries them. */
@Composable
private fun Notices() {
    val c = MedicAllTheme.colours
    Column(Modifier.fillMaxWidth().background(c.ground).padding(Space.screenMargin), verticalArrangement = Arrangement.spacedBy(Space.steps(3))) {
        Text("Licence notices", style = MedicAllTheme.type.heading, color = c.text)
        ThirdPartyNotices.all.forEach { notice ->
            var first by remember(notice) { mutableStateOf("") }
            LaunchedEffect(notice) { first = ThirdPartyNotices.text(notice).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            Column(
                Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.medium).padding(Space.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Space.steps(1)),
            ) {
                Text(notice.work, style = MedicAllTheme.type.label, color = c.text)
                Text("${notice.licence} · ${notice.source}", style = MedicAllTheme.type.caption, color = c.mutedText)
                Text(first, style = MedicAllTheme.type.caption, color = c.mutedText)
            }
        }
    }
}

/** For the desktop render test: the size the preview is drawn at, a phone's width. */
val PREVIEW_WIDTH = 412.dp
