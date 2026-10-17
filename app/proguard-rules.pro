# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===================== EdgeLLM production rules =====================

# Keep line numbers for readable crash traces in release builds
-keepattributes SourceFile,LineNumberTable

# Room: DB implementation + models are accessed via reflection
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Moshi: JSON models + generated adapters
-keep class com.perpcorp.edgellm.data.model.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keepnames class com.perpcorp.edgellm.data.model.**

# MediaPipe Tasks (LLM Inference + Vision Image Generator): JNI + reflection entry points
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn com.google.mediapipe.**

# ONNX Runtime (ships native .so via AAR)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# TensorFlow Lite runtime
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# kotlinx.coroutines (service loader dispatchers)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OkHttp / Retrofit / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Firebase AppCheck / Google services (optional at runtime, WARN strategy)
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
