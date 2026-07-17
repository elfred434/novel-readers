package com.novelreader.data.remote.novelfrance

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel
import com.novelreader.data.remote.source.NovelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Implémentation NovelSource pour NovelFrance (https://novelfrance.fr).
 *
 * ALIGNEMENT SUR L'API RÉELLE (vérifiée le 17/07/2026) :
 * - Recherche → GET /api/search?q=… (le paramètre `search` de /api/novels
 *   est IGNORÉ par le serveur : il retournait le catalogue entier).
 * - Filtre genre → paramètre `genres` (pluriel) de /api/novels.
 * - Dernières sorties → GET /api/chapters/latest (JSON propre, remplace
 *   le scraping fragile de la page /latest — conservé en fallback).
 * - Contenu d'un chapitre → GET /api/chapters/{novelSlug}/{chapterSlug}
 *   (JSON direct, remplace le parsing du flux Next.js RSC — conservé
 *   en fallback si l'API échoue).
 * - Liste des chapitres → GET /api/chapters/{slug} paginé (skip/take).
 */
class NovelFranceSource @JvmOverloads constructor(
    private val httpClient: OkHttpClient,
    private val api: NovelFranceApi,
    private val parser: NovelFranceParser = NovelFranceParser()
) : NovelSource {

    override val id: Long = 1L
    override val name: String = "NovelFrance"
    override val baseUrl: String = "https://novelfrance.fr"
    override val lang: String = "fr"
    override val iconUrl: String? = "https://novelfrance.fr/favicon.ico"
    override val version: Int = 1
    override val supportsLatest: Boolean = true

    /**
     * Derniers chapitres via l'API JSON /api/chapters/latest.
     * Fallback : scraping de la page /latest si l'API est en échec.
     */
    override suspend fun getLatestUpdates(page: Int): List<ChapterPreview> {
        return try {
            val safePage = page.coerceAtLeast(1)
            api.getLatestChapters(
                skip = (safePage - 1) * NovelFranceApi.LATEST_PAGE_SIZE,
                take = NovelFranceApi.LATEST_PAGE_SIZE
            )
        } catch (e: Exception) {
            val url = if (page <= 1) "$baseUrl/latest" else "$baseUrl/latest?page=$page"
            parser.parseLatestUpdates(fetchHtml(url))
        }
    }

    /** Recherche plein-texte via l'endpoint dédié /api/search. */
    override suspend fun search(query: String, page: Int): List<Novel> {
        return api.searchNovels(query = query, page = page, limit = 20)
    }

    override suspend fun getBrowseList(page: Int, genre: String?, status: String?, sort: String?, order: String?): List<Novel> {
        return api.getNovels(page = page, limit = 20, genre = genre, status = status, sort = sort, order = order)
    }

    override suspend fun getNovelDetails(novelSlug: String): Novel {
        return api.getNovelDetail(novelSlug)
    }

    /** Genres du catalogue (GET /api/genres) pour les chips de filtre. */
    override suspend fun getGenres(): List<com.novelreader.data.remote.source.SourceGenre> {
        return api.getGenres().map {
            com.novelreader.data.remote.source.SourceGenre(
                name = it.name,
                slug = it.slug,
                novelCount = it.novelCount
            )
        }
    }

    /**
     * Récupère TOUS les chapitres via l'API paginée.
     * Pour 552 chapitres (ORV), ça fait 6 appels API de 100.
     */
    override suspend fun getChapterList(novelSlug: String): List<ChapterPreview> {
        return api.getChaptersPaginated(novelSlug)
    }

    /**
     * Contenu d'un chapitre via l'API JSON /api/chapters/{novel}/{chapter}.
     * Fallback : parsing HTML de la page (flux Next.js RSC puis DOM Jsoup).
     */
    override suspend fun getChapterContent(chapterUrl: String): ChapterContent {
        val slugs = extractSlugs(chapterUrl)
        if (slugs != null) {
            try {
                return api.getChapterContent(slugs.first, slugs.second)
            } catch (e: Exception) {
                // Fallback HTML ci-dessous
            }
        }
        return parser.parseChapterContent(fetchHtml(chapterUrl), chapterUrl)
    }

    /**
     * Extrait (novelSlug, chapterSlug) d'une URL du type
     * https://novelfrance.fr/novel/{novelSlug}/{chapterSlug}
     */
    private fun extractSlugs(chapterUrl: String): Pair<String, String>? {
        val match = Regex("/novel/([^/]+)/([^/?#]+)").find(chapterUrl) ?: return null
        val novelSlug = match.groupValues[1]
        val chapterSlug = match.groupValues[2]
        if (novelSlug.isBlank() || chapterSlug.isBlank()) return null
        return novelSlug to chapterSlug
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml").build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw NovelFranceException(response.code, "HTTP ${response.code} pour $url")
        response.body?.string() ?: throw NovelFranceException(-1, "Réponse vide pour $url")
    }
}

class NovelFranceException(val code: Int, override val message: String) : Exception(message)
