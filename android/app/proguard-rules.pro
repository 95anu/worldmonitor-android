# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.worldmonitor.android.data.models.**$$serializer { *; }
-keepclassmembers class com.worldmonitor.android.data.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.worldmonitor.android.data.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Coil
-dontwarn coil.**
