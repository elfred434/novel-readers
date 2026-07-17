package com.novelreader.data.remote.novelfrance

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel
import com.novelreader.data.model.NovelStatus
import com.novelreader.data.model.Paragraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client HTTP pour l'API REST de NovelFrance (https://novelfrance.fr).
 *
 * CARTE DES ENDPOINTS — vérifiée contre le site réel (17/07/2026) :
 *
 *   GET /api/novels?page=N&limit=20&genres=SLUG&status=X&sort=X&order=X
 *       Paramètres FONCTIONNELS : page, limit, genres (slug, PLURIEL),
 *       status (ONGOING/COMPLETED/HIATUS), sort ∈ {popular, rating}.
 *       Paramètres IGNORÉS par le serveur (NE PAS utiliser) :
 *       search, genre (singulier), q, type, sort=views/title/createdAt/updatedAt.
 *       Tri par défaut : plus récemment créés d'abord.
 *
 *   GET /api/search?q=QUERY&page=N&limit=20
 *       Recherche plein-texte (titres). Retourne {novels, total, hasMore}.
 *       Tri toujours par popularité (views desc) — sort/order ignorés.
 *
 *   GET /api/novels/{slug}
 *       Détail complet : firstChapter, tags, allTimeRank, totalViews…
 *
 *   GET /api/chapters/{slug}?skip=N&take=100&order=desc
 *       Liste paginée des chapitres d'un novel.
 *
 *   GET /api/chapters/{novelSlug}/{chapterSlug}
 *       Contenu COMPLET d'un chapitre en JSON (paragraphs, prev/next).
 *       Évite le parsing du flux Next.js RSC des pages HTML.
 *
 *   GET /api/chapters/latest?skip=N&take=20
 *       Derniers chapitres publiés, toutes sources confondues,
 *       avec les infos du novel associé. Remplace le scraping de /latest.
 *
 *   GET /api/genres
 *       Liste des genres avec compteurs (_count.novels) pour les filtres.
 */
