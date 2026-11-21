// The device tests of libmpv-kmp-view: its shared test folder and android-device-test compile into one
// test APK, against the staged artifacts.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val libmpvKmpVersion = providers.gradleProperty("libmpvKmpVersion").get()
val module = rootProject.layout.projectDirectory.dir("../../libmpv-kmp-view")

kotlin {
    // As in the library builds: expect classes in the shared test folders.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "io.github.yuroyami.libmpv.view.devicetests"
        // The package of the test APK, the same as in the Gradle build before the move.
        testNamespace = "io.github.yuroyami.libmpv.view.test"
        compileSdk { version = release(37) { minorApiLevel = 2 } }
        minSdk = 21
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        commonTest {
            // test/ holds the host tests that start no core. A missing folder adds nothing.
            kotlin.srcDir(module.dir("test"))
            dependencies {
                implementation("io.github.yuroyami:libmpv-kmp-view:$libmpvKmpVersion")
                // The tests use Mpv directly, so they do not rely on the view exporting it.
                implementation("io.github.yuroyami:libmpv-kmp:$libmpvKmpVersion")
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir(module.dir("android-device-test/kotlin"))
            dependencies {
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}
