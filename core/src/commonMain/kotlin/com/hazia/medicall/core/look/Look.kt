package com.hazia.medicall.core.look

/**
 * The four palettes (SPEC:R114), each with the [id] it is stored under on the device. A profile's palette and the
 * device palette are chosen on the device and never synced; both start on [DEFAULT], Albarello.
 */
enum class Palette(val id: String) {
    ALBARELLO("albarello"),
    PLUM_AND_OLIVE("plum-and-olive"),
    CAPSULE("capsule"),
    MURANO("murano");

    companion object {
        val DEFAULT = ALBARELLO

        /** The palette stored as [id], or null for an id this version does not know. */
        fun byId(id: String): Palette? = entries.firstOrNull { it.id == id }
    }
}

/** Light or dark (SPEC:R114): one setting for the whole device, which follows the system until the person sets it. */
enum class LightDark(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    /** Whether screens are drawn dark, given whether the system is dark now. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** The setting stored as [id], or null for an id this version does not know. */
        fun byId(id: String): LightDark? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The device's own look (SPEC:R114): [lightDark] for every profile on the device, and [palette], the device palette
 * that screens belonging to no profile show (lock, first run, passphrase, the profile list, the device's settings).
 */
data class DeviceLook(val lightDark: LightDark = LightDark.SYSTEM, val palette: Palette = Palette.DEFAULT) {
    companion object {
        val DEFAULT = DeviceLook()
    }
}
