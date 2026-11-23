package com.hazia.medicall.data

import com.hazia.medicall.core.sync.Ids
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * This device's sync identity (ADR 01, SPEC:R16), kept in its own [file] (ADR 09): not a setting
 * and not in the database, so neither a settings reset nor a recovered database gives the device a
 * new one. A UUID, as every MedicAll id is (SPEC:R15).
 *
 * Created once: the new id is written to a temporary file and moved into place only if no other
 * process got there first, in which case that process's id is used. An unreadable file is set
 * aside and a new identity made; peers then see a new device, which costs only a stale name.
 */
@OptIn(ExperimentalUuidApi::class)
fun deviceId(file: File, now: () -> Long = System::currentTimeMillis): String {
    if (file.exists()) {
        val id = file.readText().trim()
        if (Uuid.parseOrNull(id) != null) return id
        setAside(file, now())
    }
    val dir = requireNotNull(file.absoluteFile.parentFile) { "no directory for ${file.path}" }.apply { mkdirs() }
    val tmp = Files.createTempFile(dir.toPath(), file.name, ".tmp")
    // Files.write, not Files.writeString: Android before API 33 has no writeString (found on the 6T, Android 11).
    Files.write(tmp, Ids.new().encodeToByteArray())
    try {
        Files.move(tmp, file.toPath())
    } catch (_: FileAlreadyExistsException) {
        Files.delete(tmp)
    }
    return file.readText().trim()
}
