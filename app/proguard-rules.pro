# Keep line number info for readable release crash traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room: DB implementation + models are accessed via reflection
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Moshi: JSON models + generated adapters
-keep class com.squareup.moshi.** { *; }
-keep class com.perpcorp.edgellm.data.model.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keepnames class com.perpcorp.edgellm.data.model.**

# MediaPipe Tasks (LLM Inference + Vision Image Generator): JNI + reflection
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn com.google.mediapipe.**

# ONNX Runtime (ships native .so via AAR)
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-keepclassmembers class com.microsoft.onnxruntime.** { native <methods>; }
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# TensorFlow Lite runtime
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { native <methods>; }
-dontwarn org.tensorflow.lite.**

# kotlinx.coroutines (service loader dispatchers)
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# OkHttp / Retrofit / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# Firebase AppCheck / Google services (optional at runtime, WARN strategy)
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.** { *; }

# Keep native methods in JNI bridge
-keepclasseswithmembernames class * {
    native <methods>;
}

# BuildConfig is read reflectively by BillingRepository; keep its fields.
-keep class com.perpcorp.edgellm.BuildConfig { *; }
