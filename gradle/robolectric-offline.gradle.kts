// Robolectric's Android runtime jar comes through Gradle, and the tests run with Robolectric offline.
// Left to itself Robolectric downloads android-all-instrumented from Maven Central while a test runs,
// and a flaky runner network then fails the gate (an IOException in ManifestTest). Gradle retries the
// download, and the CI's Gradle cache keeps the jar between runs.
//
// Applied by each module whose JVM tests use Robolectric. It covers the Test tasks AGP makes for them
// (testDebugUnitTest, testAndroidHostTest), not the desktop target's.
//
// Only the SDK 36 jar is here (targetSdk, and @Config(sdk = [36]) in :data). A test run on another SDK
// fails with "Unable to locate dependency" until that SDK's jar is added to robolectricRuntime.

val robolectricRuntime = configurations.create("robolectricRuntime") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    // A script plugin has no `libs` accessor.
    val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    add(robolectricRuntime.name, libs.findLibrary("robolectric-android-all").get())
}

val robolectricDeps = tasks.register<Sync>("robolectricDeps") {
    from(robolectricRuntime)
    into(layout.buildDirectory.dir("robolectric-deps"))
}

// A class, not a lambda: Gradle will not cache a test task whose argument provider is a lambda.
class RobolectricOfflineArgs(
    @get:InputDirectory @get:PathSensitive(PathSensitivity.NAME_ONLY) val dependencyDir: Provider<Directory>,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf(
        "-Drobolectric.offline=true",
        "-Drobolectric.dependency.dir=${dependencyDir.get().asFile.absolutePath}",
    )
}

tasks.withType<Test>().matching { it.name.endsWith("UnitTest") || it.name.endsWith("HostTest") }.configureEach {
    dependsOn(robolectricDeps)
    jvmArgumentProviders.add(RobolectricOfflineArgs(layout.dir(robolectricDeps.map { it.destinationDir })))
}
