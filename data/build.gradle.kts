plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)

    // Room generates an `actual object` for the database constructor.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.hazia.medicall.data"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
        withJava()
    }

    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmCommon = create("jvmCommon") {
            dependsOn(commonMain.get())
            kotlin.srcDir("src/jvmCommon/java")
        }
        androidMain { dependsOn(jvmCommon) }
        getByName("desktopMain") { dependsOn(jvmCommon) }

        commonMain.dependencies {
            api(project(":core"))
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            // The element API only, so no serialization compiler plugin.
            implementation(libs.kotlinx.serialization.json)
        }
        jvmCommon.dependencies {
            // SPEC:R112 backups (docs/decisions/2026-11-22-7zip-library.md); xz is its LZMA2.
            implementation(libs.commons.compress)
            implementation(libs.xz)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
            // SPEC:R10 the Android database is SQLCipher.
            api(libs.sqlcipher.android)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

// One database, one schema file per version, committed: a migration needs the previous version's file.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The patched 7z writer (SealedSevenZOutputFile, SPEC:R112) is Java shared by both targets: the Kotlin
// compilers see it through jvmCommon's source root, and each target's javac compiles it.
tasks.withType<JavaCompile>().matching { it.name == "compileDesktopMainJava" || it.name == "compileAndroidMainJavaWithJavac" }.configureEach {
    source("src/jvmCommon/java")
}

// Both targets write the same schema file; running them in parallel races on it.
tasks.matching { it.name == "kspKotlinDesktop" }.configureEach {
    mustRunAfter("kspAndroidMain")
}
