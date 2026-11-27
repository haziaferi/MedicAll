// :ui -- MedicAll's Compose theme (SPEC:R93, R114 to R122) and, from phase 2b, its screens. Shared by Android and
// the desktop (SPEC:R03, R04). The theme's tests run on the desktop JVM with the other shared modules' (SPEC:R99).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hazia.medicall.ui"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // The fonts, icons and licence texts reach Android through Compose Multiplatform resources.
        androidResources.enable = true
    }

    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopTest").dependencies {
            // Skia for the preview's headless render (ImageComposeScene) on the machine running the tests.
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    // Screens in other modules (the Android debug preview) reach the fonts, icons and licences through the theme's API;
    // the generated accessors stay in this module.
    packageOfResClass = "com.hazia.medicall.ui.resources"
}

// The theme's tests read SPEC.md's tables and scan the repository's sources, so they know where the root is.
tasks.withType<Test>().configureEach {
    systemProperty("medicall.root", rootDir.absolutePath)
    inputs.file(rootProject.file("SPEC.md"))
}
