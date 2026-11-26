package com.hazia.medicall.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC:R93, SPEC:R122: no colour literal appears outside the theme. Every Kotlin, Gradle and XML source in the
 * repository is read, except the theme's own package (its sources and these tests), the icon drawables, which are
 * black masks that Icon recolours (checked in [ShapeSpaceIconTest]), and the launcher icon and splash screen, which
 * R93 exempts: Android resource files named `ic_launcher*`, `launcher*` or `splash*`, whose colours R118 fixes.
 */
class NoColourLiteralTest {

    private val literals = listOf(
        Regex("""Color\(\s*0x""") to "a Color made from a number",
        Regex("""Color\(\s*(?:red\s*=|[0-9.]+f?\s*,)""") to "a Color made from channels",
        Regex("""\b0x[0-9A-Fa-f]{8}\b""") to "an ARGB number",
        Regex("""["'>]#(?:[0-9A-Fa-f]{3,4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\b""") to "a hex colour",
        Regex("""\bColor\.(?:Black|White|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b""") to "a named colour",
        Regex("""\bandroid\.graphics\.Color\b|\bColor\.parseColor\b|\bColor\.rgb\(|\bColor\.argb\(""") to "an Android colour",
    )

    private val skippedDirs = setOf(".git", ".gradle", ".kotlin", ".claude", ".idea", "build", "build-logs", "docs", "tools")
    private val themeDir = Regex("""[/\\]kotlin[/\\]com[/\\]hazia[/\\]medicall[/\\]ui[/\\]theme[/\\]""")
    private val iconDrawables = File(ROOT, "ui/src/commonMain/composeResources/drawable").canonicalFile

    /** The launcher icon's and splash screen's Android resources (R93's exemption, R118's colours). */
    private val launcherResource =
        Regex("""[/\\]android[/\\]src[/\\][^/\\]+[/\\]res[/\\][^/\\]+[/\\](?:ic_launcher|launcher|splash)[^/\\]*\.xml$""")

    private fun sources(): List<File> = ROOT.walkTopDown()
        .onEnter { it.name !in skippedDirs && it.canonicalFile != iconDrawables }
        .filter { it.isFile && it.extension in setOf("kt", "kts", "xml") }
        .filterNot { themeDir.containsMatchIn(it.path) || launcherResource.containsMatchIn(it.path) }
        .toList()

    @Test
    fun onlyTheLaunchersResourcesAreExempt_SPEC_R93() {
        for (p in listOf(
            "/r/android/src/main/res/mipmap-anydpi/ic_launcher.xml", "/r/android/src/main/res/drawable/ic_launcher_foreground.xml",
            "/r/android/src/main/res/values/launcher.xml", "/r/android/src/main/res/values/splash.xml",
            "C:\\r\\android\\src\\main\\res\\values-night\\splash.xml",
        )) assertTrue(launcherResource.containsMatchIn(p), p)
        for (p in listOf(
            "/r/android/src/main/res/values/themes.xml", "/r/android/src/main/res/values/colors.xml",
            "/r/android/src/main/kotlin/com/hazia/medicall/SplashActivity.kt", "/r/ui/src/main/res/values/splash.xml",
            "/r/android/src/main/AndroidManifest.xml",
        )) assertTrue(!launcherResource.containsMatchIn(p), p)
    }

    @Test
    fun noColourLiteralOutsideTheTheme_SPEC_R93() {
        val files = sources()
        // The scan reaches the code it guards: the preview, the Android app and the shared modules.
        assertTrue(files.any { it.path.replace('\\', '/').endsWith("ui/preview/ThemePreview.kt") }, "the scan reads the preview")
        assertTrue(files.any { it.path.replace('\\', '/').endsWith("android/src/main/AndroidManifest.xml") })
        assertTrue(files.count { it.path.replace('\\', '/').contains("/core/src/") } > 5)
        val found = files.flatMap { file ->
            file.readLines().withIndex().flatMap { (i, line) ->
                literals.filter { it.first.containsMatchIn(line) }.map { "${ROOT.toPath().relativize(file.toPath())}:${i + 1}: ${it.second}: ${line.trim()}" }
            }
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun theScanFindsALiteral() {
        val samples = listOf(
            "val c = Color(0xFF2747A0)", "Color(0.1f, 0.2f, 0.3f)", "Color(red = 1, green = 2, blue = 3)",
            "tint = Color.White", "background = \"#2747A0\"", "<color>#FFF</color>", "android.graphics.Color.RED",
        )
        for (s in samples) assertTrue(literals.any { it.first.containsMatchIn(s) }, s)
        for (s in listOf("Color.Transparent", "MedicAllTheme.colours.main", "0xFF and bytes", "// see #12 on GitHub")) {
            assertTrue(literals.none { it.first.containsMatchIn(s) }, s)
        }
    }
}
