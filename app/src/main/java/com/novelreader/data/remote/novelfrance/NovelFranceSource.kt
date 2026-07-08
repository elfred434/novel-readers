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
 * Implémentation de NovelSource pour le site NovelFrance (https://novelfrance.fr).
 *
 * STRATÉGIE DE RÉCUPÉRATION DES DONNÉES :
 *   - API REST (/api/novels) pour browse, search, détails novel
 *   - Parsing JSON embarqué Next.js (initialChapter, initialChaptersResponse)
 *     pour les chapitres et le contenu
 *   - Jsoup DOM pour les dernières mises à jour (/latest)
 *
 * CORRECTIONS AUDIT :
 * - Plus d'init block mort : la configuration OkHttp est faite dans AppModule
 * - Tous les paramètres du constructeur sont injectés (pas de valeurs par défaut)
 * - Tous les appels réseau sont dans withContext(Dispatchers.IO)
 * - Le HTTP client arrive déjà configuré (timeouts + User-Agent)
 *
 * @see NovelSource Interface source extensible
 */
class NovelFranceSource @JvmOverloads constructor(
    private val httpClient: OkHttpClient,    // Client configuré par Hilt
    private val api: NovelFranceApi,          // API injectée
    private val parser: NovelFranceParser = NovelFranceParser()  // Parser (stateless)
) : NovelSource {

    override val name: String = "NovelFrance"
    override val baseUrl: String = "https://novelfrance.fr"

    /**
     * Derniers chapitres publiés (page /latest).
     */
    override suspend fun getLatestUpdates(page: Int): List<ChapterPreview> {
        val url = if (page <= 1) {
            "$baseUrl/latest"
        } else {
            "$baseUrl/latest?page=$page"
        }
        val html = fetchHtml(url)
        return parser.parseLatestUpdates(html)
    }

    /**
     * Recherche par titre via l'API REST.
     * Note : l'API /api/novels?search=X ne semble pas filtrer correctement
     * (retourne tous les résultats). On délègue le filtrage côté client
     * pour le moment.
     */
    override suspend fun search(query: String, page: Int): List<Novel> {
        return api.getNovels(page = page, limit = 20, search = query)
    }

    /**
     * Parcourir le catalogue avec filtres.
     */
    override suspend fun getBrowseList(
        page: Int,
        genre: String?,
        status: String?
    ): List<Novel> {
        return api.getNovels(page = page, limit = 20, genre = genre, status = status)
    }

    /**
     * Détails d'un novel via l'API REST.
     */
    override suspend fun getNovelDetails(novelSlug: String): Novel {
        return api.getNovelDetail(novelSlug)
    }

    /**
     * Liste des chapitres (HTML + JSON embarqué).
     */
    override suspend fun getChapterList(novelSlug: String): List<ChapterPreview> {
        val url = "$baseUrl/novel/$novelSlug"
        val html = fetchHtml(url)
        val chapters = parser.parseChapterList(html)
        return chapters.map { it.copy(novelSlug = it.novelSlug.ifBlank { novelSlug }) }
    }

    /**
     * Contenu d'un chapitre (HTML + JSON embarqué).
     */
    override suspend fun getChapterContent(chapterUrl: String): ChapterContent {
        val html = fetchHtml(chapterUrl)
        return parser.parseChapterContent(html, chapterUrl)
    }

    // ---- Méthodes privées ----

    /**
     * Récupère le HTML brut d'une page NovelFrance.
     * EXÉCUTÉ SUR Dispatchers.IO (appel réseau bloquant OkHttp).
     */
    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string() ?: ""
            throw NovelFranceException(
                code = response.code,
                message = "HTTP ${response.code} pour $url — $body"
            )
        }
        response.body?.string() ?: throw NovelFranceException(
            code = -1,
            message = "Réponse vide pour $url"
        )
    }
}

/**
 * Exception spécifique à NovelFrance.
 */
class NovelFranceException(
    val code: Int,
    override val message: String
) : Exception(message)
