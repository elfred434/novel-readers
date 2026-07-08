package com.novelreader.data.remote.novelfrance

import com.novelreader.data.model.Novel
import com.novelreader.data.model.NovelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client HTTP pour l'API REST de NovelFrance.
 *
 * Endpoints API découverts expérimentalement (juillet 2026) :
 *   GET /api/novels?page=N&limit=20&search=X&genre=X&status=X&sort=X
 *   GET /api/novels/{slug}
 *
 * L'API retourne du JSON structuré (contrairement aux pages HTML des chapitres
 * qui nécessitent un parsing du JSON embarqué).
 *
 * CORRECTION AUDIT :
 * - Plus de méthode buildClient() morte : le client arrive déjà configuré de Hilt
 * - Tous les appels réseau sont dans withContext(Dispatchers.IO)
 * - Gestion des erreurs HTTP avec messages explicites
 */
class NovelFranceApi(
    private val client: OkHttpClient  // Client déjà configuré par Hilt
) {

    companion object {
        private const val BASE_URL = "https://novelfrance.fr"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Appelle GET /api/novels avec filtrage et pagination.
     *
     * @param page Numéro de page (1-indexed)
     * @param limit Nombre d'éléments (1-50)
     * @param search Terme de recherche (optionnel — attention: l'API semble ignorer ce paramètre)
     * @param genre Slug du genre
     * @param status "ONGOING" | "COMPLETED"
     * @param sort "latest" | "rating" | "popular"
     */
    suspend fun getNovels(
        page: Int = 1,
        limit: Int = 20,
        search: String? = null,
        genre: String? = null,
        status: String? = null,
        sort: String? = null
    ): List<Novel> = withContext(Dispatchers.IO) {
        val url = buildUrl("/api/novels") {
            put("page", page.toString())
            put("limit", limit.coerceIn(1, 50).toString())
            search?.let { put("search", it) }
            genre?.let { put("genre", it) }
            status?.let { put("status", it) }
            sort?.let { put("sort", it) }
        }

        val response = executeGet(url)
        val apiResponse = json.decodeFromString<BrowseResponse>(response)
        apiResponse.novels.map { it.toDomainModel() }
    }

    /**
     * Appelle GET /api/novels/{slug} pour les détails d'un novel.
     */
    suspend fun getNovelDetail(slug: String): Novel = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/novels/$slug"
        val response = executeGet(url)
        val apiNovel = json.decodeFromString<NovelDetailResponse>(response)
        apiNovel.toDomainModel()
    }

    // ---- Méthodes privées ----

    /**
     * Exécute une requête GET HTTP.
     * Lance une exception avec le code HTTP si la réponse n'est pas 2xx.
     */
    private suspend fun executeGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string() ?: ""
            throw NovelFranceException(
                code = response.code,
                message = "API HTTP ${response.code} pour $url — $body"
            )
        }
        response.body?.string() ?: throw NovelFranceException(
            code = -1,
            message = "Réponse vide pour $url"
        )
    }

    private fun buildUrl(path: String, block: MutableMap<String, String>.() -> Unit): String {
        val params = mutableMapOf<String, String>()
        params.block()
        val queryString = params.entries.joinToString("&") { 
            "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        return "$BASE_URL$path?$queryString"
    }

    // ---- Réponses JSON de l'API ----

    @Serializable
    data class BrowseResponse(
        val novels: List<ApiNovel>,
        val total: Int,
        @SerialName("totalPages") val totalPages: Int,
        val page: Int
    )

    @Serializable
    data class ApiGenre(
        val id: String,
        val name: String,
        val slug: String
    )

    @Serializable
    data class ApiCount(
        val chapters: Int
    )

    @Serializable
    data class ApiNovel(
        val id: String,
        val title: String,
        val slug: String,
        val description: String? = null,
        @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null,
        val status: String? = null,
        val rating: Double? = null,
        val genres: List<ApiGenre>? = null,
        @SerialName("_count") val count: ApiCount? = null
    ) {
        fun toDomainModel(): Novel = Novel(
            id = id,
            slug = slug,
            title = title,
            author = author ?: "Inconnu",
            coverImageUrl = if (coverImage != null) "https://novelfrance.fr$coverImage" else "",
            synopsis = description ?: "",
            status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0,
            genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "https://novelfrance.fr/novel/$slug"
        )
    }

    @Serializable
    data class NovelDetailResponse(
        val id: String,
        val title: String,
        val slug: String,
        val description: String? = null,
        @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null,
        val status: String? = null,
        val rating: Double? = null,
        val genres: List<ApiGenre>? = null,
        @SerialName("_count") val count: ApiCount? = null
    ) {
        fun toDomainModel(): Novel = Novel(
            id = id,
            slug = slug,
            title = title,
            author = author ?: "Inconnu",
            coverImageUrl = if (coverImage != null) "https://novelfrance.fr$coverImage" else "",
            synopsis = description ?: "",
            status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0,
            genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "https://novelfrance.fr/novel/$slug"
        )
    }
}
