// The device tests of libmpvkt-native: its shared test folder and android-device-test compile into one
// test APK, against the staged artifacts.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val libmpvktVersion = providers.gradleProperty("libmpvktVersion").get()
val module = rootProject.layout.projectDirectory.dir("../../libmpvkt-native")

kotlin {
    // As in the library builds: expect classes in the shared test folders.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "io.github.yuroyami.libmpvkt.jni.devicetests"
        // The package of the test APK, the same as in the Gradle build before the move.
        testNamespace = "io.github.yuroyami.libmpvkt.jni.test"
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
            dependencies {
                implementation("io.github.yuroyami:libmpvkt-native:$libmpvktVersion")
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
