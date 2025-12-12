# --- Kotlin General ---
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keep class kotlin.Metadata { *; }

# --- Kotlinx Serialization (CRITICAL for Ktor/Network) ---
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keep,allowobfuscation,allowshrinking class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepnames class kotlinx.serialization.json.** { *; }
# Keep serializer companion objects
-keepclassmembers class * {
    static ** Companion;
}
-keepclasseswithmembers class * {
    static ** serializer();
}

# --- Ktor ---
-keep class io.ktor.** { *; }
-keep class io.ktor.client.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# --- Koin (Dependency Injection) ---
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.module.Module
-dontwarn org.koin.**

# --- Voyager (Navigation) ---
-keep class cafe.adriel.voyager.** { *; }

# --- Yandex Maps (Native/JNI) ---
# Yandex Maps uses JNI heavily. If you strip these, the map will crash immediately.
-keep class com.yandex.mapkit.** { *; }
-keep class com.yandex.runtime.** { *; }
-keep interface com.yandex.runtime.** { *; }

# --- Firebase ---
# Usually Firebase ships with its own consumer rules, but these are safe defaults
-keep class com.google.firebase.** { *; }

# --- CameraX & ML Kit ---
-keep class androidx.camera.** { *; }
-keep class com.google.mlkit.** { *; }

# --- ViewModels / ScreenModels ---
# Ensure your ScreenModels are kept if they are accessed via reflection (Koin often does this)
-keep class * extends cafe.adriel.voyager.core.model.ScreenModel { <init>(...); }

# --- Data Classes (Optional, safer for JSON parsing) ---
# If you have specific data classes that are parsed via reflection (not compile-time serialization), keep them:
# -keep class io.loyaltyloop.shared.models.** { *; }


