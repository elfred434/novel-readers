package com.novelreader.data.remote.novelfrance

import com.novelreader.data.model.ChapterPreview
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
 *   GET /api/chapters/{slug}?skip=N&take=N&order=desc
 *
 * L'API /api/chapters/{slug} utilise des cookies de session. Fonctionne
 * avec un client HTTP standard (OkHttp) qui maintient les cookies.
 */
class NovelFranceApi(
    private val client: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://novelfrance.fr"
        private const val CHAPTERS_PAGE_SIZE = 100
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getNovels(
        page: Int = 1, limit: Int = 20,
        search: String? = null, genre: String? = null,
        status: String? = null, sort: String? = null
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
        json.decodeFromString<BrowseResponse>(response).novels.map { it.toDomainModel() }
    }

    suspend fun getNovelDetail(slug: String): Novel = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/novels/$slug"
        json.decodeFromString<NovelDetailResponse>(executeGet(url)).toDomainModel()
    }

    /**
     * Récupère TOUS les chapitres d'un novel via pagination.
     * L'API limite à 100 chapitres par appel, donc on itère.
     */
    suspend fun getChaptersPaginated(slug: String): List<ChapterPreview> = withContext(Dispatchers.IO) {
        val allChapters = mutableListOf<ChapterPreview>()
        var skip = 0
        var hasMore = true

        while (hasMore) {
            val url = "$BASE_URL/api/chapters/$slug?skip=$skip&take=$CHAPTERS_PAGE_SIZE&order=desc"
            val response = executeGet(url)
            val page = json.decodeFromString<ChaptersResponse>(response)

            page.chapters.forEach { raw ->
                allChapters.add(
                    ChapterPreview(
                        id = raw.id ?: "${slug}_${raw.chapterNumber}",
                        novelSlug = slug,
                        chapterNumber = raw.chapterNumber,
                        title = raw.title ?: "Chapitre ${raw.chapterNumber}",
                        url = "$BASE_URL/novel/$slug/${raw.slug ?: "chapter-${raw.chapterNumber}"}",
                        publishedAt = raw.createdAt
                    )
                )
            }

            hasMore = page.hasMore
            skip += CHAPTERS_PAGE_SIZE

            if (page.chapters.isEmpty()) hasMore = false
        }

        allChapters
    }

    private suspend fun executeGet(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw NovelFranceException(response.code, "API HTTP ${response.code} pour $url")
        }
        response.body?.string() ?: throw NovelFranceException(-1, "Réponse vide pour $url")
    }

    private fun buildUrl(path: String, block: MutableMap<String, String>.() -> Unit): String {
        val params = mutableMapOf<String, String>()
        params.block()
        val qs = params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        return "$BASE_URL$path?$qs"
    }

    // ===== Responses =====

    @Serializable
    data class BrowseResponse(val novels: List<ApiNovel>, val total: Int, @SerialName("totalPages") val totalPages: Int, val page: Int)

    @Serializable
    data class ApiGenre(val id: String, val name: String, val slug: String)

    @Serializable
    data class ApiCount(val chapters: Int)

    @Serializable
    data class ApiNovel(
        val id: String, val title: String, val slug: String,
        val description: String? = null, @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null, val status: String? = null, val rating: Double? = null,
        val genres: List<ApiGenre>? = null, @SerialName("_count") val count: ApiCount? = null
    ) {
        fun toDomainModel(): Novel = Novel(
            id = id, slug = slug, title = title,
            author = author ?: "Inconnu",
            coverImageUrl = if (coverImage != null) "https://novelfrance.fr$coverImage" else "",
            synopsis = description ?: "", status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0, genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "https://novelfrance.fr/novel/$slug"
        )
    }

    @Serializable
    data class NovelDetailResponse(
        val id: String, val title: String, val slug: String,
        val description: String? = null, @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null, val status: String? = null, val rating: Double? = null,
        val genres: List<ApiGenre>? = null, @SerialName("_count") val count: ApiCount? = null
    ) {
        fun toDomainModel(): Novel = Novel(
            id = id, slug = slug, title = title,
            author = author ?: "Inconnu",
            coverImageUrl = if (coverImage != null) "https://novelfrance.fr$coverImage" else "",
            synopsis = description ?: "", status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0, genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "https://novelfrance.fr/novel/$slug"
        )
    }

    /** Réponse du endpoint /api/chapters/{slug} */
    @Serializable
    data class ChaptersResponse(
        val chapters: List<ChapterRaw>,
        val total: Int? = null,
        val skip: Int? = null,
        val take: Int? = null,
        val hasMore: Boolean = false
    )

    @Serializable
    data class ChapterRaw(
        val id: String? = null,
        val chapterNumber: Int,
        val title: String? = null,
        val slug: String? = null,
        val createdAt: String? = null
    )
}
