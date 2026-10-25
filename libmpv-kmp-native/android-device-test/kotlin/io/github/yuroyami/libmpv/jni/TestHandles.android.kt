package io.github.yuroyami.libmpv.jni

import androidx.test.core.app.ApplicationProvider

@OptIn(MpvNativeApi::class)
internal actual fun createTestHandle(): Long {
    MpvAndroidNative.initAndroid(ApplicationProvider.getApplicationContext())
    return MpvNative.create()
}
