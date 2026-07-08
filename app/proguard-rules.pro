# NovelReader ProGuard Rules

# Keep data models for serialization/deserialization
-keepclassmembers class com.novelreader.data.model.** { *; }
-keepclassmembers class com.novelreader.data.local.entity.** { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Jsoup
-keep class org.jsoup.** { *; }
