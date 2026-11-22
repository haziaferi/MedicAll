plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.hazia.medicall.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
    }

    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Android and the desktop both have javax.crypto: the crypto code lives here once.
        val jvmShared = create("jvmShared") { dependsOn(commonMain.get()) }
        androidMain { dependsOn(jvmShared) }
        getByName("desktopMain") { dependsOn(jvmShared) }

        commonMain.dependencies {
            // The element API only, so no serialization compiler plugin (as in Factotum's :data).
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
