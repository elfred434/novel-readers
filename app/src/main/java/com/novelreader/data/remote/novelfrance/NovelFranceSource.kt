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
 * Utilise l'API REST pour les listes de chapitres (contourne la limite SSR de 50).
 * Utilise le parsing HTML/JSON uniquement pour le contenu des chapitres.
 *
 * AMÉLIORATION : getChapterList() utilise /api/chapters/{slug}?skip=N&take=100
 * avec pagination complète pour charger TOUS les chapitres.
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

    override suspend fun getLatestUpdates(page: Int): List<ChapterPreview> {
        val url = if (page <= 1) "$baseUrl/latest" else "$baseUrl/latest?page=$page"
        return parser.parseLatestUpdates(fetchHtml(url))
    }

    override suspend fun search(query: String, page: Int): List<Novel> {
        return api.getNovels(page = page, limit = 20, search = query)
    }

    override suspend fun getBrowseList(page: Int, genre: String?, status: String?, sort: String?, order: String?): List<Novel> {
        return api.getNovels(page = page, limit = 20, genre = genre, status = status, sort = sort, order = order)
    }

    override suspend fun getNovelDetails(novelSlug: String): Novel {
        return api.getNovelDetail(novelSlug)
    }

    /**
     * Récupère TOUS les chapitres via l'API paginée.
     * Plus de limite de 50 — l'API renvoie 100 chapitres par page.
     * Pour 552 chapitres (ORV), ça fait 6 appels API.
     */
    override suspend fun getChapterList(novelSlug: String): List<ChapterPreview> {
        return api.getChaptersPaginated(novelSlug)
    }

    override suspend fun getChapterContent(chapterUrl: String): ChapterContent {
        return parser.parseChapterContent(fetchHtml(chapterUrl), chapterUrl)
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
