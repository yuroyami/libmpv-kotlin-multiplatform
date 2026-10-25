package io.github.yuroyami.libmpv.buildtools

import java.io.File

/**
 * The `include` folder of a JDK, which holds `jni.h`. The toolchain runs its plugins on a runtime without
 * headers, so this also asks JAVA_HOME and `/usr/libexec/java_home`.
 */
fun jdkIncludeDir(): File {
    val candidates = buildList {
        System.getenv("JAVA_HOME")?.let { add(File(it)) }
        add(File(System.getProperty("java.home")))
        val javaHome = File("/usr/libexec/java_home")
        if (javaHome.canExecute()) {
            runCatching {
                ProcessBuilder(javaHome.path).start().inputStream.bufferedReader().readText().trim()
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { add(File(it)) }
        }
    }
    return candidates.map { it.resolve("include") }.firstOrNull { it.resolve("jni.h").isFile }
        ?: error("No JDK with include/jni.h found. Set JAVA_HOME to a JDK.")
}

/**
 * `jni_md.h` for a Linux or Windows link, with the values of that platform's JDK. `jni.h` is the same on
 * every platform, so a cross link needs no JDK for its target. On Windows `JNIEXPORT` stays
 * `__declspec(dllexport)`, because the JVM finds the `MpvNative` functions by name.
 */
fun jniPlatformHeader(windows: Boolean): String {
    val body = if (windows) {
        """
        #define JNIEXPORT __declspec(dllexport)
        #define JNIIMPORT __declspec(dllimport)
        #define JNICALL __stdcall
        typedef long jint;
        typedef long long jlong;
        typedef signed char jbyte;
        """
    } else {
        """
        #define JNIEXPORT __attribute__((visibility("default")))
        #define JNIIMPORT __attribute__((visibility("default")))
        #define JNICALL
        typedef int jint;
        #ifdef _LP64
        typedef long jlong;
        #else
        typedef long long jlong;
        #endif
        typedef signed char jbyte;
        """
    }.trimIndent()
    return "/* Written by the libmpv-kotlin-multiplatform build for a cross JNI link. */\n" +
        "#ifndef _JAVASOFT_JNI_MD_H_\n#define _JAVASOFT_JNI_MD_H_\n$body\n#endif\n"
}
