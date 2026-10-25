#include <jni.h>

#include <mutex>

extern "C" {
#include <libavcodec/jni.h>
}

// Android only: ndk-build compiles this file, the desktop JNI library does not.
#define FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_io_github_yuroyami_libmpv_jni_MpvAndroidNative_##name

FN(void, initAndroid)(JNIEnv *env, jobject, jobject appctx) {
    static std::once_flag once;
    std::call_once(once, [&] {
        JavaVM *vm = nullptr;
        if (env->GetJavaVM(&vm) != JNI_OK) return;
        av_jni_set_java_vm(vm, nullptr);
        jobject global = env->NewGlobalRef(appctx);
        if (global) av_jni_set_android_app_ctx(global, nullptr);
    });
}

FN(jlong, surfaceHandle)(JNIEnv *env, jobject, jobject surface) { return reinterpret_cast<jlong>(env->NewGlobalRef(surface)); }
FN(void, releaseSurfaceHandle)(JNIEnv *env, jobject, jlong handle) { if (handle) env->DeleteGlobalRef(reinterpret_cast<jobject>(handle)); }
