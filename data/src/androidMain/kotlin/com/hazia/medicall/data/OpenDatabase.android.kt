package com.hazia.medicall.data

import android.content.Context
import androidx.room.Room

fun openMedicAllDatabase(context: Context): DatabaseOpen<MedicAllDatabase> {
    val app = context.applicationContext
    return openDatabase(
        file = app.getDatabasePath(DATABASE_NAME),
        builder = { Room.databaseBuilder<MedicAllDatabase>(app, DATABASE_NAME) },
        driver = KeepFileOnCorruptionDriver(),
    )
}
