// The device tests of libmpv-kmp-canvas: its shared test folder and android-device-test compile into one
// test APK, against the staged artifacts.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val libmpvKmpVersion = providers.gradleProperty("libmpvKmpVersion").get()
val module = rootProject.layout.projectDirectory.dir("../../libmpv-kmp-canvas")

// The tests use internal declarations of libmpv-kmp-canvas (MpvRenderNative). The compiler sees them
// through the jar inside the staged Android archive, passed as a friend path.
val friendDir = layout.buildDirectory.dir("friend")
val friendClasses = tasks.register<Copy>("friendClasses") {
    val repository = providers.gradleProperty("verificationRepository").get()
    fileTree(repository) { include("io/github/yuroyami/libmpv-kmp-canvas-android/$libmpvKmpVersion/*.aar") }
        .forEach { from(zipTree(it)) { include("libs/*.jar"); eachFile { path = "classes.jar" } } }
    into(friendDir)
}
val friendJar = friendDir.map { it.file("classes.jar").asFile }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(friendClasses)
    compilerOptions.freeCompilerArgs.add(friendJar.map { "-Xfriend-paths=$it" })
}

kotlin {
    // As in the library builds: expect classes in the shared test folders.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "io.github.yuroyami.libmpv.canvas.devicetests"
        // The package of the test APK, the same as in the Gradle build before the move.
        testNamespace = "io.github.yuroyami.libmpv.canvas.test"
        compileSdk = 37
        // 26, as libmpv-kmp-canvas: AHardwareBuffer starts there.
        minSdk = 26
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
                implementation("io.github.yuroyami:libmpv-kmp-canvas:$libmpvKmpVersion")
                // The tests use Mpv and MpvOptions directly, so they do not rely on the canvas exporting them.
                implementation("io.github.yuroyami:libmpv-kmp-view:$libmpvKmpVersion")
                implementation("io.github.yuroyami:libmpv-kmp:$libmpvKmpVersion")
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir(module.dir("android-device-test/kotlin"))
            dependencies {
                compileOnly(files(friendJar).builtBy(friendClasses))
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                // The library keeps Compose UI internal, and the tests use its ImageBitmap directly.
                implementation(libs.compose.ui)
            }
        }
    }
}
