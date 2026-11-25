package com.hazia.medicall

import android.app.Activity
import android.os.Bundle

/**
 * Debug only: brings the process to the foreground and closes at once. OxygenOS's freezer stops an instrumented
 * run's process in the background; `adb shell am start -n com.hazia.medicall/.WakeActivity` wakes it. It touches no
 * data, unlike [HarnessActivity], whose start-up clean-up ([AndroidDevice.sweepOrphans]) would take the tests'
 * folders, joined under other settings files, for orphans.
 */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
