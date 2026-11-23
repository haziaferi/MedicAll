package com.hazia.medicall.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

fun openMedicAllDatabase(file: File, now: () -> Long = System::currentTimeMillis): DatabaseOpen<MedicAllDatabase> =
    openDatabase(
        file = file,
        builder = { Room.databaseBuilder<MedicAllDatabase>(name = file.absolutePath) },
        driver = BundledSQLiteDriver(),
        now = now,
    )
