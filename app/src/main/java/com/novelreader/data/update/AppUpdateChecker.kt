package com.novelreader.data.update

import com.novelreader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vérifie les mises à jour de l'application via l'API GitHub Releases.
 *
 * Appelle https://api.github.com/repos/elfred434/novel-readers/releases/latest
 * et compare la version avec BuildConfig.VERSION_NAME.
 *
 * Résout le conflit de signature Android en téléchargeant l'APK
 * via le DownloadManager système + Intent d'installation standard.
 */
@Singleton
class AppUpdateChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val GITHUB_API = "https://api.github.com/repos/elfred434/novel-readers/releases/latest"
    }

    /**
     * Vérifie si une mise à jour est disponible.
     * Distingue « à jour », « disponible » et « erreur » (réseau / serveur),
     * pour que l'UI ne fasse pas passer un échec pour un « à jour ».
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext UpdateCheckResult.Error

            val body = response.body?.string() ?: return@withContext UpdateCheckResult.Error
            val release = json.decodeFromString<GitHubRelease>(body)

            val tagVersion = release.tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (compareVersions(tagVersion, currentVersion) > 0) {
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                if (apkAsset == null) return@withContext UpdateCheckResult.UpToDate

                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionName = tagVersion,
                        apkUrl = apkAsset.browserDownloadUrl,
                        changelog = release.body ?: "Mise à jour disponible",
                        publishedAt = release.publishedAt ?: ""
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            android.util.Log.w("AppUpdateChecker", "Erreur vérification update", e)
            UpdateCheckResult.Error
        }
    }

    /** Compare deux versions sémantiques "1.0.42" > "1.0.5" => positif */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = normalizeVersion(v1)
        val parts2 = normalizeVersion(v2)
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a > b) return 1
            if (a < b) return -1
        }
        return 0
    }

    /**
     * "1.0.5" / "1.0.5-debug" / "1.0.5-beta1" → [1, 0, 5]
     * Ignore les suffixes (debug, beta, rc…) pour ne pas casser la comparaison.
     */
    private fun normalizeVersion(v: String): List<Int> =
        v.split(".").map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset> = emptyList()
    )

    @Serializable
    data class GitHubAsset(
        val name: String = "",
        val url: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = ""
    )
}

/** Résultat d'une vérification de mise à jour. */
sealed class UpdateCheckResult {
    /** Une nouvelle version est disponible et téléchargeable. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult()

    /** L'app est à jour (ou la release n'a pas d'APK). */
    data object UpToDate : UpdateCheckResult()

    /** Erreur réseau / serveur / parsing : impossible de vérifier. */
    data object Error : UpdateCheckResult()
}

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val publishedAt: String
)
