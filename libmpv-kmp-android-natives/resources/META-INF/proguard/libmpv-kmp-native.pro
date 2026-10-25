# The JNI library finds the native methods of MpvNative and MpvAndroidNative by name.
-keepclasseswithmembernames,includedescriptorclasses class io.github.yuroyami.libmpv.jni.MpvNative { native <methods>; }
-keepclasseswithmembernames,includedescriptorclasses class io.github.yuroyami.libmpv.jni.MpvAndroidNative { native <methods>; }
# stream_cb.cpp resolves these two interfaces with FindClass and calls their methods by signature.
-keep interface io.github.yuroyami.libmpv.stream.MpvStreamProvider { *; }
-keep interface io.github.yuroyami.libmpv.stream.MpvStream { *; }
-keep class * implements io.github.yuroyami.libmpv.stream.MpvStream { public <methods>; }
-keep class * implements io.github.yuroyami.libmpv.stream.MpvStreamProvider { public <methods>; }
