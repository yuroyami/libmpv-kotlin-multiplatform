// The device tests of libmpv-kmp-native: its shared test folder and android-device-test compile into one
// test APK, against the staged artifacts.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val libmpvKmpVersion = providers.gradleProperty("libmpvKmpVersion").get()
val module = rootProject.layout.projectDirectory.dir("../../libmpv-kmp-native")

kotlin {
    // As in the library builds: expect classes in the shared test folders.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "io.github.yuroyami.libmpv.jni.devicetests"
        // The package of the test APK, the same as in the Gradle build before the move.
        testNamespace = "io.github.yuroyami.libmpv.jni.test"
        compileSdk = 37
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
            // test@core/ holds the tests that start a real core. The JVM and macOS run them too.
            kotlin.srcDir(module.dir("test@core"))
            dependencies {
                implementation("io.github.yuroyami:libmpv-kmp-native:$libmpvKmpVersion")
                implementation(kotlin("test"))
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
