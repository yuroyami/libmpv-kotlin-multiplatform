// The Android device tests of the libmpvKt libraries. The Kotlin Toolchain has no instrumented test
// support, so this Gradle build runs them against the staged artifacts of this checkout.
// Run it through scripts/run-device-tests.sh, which stages the artifacts first.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "device-tests"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        // The libmpvKt artifacts come only from the staged copy of this checkout.
        exclusiveContent {
            forRepository {
                maven {
                    name = "verification"
                    url = uri(providers.gradleProperty("verificationRepository")
                        .orElse(rootDir.resolve("../../build/verification-maven").absolutePath).get())
                }
            }
            filter { includeModuleByRegex("io\\.github\\.yuroyami", "libmpvkt.*") }
        }
        google()
        mavenCentral()
    }
}

include(":native", ":core", ":view", ":canvas")
