# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.volunteerdash.**$$serializer { *; }
-keepclassmembers class com.volunteerdash.** { *** Companion; }
-keepclasseswithmembers class com.volunteerdash.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit service interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.volunteerdash.data.remote.ApiService { *; }

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
