# NovelReader ProGuard Rules

# ==== Modèles de données ====
-keepclassmembers class com.novelreader.data.model.** { *; }
-keepclassmembers class com.novelreader.data.local.entity.** { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase { *; }

# ==== Kotlinx Serialization ====
# CRITIQUE : toutes les classes @Serializable de l'app (DTO API NovelFrance,
# modèles de cache Room, fichiers de téléchargement JSON, GitHub Releases)
# doivent conserver leurs noms de champs, sinon le parsing JSON est cassé
# en build release minifiée (R8 obfusque les noms de propriétés).
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

# Conserver intégralement toutes les classes @Serializable de l'app
-keep @kotlinx.serialization.Serializable class com.novelreader.** { *; }

# Conserver les companion objects (les serializers générés y vivent)
-keepclassmembers class com.novelreader.** {
    *** Companion;
}

# Conserver les fonctions serializer() générées par le plugin
-keepclasseswithmembers class com.novelreader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==== Jsoup ====
-keep class org.jsoup.** { *; }

# ==== OkHttp / Okio (règles recommandées) ====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
