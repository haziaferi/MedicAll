package com.hazia.medicall.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Version 1 of the database: its tables' keys and the schema file Room exports (its safe open: [BundledDriverCorruptionTest]). */
class DatabaseTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun profile(id: String) = ProfileEntity(
        id = id, name = "Profile A", nameHlc = 1, nameDevice = "A",
        birthDate = null, kind = "ADULT", detailsHlc = 1, detailsDevice = "A",
        removedAt = null, removedHlc = 1, removedDevice = "A",
    )

    private fun blobRef(id: String, profileId: String) = BlobRefEntity(
        id = id, profileId = profileId, sha256 = "00".repeat(32), size = 3, name = "Lab 1.pdf", mime = "application/pdf",
        mainHlc = 1, mainDevice = "A", removedAt = null, removedHlc = 1, removedDevice = "A",
    )

    @Test
    fun blobRefWithoutProfileIsRefused_SPEC_R15() {
        val db = openMedicAllDatabase(File(tmp.root, DATABASE_NAME)).database
        try {
            val failure = runCatching { runBlocking { db.blobRefDao().putBlobRefs(listOf(blobRef("b1", "no-such-profile"))) } }.exceptionOrNull()

            assertNotNull(failure, "a file reference to a missing profile was written")
            assertTrue(isConstraintViolation(failure), "expected a constraint error, got $failure")
            assertEquals(emptyList(), runBlocking { db.blobRefDao().allBlobRefs() })

            // The control: the same reference to a profile that exists is written.
            runBlocking {
                db.profileDao().putProfiles(listOf(profile("p1")))
                db.blobRefDao().putBlobRefs(listOf(blobRef("b1", "p1")))
            }
            assertEquals(listOf("b1"), runBlocking { db.blobRefDao().allBlobRefs() }.map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun schemaFileMatchesVersion() {
        // Gradle runs the desktop tests in the module's directory, where Room exports the schema.
        val schema = File("schemas/com.hazia.medicall.data.MedicAllDatabase/1.json")

        assertTrue(schema.isFile, "no schema file at ${schema.absolutePath}")
        val version = Json.parseToJsonElement(schema.readText()).jsonObject.getValue("database").jsonObject.getValue("version").jsonPrimitive.int
        assertEquals(1, version)
        assertEquals(SCHEMA_VERSION, version)
    }
}
