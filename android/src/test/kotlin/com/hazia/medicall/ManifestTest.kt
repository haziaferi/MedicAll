package com.hazia.medicall

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ManifestTest {
    // SPEC:R12 MedicAll connects only for the medicine-list refresh, which is not built yet.
    @Test
    fun noInternetPermission_SPEC_R12() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertFalse("requested: $requested", "android.permission.INTERNET" in requested)
    }
}
