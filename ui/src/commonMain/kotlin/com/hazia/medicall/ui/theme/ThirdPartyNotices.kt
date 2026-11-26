package com.hazia.medicall.ui.theme

import com.hazia.medicall.ui.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * A third-party work bundled in the app, with the licence text that must travel with it: [path] is the text's place
 * among the app's resource files.
 */
data class Notice(val work: String, val source: String, val licence: String, val path: String)

/**
 * The licence notices of what the theme bundles (SPEC:R115, SPEC:R117): the two fonts under the SIL Open Font
 * License 1.1, which asks that its text travel with the fonts, and Phosphor's icons under the MIT licence, which asks
 * that its notice travel with copies. A screen that shows them is part of phase 2b.
 */
object ThirdPartyNotices {
    val all: List<Notice> = listOf(
        Notice(
            work = "Fraunces",
            source = "https://github.com/undercasetype/Fraunces",
            licence = "SIL Open Font License 1.1",
            path = "files/licences/fraunces-ofl.txt",
        ),
        Notice(
            work = "Atkinson Hyperlegible Next",
            source = "https://github.com/googlefonts/atkinson-hyperlegible-next",
            licence = "SIL Open Font License 1.1",
            path = "files/licences/atkinson-hyperlegible-next-ofl.txt",
        ),
        Notice(
            work = "Phosphor Icons",
            source = "https://github.com/phosphor-icons/core",
            licence = "MIT License",
            path = "files/licences/phosphor-mit.txt",
        ),
    )

    /** The full licence text of [notice]. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun text(notice: Notice): String = Res.readBytes(notice.path).decodeToString()
}