class NovelFranceApi(
    private val client: OkHttpClient
) {

    companion object {
        private const val BASE_URL = "https://novelfrance.fr"
        private const val CHAPTERS_PAGE_SIZE = 100
        const val LATEST_PAGE_SIZE = 20
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ===================== Catalogue (/api/novels) =====================

    /**
     * Parcourt le catalogue.
     *
     * @param genre  SLUG de genre (ex: "action") — transmis via le paramètre
     *               `genres` (pluriel), le seul accepté par le serveur.
     * @param status "ONGOING", "COMPLETED", "HIATUS"…
     * @param sort   Seules valeurs effectives côté serveur : "popular", "rating".
     *               Toute autre valeur est ignorée (tri par défaut).
     */
    suspend fun getNovels(
        page: Int = 1, limit: Int = 20,
        genre: String? = null,
        status: String? = null, sort: String? = null,
        order: String? = null
    ): List<Novel> = withContext(Dispatchers.IO) {
        val url = buildUrl("/api/novels") {
            put("page", page.toString())
            put("limit", limit.coerceIn(1, 50).toString())
            genre?.let { put("genres", it) }   // NB : paramètre PLURIEL (singulier ignoré)
            status?.let { put("status", it) }
            sort?.let { put("sort", it) }
            order?.let { put("order", it) }
        }
        val response = executeGet(url)
        json.decodeFromString<BrowseResponse>(response).novels.map { it.toDomainModel() }
    }

    /**
     * Recherche plein-texte — endpoint DÉDIÉ /api/search?q=…
     * (/api/novels?search=… existe mais le paramètre est IGNORÉ par le serveur :
     * il retourne le catalogue entier non filtré.)
     */
    suspend fun searchNovels(query: String, page: Int = 1, limit: Int = 20): List<Novel> =
        withContext(Dispatchers.IO) {
            val url = buildUrl("/api/search") {
                put("q", query)
                put("page", page.toString())
                put("limit", limit.coerceIn(1, 50).toString())
            }
            val response = executeGet(url)
            json.decodeFromString<SearchResponse>(response).novels.map { it.toDomainModel() }
        }

    /** Liste complète des genres (avec compteurs) pour les filtres de l'UI. */
    suspend fun getGenres(): List<GenreInfo> = withContext(Dispatchers.IO) {
        val response = executeGet("$BASE_URL/api/genres")
        json.decodeFromString<List<GenreRaw>>(response).map {
            GenreInfo(name = it.name, slug = it.slug, novelCount = it.count?.novels ?: 0)
        }
    }

    /** Détail d'un novel. */
    suspend fun getNovelDetail(slug: String): Novel = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/novels/$slug"
        json.decodeFromString<NovelDetailResponse>(executeGet(url)).toDomainModel()
    }

    // ===================== Chapitres =====================

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

            page.chapters.forEach { raw ->
                allChapters.add(
                    ChapterPreview(
                        id = raw.id ?: "${slug}_${raw.chapterNumber}",
                        novelSlug = slug,
                        chapterNumber = raw.chapterNumber,
                        title = raw.title ?: "Chapitre ${raw.chapterNumber}",
                        url = "$BASE_URL/novel/$slug/${raw.slug ?: "chapter-${raw.chapterNumber}"}",
                        publishedAt = raw.createdAt,
                        wordCount = raw.wordCount
                    )
                )
            }

            hasMore = page.hasMore
            skip += CHAPTERS_PAGE_SIZE

            if (page.chapters.isEmpty()) hasMore = false
        }

        allChapters.reversed()
    }

    /**
     * Contenu complet d'un chapitre via l'API JSON dédiée.
     * Bien plus fiable que le parsing du flux Next.js RSC des pages HTML.
     */
    suspend fun getChapterContent(novelSlug: String, chapterSlug: String): ChapterContent =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/api/chapters/$novelSlug/$chapterSlug"
            val raw = json.decodeFromString<ChapterContentResponse>(executeGet(url))
            raw.toDomainModel()
        }

    /**
     * Derniers chapitres publiés (flux « Nouveaux chapitres » du site).
     * Remplace avantageusement le scraping HTML de /latest.
     */
    suspend fun getLatestChapters(skip: Int = 0, take: Int = LATEST_PAGE_SIZE): List<ChapterPreview> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/api/chapters/latest?skip=$skip&take=$take"
            val response = json.decodeFromString<LatestChaptersResponse>(executeGet(url))
            response.data.map { it.toDomainModel() }
        }

    // ===================== HTTP bas niveau =====================

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
        if (params.isEmpty()) return "$BASE_URL$path"
        val qs = params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        return "$BASE_URL$path?$qs"
    }

    /** Normalise une URL de couverture : préfixe les chemins relatifs, garde les absolues. */
    private fun absoluteCoverUrl(coverImage: String?): String = when {
        coverImage.isNullOrBlank() -> ""
        coverImage.startsWith("http://") || coverImage.startsWith("https://") -> coverImage
        else -> "$BASE_URL$coverImage"
    }

    // ===================== DTO — /api/novels & /api/search =====================

    @Serializable
    data class BrowseResponse(val novels: List<ApiNovel>, val total: Int, @SerialName("totalPages") val totalPages: Int, val page: Int)

    /** Réponse de /api/search : {novels, total, hasMore} (pas de totalPages). */
    @Serializable
    data class SearchResponse(
        val novels: List<ApiNovel>,
        val total: Int = 0,
        val hasMore: Boolean = false
    )

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
            coverImageUrl = absoluteCoverUrl(coverImage),
            synopsis = description ?: "", status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0, ratingCount = ratingCount ?: 0,
            views = views ?: 0, bookmarkCount = bookmarkCount ?: 0,
            type = type ?: "", year = year,
            alternativeTitles = alternativeTitles ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "$BASE_URL/novel/$slug"
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
            coverImageUrl = absoluteCoverUrl(coverImage),
            synopsis = description ?: "", status = NovelStatus.fromString(status ?: ""),
            rating = rating ?: 0.0, ratingCount = ratingCount ?: 0,
            views = views ?: 0, bookmarkCount = bookmarkCount ?: 0,
            type = type ?: "", year = year,
            alternativeTitles = alternativeTitles ?: "",
            genres = genres?.map { it.name } ?: emptyList(),
            chapterCount = count?.chapters ?: 0,
            sourceUrl = "$BASE_URL/novel/$slug",
            firstChapterSlug = firstChapter?.slug,
            allTimeRank = allTimeRank,
            totalViews = totalViews ?: pageViews,
            tags = tags?.mapNotNull { it.name } ?: emptyList()
        )
    }

    // ===================== DTO — /api/genres =====================

    /** Genre tel qu'exposé à l'UI (avec compteur de novels). */
    data class GenreInfo(val name: String, val slug: String, val novelCount: Int)

    @Serializable
    private data class GenreRaw(
        val id: String? = null,
        val name: String,
        val slug: String,
        @SerialName("_count") val count: GenreCount? = null
    )

    @Serializable
    private data class GenreCount(val novels: Int? = 0)

    // ===================== DTO — /api/chapters =====================

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
    )

    /** Réponse de /api/chapters/{novelSlug}/{chapterSlug} — contenu complet. */
    @Serializable
    data class ChapterContentResponse(
        val id: String? = null,
        val chapterNumber: Int? = null,
        val title: String? = null,
        val slug: String? = null,
        val paragraphs: List<ApiParagraph>? = null,
        val wordCount: Int? = null,
        val novel: ApiChapterNovel? = null,
        val prevChapter: ApiPrevNext? = null,
        val nextChapter: ApiPrevNext? = null
    ) {
        fun toDomainModel(): ChapterContent = ChapterContent(
            chapterTitle = title ?: "Chapitre ${chapterNumber ?: ""}".trim(),
            novelTitle = novel?.title ?: "",
            paragraphs = paragraphs
                ?.sortedBy { it.index ?: Int.MAX_VALUE }
                ?.mapIndexed { fallback, p -> Paragraph(index = p.index ?: fallback, htmlContent = p.content ?: "") }
                ?: emptyList(),
            prevChapterUrl = prevChapter?.slug?.let { "$BASE_URL/novel/${novel?.slug}/$it" },
            nextChapterUrl = nextChapter?.slug?.let { "$BASE_URL/novel/${novel?.slug}/$it" }
        )
    }

    @Serializable
    data class ApiParagraph(
        val id: String? = null,
        val index: Int? = null,
        val content: String? = null,
        val wordCount: Int? = null
    )

    @Serializable
    data class ApiChapterNovel(
        val id: String? = null,
        val title: String? = null,
        val slug: String? = null,
        val author: String? = null,
        @SerialName("coverImage") val coverImage: String? = null
    )

    @Serializable
    data class ApiPrevNext(
        val slug: String? = null,
        val chapterNumber: Int? = null,
        val title: String? = null
    )

    // ===================== DTO — /api/chapters/latest =====================

    /**
     * Réponse de /api/chapters/latest.
     * Le serveur renvoie la liste en DOUBLE sous les clés "data" et "chapters"
     * (alias identiques) — on lit "data".
     */
    @Serializable
    data class LatestChaptersResponse(
        val data: List<LatestChapterRaw> = emptyList(),
        val total: Int? = null,
        val skip: Int? = null,
        val take: Int? = null,
        val hasMore: Boolean = false
    )

    @Serializable
    data class LatestChapterRaw(
        val id: String? = null,
        val chapterNumber: Int,
        val title: String? = null,
        val slug: String? = null,
        val createdAt: String? = null,
        val novel: LatestNovelInfo? = null
    ) {
        fun toDomainModel(): ChapterPreview {
            val novelSlug = novel?.slug ?: ""
            val chapterSlug = slug ?: "chapter-$chapterNumber"
            return ChapterPreview(
                id = id ?: "${novelSlug}_$chapterNumber",
                novelSlug = novelSlug,
                chapterNumber = chapterNumber,
                title = title ?: "Chapitre $chapterNumber",
                url = "$BASE_URL/novel/$novelSlug/$chapterSlug",
                publishedAt = createdAt,
                novelTitle = novel?.title
            )
        }
    }

    @Serializable
    data class LatestNovelInfo(
        val id: String? = null,
        val title: String? = null,
        val slug: String? = null,
        @SerialName("coverImage") val coverImage: String? = null,
        val author: String? = null,
        val rating: Double? = null
    )
}
