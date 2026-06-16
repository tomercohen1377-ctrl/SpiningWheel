# Consumer ProGuard rules — automatically applied to apps that depend on :spinwheel

# Keep @Serializable models used over the public API
-keep @kotlinx.serialization.Serializable class com.example.spinwheel.data.** { *; }

# OkHttp (transitive)
-dontwarn okhttp3.**
-dontwarn okio.**
