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
 * Endpoints API :
 *   GET /api/novels?page=N&limit=20&search=X&genre=X&status=X&sort=X&order=X&type=X
 *   GET /api/novels/{slug}
 *   GET /api/chapters/{slug}?skip=N&take=N&order=desc
 */
class NovelFranceApi(
    private val client: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://novelfrance.fr"
        private const val CHAPTERS_PAGE_SIZE = 100

        /** Garde-fou anti-boucle : 10 pages de 100 = 1000 chapitres max par novel. */
        private const val CHAPTERS_MAX_TOTAL = 1000
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getNovels(
        page: Int = 1, limit: Int = 20,
        search: String? = null, genre: String? = null,
        status: String? = null, sort: String? = null,
        order: String? = null, type: String? = null
    ): List<Novel> = withContext(Dispatchers.IO) {
        val url = buildUrl("/api/novels") {
            put("page", page.toString())
            put("limit", limit.coerceIn(1, 50).toString())
            search?.let { put("search", it) }
            genre?.let { put("genre", it) }
            status?.let { put("status", it) }
            sort?.let { put("sort", it) }
            order?.let { put("order", it) }
            type?.let { put("type", it) }
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
     */
    suspend fun getChaptersPaginated(slug: String): List<ChapterPreview> = withContext(Dispatchers.IO) {
        val allChapters = mutableListOf<ChapterPreview>()
        var skip = 0
        var hasMore = true

        while (hasMore) {
            val url = "$BASE_URL/api/chapters/$slug?skip=$skip&take=$CHAPTERS_PAGE_SIZE&order=desc"
            val response = executeGet(url)
            val page = json.decodeFromString<ChaptersResponse>(response)

            page.chapters.forEach { raw -> allChapters.add(raw.toPreview(slug)) }

            hasMore = page.hasMore && page.chapters.isNotEmpty()
            skip += CHAPTERS_PAGE_SIZE
            if (skip > CHAPTERS_MAX_TOTAL) hasMore = false // garde-fou : 10 pages max
        }

        allChapters.reversed()
    }

    /**
     * Derniers chapitres publiés sur le site (endpoint réel : /api/chapters/latest).
     * Riche : titre réel du chapitre + infos du novel (titre, couverture, note).
     * Pagination par skip/take (défaut serveur : 20).
     */
    suspend fun getLatestChapters(skip: Int = 0, take: Int = 20): List<ChapterPreview> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/chapters/latest?skip=${skip.coerceAtLeast(0)}&take=${take.coerceIn(1, 100)}"
        val response = executeGet(url)
        json.decodeFromString<LatestChaptersResponse>(response).data.map { it.toPreview() }
    }

    /**
     * Récupère uniquement les chapitres plus récents que ceux déjà connus.
     * Optimisation : on parcourt l'API par pages décroissantes et on s'arrête
     * dès qu'on atteint le plus haut numéro local connu → 1 seul appel dans
     * la grande majorité des cas (au lieu de télécharger TOUS les chapitres).
     */
    suspend fun getNewChaptersSince(slug: String, knownNumbers: Set<Int>): List<ChapterPreview> = withContext(Dispatchers.IO) {
        if (knownNumbers.isEmpty()) return@withContext getChaptersPaginated(slug)

        val maxKnown = knownNumbers.maxOrNull() ?: 0
        val newChapters = mutableListOf<ChapterPreview>()
        var skip = 0
        var done = false

        while (!done) {
            val url = "$BASE_URL/api/chapters/$slug?skip=$skip&take=$CHAPTERS_PAGE_SIZE&order=desc"
            val page = json.decodeFromString<ChaptersResponse>(executeGet(url))

            for (raw in page.chapters) {
                if (raw.chapterNumber <= maxKnown) { done = true; break }
                newChapters.add(raw.toPreview(slug))
            }

            if (page.chapters.isEmpty() || !page.hasMore || done) break
            skip += CHAPTERS_PAGE_SIZE
            if (skip > CHAPTERS_MAX_TOTAL) break // garde-fou
        }

        newChapters
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
    data class ApiCount(val chapters: Int? = 0, val ratings: Int? = 0, val bookmarks: Int? = 0)

    @Serializable
    data class FirstChapterSlug(val slug: String? = null)

    @Serializable
    data class ApiTag(val id: String? = null, val name: String? = null, val slug: String? = null)

    @Serializable
    data class ApiNovel(
        val id: String, val title: String, val slug: String,
        val description: String? = null, @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null, val status: String? = null, val rating: Double? = null,
        val ratingCount: Int? = null, val views: Int? = null, val bookmarkCount: Int? = null,
        val type: String? = null, val year: Int? = null,
        @SerialName("alternativeTitles") val alternativeTitles: String? = null,
        @SerialName("translatorName") val translatorName: String? = null,
        val genres: List<ApiGenre>? = null, @SerialName("_count") val count: ApiCount? = null
    ) {
        fun toDomainModel(): Novel = Novel(
            id = id, slug = slug, title = title,
            author = author ?: "Inconnu",
            translatorName = translatorName,
            coverImageUrl = if (coverImage != null) "https://novelfrance.fr$coverImage" else "",
            synopsis = description ?: "", status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0, ratingCount = ratingCount ?: 0,
            views = views ?: 0, bookmarkCount = bookmarkCount ?: 0,
            type = type ?: "", year = year,
            alternativeTitles = alternativeTitles ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "https://novelfrance.fr/novel/$slug"
        )
    }

    @Serializable
    data class NovelDetailResponse(
        val id: String, val title: String, val slug: String,
        val description: String? = null, @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null, val status: String? = null, val rating: Double? = null,
        val ratingCount: Int? = null, val views: Int? = null,
        val bookmarkCount: Int? = null, val type: String? = null,
        val year: Int? = null,
        @SerialName("alternativeTitles") val alternativeTitles: String? = null,
        @SerialName("translatorName") val translatorName: String? = null,
        @SerialName("firstChapter") val firstChapter: FirstChapterSlug? = null,
        val allTimeRank: Int? = null,
        val pageViews: Int? = null,
        val chapterViews: Int? = null,
        val totalViews: Int? = null,
        val tags: List<ApiTag>? = null,
        val genres: List<ApiGenre>? = null, @SerialName("_count") val count: ApiCount? = null
    ) {
        fun toDomainModel(): Novel = Novel(
            id = id, slug = slug, title = title,
            author = author ?: "Inconnu",
            translatorName = translatorName,
            coverImageUrl = if (coverImage != null) "https://novelfrance.fr$coverImage" else "",
            synopsis = description ?: "", status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0, ratingCount = ratingCount ?: 0,
            views = views ?: 0, bookmarkCount = bookmarkCount ?: 0,
            type = type ?: "", year = year,
            alternativeTitles = alternativeTitles ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "https://novelfrance.fr/novel/$slug",
            firstChapterSlug = firstChapter?.slug,
            allTimeRank = allTimeRank,
            totalViews = totalViews ?: pageViews,
            tags = tags?.mapNotNull { it.name } ?: emptyList()
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
        val createdAt: String? = null,
        val wordCount: Int? = null
    ) {
        fun toPreview(novelSlug: String): ChapterPreview = ChapterPreview(
            id = id ?: "${novelSlug}_$chapterNumber",
            novelSlug = novelSlug,
            chapterNumber = chapterNumber,
            title = title ?: "Chapitre $chapterNumber",
            url = "$BASE_URL/novel/$novelSlug/${slug ?: "chapter-$chapterNumber"}",
            publishedAt = createdAt,
            wordCount = wordCount
        )
    }

    /** Réponse du endpoint /api/chapters/latest (derniers chapitres publiés). */
    @Serializable
    data class LatestChaptersResponse(
        val data: List<ApiLatestChapter>,
        val total: Int? = null,
        val skip: Int? = null,
        val take: Int? = null,
        val hasMore: Boolean = false
    )

    @Serializable
    data class ApiLatestChapter(
        val id: String? = null,
        val novelId: String? = null,
        val chapterNumber: Int,
        val title: String? = null,
        val slug: String? = null,
        val createdAt: String? = null,
        val novel: ApiLatestNovel? = null
    ) {
        fun toPreview(): ChapterPreview {
            val novelSlug = novel?.slug ?: ""
            val chapterSlug = slug ?: "chapter-$chapterNumber"
            return ChapterPreview(
                id = id ?: "${novelSlug}_$chapterNumber",
                novelSlug = novelSlug,
                chapterNumber = chapterNumber,
                title = title ?: "Chapitre $chapterNumber",
                url = "$BASE_URL/novel/$novelSlug/$chapterSlug",
                publishedAt = createdAt,
                novelTitle = novel?.title,
                novelCoverUrl = novel?.coverImage?.let { "$BASE_URL$it" }
            )
        }
    }

    @Serializable
    data class ApiLatestNovel(
        val id: String? = null,
        val title: String? = null,
        val slug: String? = null,
        val coverImage: String? = null,
        val author: String? = null,
        val rating: Double? = null
    )
}
