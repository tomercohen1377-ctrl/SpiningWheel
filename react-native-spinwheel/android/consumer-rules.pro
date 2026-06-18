# Consumer ProGuard rules — automatically applied to apps that depend on
# the `react-native-spinwheel` AAR.

# Keep @Serializable models used over the public API surface
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep @kotlinx.serialization.Serializable class com.example.spinwheel.data.** { *; }

# OkHttp (transitive)
-dontwarn okhttp3.**
-dontwarn okio.**

# Glance / Compose / Kotlin reflection used at runtime
-dontwarn androidx.glance.**
