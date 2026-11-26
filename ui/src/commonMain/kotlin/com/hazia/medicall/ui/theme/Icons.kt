package com.hazia.medicall.ui.theme

import com.hazia.medicall.ui.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * MedicAll's icons (SPEC:R117): Phosphor's regular icons for generic controls ([Phosphor]) and MedicAll's own set for
 * its objects ([Own]), all on a 24 dp grid. Each is drawn as a mask: an `Icon` takes the colour of the text beside it,
 * and the theme's colours decide which (SPEC:R117, SPEC:R93). The vector drawables are made by
 * `tools/icons_to_vectors.py`; Phosphor's are added here as screens need them.
 */
object MedicAllIcons {
    /** Phosphor's regular icons (MIT licence, see [ThirdPartyNotices]). */
    object Phosphor {
        val Archive = Res.drawable.ph_archive
        val ArrowCounterClockwise = Res.drawable.ph_arrow_counter_clockwise
        val ArrowLeft = Res.drawable.ph_arrow_left
        val ArrowsClockwise = Res.drawable.ph_arrows_clockwise
        val Bell = Res.drawable.ph_bell
        val BellSlash = Res.drawable.ph_bell_slash
        val CalendarBlank = Res.drawable.ph_calendar_blank
        val CaretDown = Res.drawable.ph_caret_down
        val CaretLeft = Res.drawable.ph_caret_left
        val CaretRight = Res.drawable.ph_caret_right
        val CaretUp = Res.drawable.ph_caret_up
        val Check = Res.drawable.ph_check
        val CircleHalf = Res.drawable.ph_circle_half
        val Clock = Res.drawable.ph_clock
        val DotsThreeVertical = Res.drawable.ph_dots_three_vertical
        val DownloadSimple = Res.drawable.ph_download_simple
        val Eye = Res.drawable.ph_eye
        val EyeSlash = Res.drawable.ph_eye_slash
        val File = Res.drawable.ph_file
        val Fingerprint = Res.drawable.ph_fingerprint
        val Folder = Res.drawable.ph_folder
        val Gear = Res.drawable.ph_gear
        val House = Res.drawable.ph_house

        /** The information icon: beside an error (in the error colour) and a wrong PIN (SPEC:R119, SPEC:R122). */
        val Info = Res.drawable.ph_info
        val Key = Res.drawable.ph_key
        val List = Res.drawable.ph_list
        val Lock = Res.drawable.ph_lock
        val LockOpen = Res.drawable.ph_lock_open
        val MagnifyingGlass = Res.drawable.ph_magnifying_glass
        val Minus = Res.drawable.ph_minus
        val Moon = Res.drawable.ph_moon
        val Palette = Res.drawable.ph_palette
        val Paperclip = Res.drawable.ph_paperclip
        val PencilSimple = Res.drawable.ph_pencil_simple
        val Plus = Res.drawable.ph_plus
        val Question = Res.drawable.ph_question
        val ShareNetwork = Res.drawable.ph_share_network
        val Sun = Res.drawable.ph_sun
        val Trash = Res.drawable.ph_trash
        val UploadSimple = Res.drawable.ph_upload_simple
        val User = Res.drawable.ph_user
        val UserPlus = Res.drawable.ph_user_plus
        val Users = Res.drawable.ph_users
        val X = Res.drawable.ph_x

        val all: Map<String, DrawableResource> = linkedMapOf(
            "archive" to Archive, "arrow-counter-clockwise" to ArrowCounterClockwise, "arrow-left" to ArrowLeft,
            "arrows-clockwise" to ArrowsClockwise, "bell" to Bell, "bell-slash" to BellSlash,
            "calendar-blank" to CalendarBlank, "caret-down" to CaretDown, "caret-left" to CaretLeft,
            "caret-right" to CaretRight, "caret-up" to CaretUp, "check" to Check, "circle-half" to CircleHalf,
            "clock" to Clock, "dots-three-vertical" to DotsThreeVertical, "download-simple" to DownloadSimple,
            "eye" to Eye, "eye-slash" to EyeSlash, "file" to File, "fingerprint" to Fingerprint, "folder" to Folder,
            "gear" to Gear, "house" to House, "info" to Info, "key" to Key, "list" to List, "lock" to Lock,
            "lock-open" to LockOpen, "magnifying-glass" to MagnifyingGlass, "minus" to Minus, "moon" to Moon,
            "palette" to Palette, "paperclip" to Paperclip, "pencil-simple" to PencilSimple, "plus" to Plus,
            "question" to Question, "share-network" to ShareNetwork, "sun" to Sun, "trash" to Trash,
            "upload-simple" to UploadSimple, "user" to User, "user-plus" to UserPlus, "users" to Users, "x" to X,
        )
    }

    /** MedicAll's own icons, from `docs/design/icons`: a 1.5 dp stroke on the 24 dp grid. */
    object Own {
        /** Medicines: a plain pharmacy jar. */
        val Medicine = Res.drawable.mc_medicine

        /** A taken dose; it takes the taken colour (SPEC:R117). */
        val DoseTaken = Res.drawable.mc_dose_taken

        /** A skipped dose; it needs its word beside it. */
        val DoseSkipped = Res.drawable.mc_dose_skipped
        val DoseMissed = Res.drawable.mc_dose_missed
        val Tablet = Res.drawable.mc_tablet
        val Capsule = Res.drawable.mc_capsule
        val Drops = Res.drawable.mc_drops
        val Injection = Res.drawable.mc_injection
        val Patch = Res.drawable.mc_patch
        val ReferenceRange = Res.drawable.mc_reference_range

        val all: Map<String, DrawableResource> = linkedMapOf(
            "medicine" to Medicine, "dose-taken" to DoseTaken, "dose-skipped" to DoseSkipped,
            "dose-missed" to DoseMissed, "tablet" to Tablet, "capsule" to Capsule, "drops" to Drops,
            "injection" to Injection, "patch" to Patch, "reference-range" to ReferenceRange,
        )
    }
}
