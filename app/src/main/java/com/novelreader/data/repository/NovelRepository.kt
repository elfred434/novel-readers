package com.novelreader.data.repository

import com.novelreader.data.local.dao.ChapterContentDao
import com.novelreader.data.local.dao.ChapterDao
import com.novelreader.data.local.dao.NovelDao
import com.novelreader.data.local.entity.ChapterContentEntity
import com.novelreader.data.local.entity.ChapterEntity
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel
import com.novelreader.data.remote.source.NovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository central — pont entre le réseau (NovelSource) et la base locale (Room).
 *
 * CORRECTIONS AUDIT :
 * - Dépend de l'interface NovelSource (pas de la classe NovelFranceSource concrète)
 * - downloadChapter() met à jour isDownloaded sur le ChapterEntity
 * - getCachedChapter() stocke et restitue les titres du chapitre
 * - Les IDs de chapitres sont cohérents (format "novelSlug_chapterNumber")
 *
 * @property source La source active (injectée via Hilt, typiquement NovelFranceSource)
 */
@Singleton
class NovelRepository @Inject constructor(
    private val source: NovelSource,          // Dépend de l'INTERFACE, pas de l'implémentation
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val chapterContentDao: ChapterContentDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ===================== Opérations réseau =====================

    /** Parcourir les novels depuis la source. */
    suspend fun browseNovels(page: Int, genre: String? = null, status: String? = null, sort: String? = null, order: String? = null): List<Novel> {
        return source.getBrowseList(page = page, genre = genre, status = status, sort = sort, order = order)
    }

    /** Rechercher des novels. */
    suspend fun searchNovels(query: String, page: Int = 1): List<Novel> {
        return source.search(query, page)
    }

    /** Dernières mises à jour depuis la source. */
    suspend fun getLatestUpdates(page: Int = 1): List<ChapterPreview> {
        return source.getLatestUpdates(page)
    }

    /** Détails d'un novel depuis la source. */
    suspend fun getNovelDetails(slug: String): Novel {
        return source.getNovelDetails(slug)
    }

    /** Liste des chapitres d'un novel depuis la source. */
    suspend fun getChapterList(slug: String): List<ChapterPreview> {
        return source.getChapterList(slug)
    }

    /** Contenu d'un chapitre depuis la source. */
    suspend fun getChapterContent(url: String): ChapterContent {
        return source.getChapterContent(url)
    }

    // ===================== Bibliothèque locale (Room) =====================

    /** Retourne la liste des novels dans la bibliothèque (Flow réactif). */
    fun getAllLibraryNovels(): Flow<List<NovelEntity>> {
        return novelDao.getAllNovels()
    }

    /** Ajoute un novel à la bibliothèque. */
    suspend fun addNovelToLibrary(novel: Novel) {
        val entity = NovelEntity(
            slug = novel.slug,
            title = novel.title,
            author = novel.author,
            coverImageUrl = novel.coverImageUrl,
            synopsis = novel.synopsis,
            status = novel.status.name,
            rating = novel.rating,
            genres = novel.genres,  // Liste passée directement → TypeConverter Room gère la sérialisation JSON
            sourceUrl = novel.sourceUrl
        )
        novelDao.insertNovel(entity)
    }

    /** Retire un novel de la bibliothèque (cascade supprime aussi ses chapitres). */
    suspend fun removeNovelFromLibrary(slug: String) {
        novelDao.deleteNovelBySlug(slug)
    }

    /** Vérifie si un novel est dans la bibliothèque. */
    suspend fun isNovelInLibrary(slug: String): Boolean {
        return novelDao.getNovelBySlug(slug) != null
    }

    /** Récupère un novel depuis la base locale (hors-ligne). */
    suspend fun getLocalNovelBySlug(slug: String): NovelEntity? {
        return novelDao.getNovelBySlug(slug)
    }

    /** Récupère les chapitres d'un novel depuis la base locale. */
    suspend fun getChaptersFromDb(slug: String): List<ChapterEntity> {
        return chapterDao.getChaptersForNovelOnce(slug)
    }

    // ===================== Chapitres locaux =====================

    /** Récupère les chapitres d'un novel depuis le cache local (Flow réactif). */
    fun getLocalChapters(novelSlug: String): Flow<List<ChapterEntity>> {
        return chapterDao.getChaptersForNovel(novelSlug)
    }

    /**
     * Sauvegarde la liste des chapitres en local.
     *
     * @param addedAt timestamp local d'ajout : > 0 signifie « détecté par une mise à jour »
     *                (visible dans l'onglet Mises à jour), 0 = chargement initial du novel
     *                (ajout en bibliothèque, non considéré comme une mise à jour).
     */
    /**
     * Sauvegarde la liste des chapitres en local (UPSERT).
     *
     * - Les NOUVEAUX chapitres sont insérés (avec `addedAt` si fourni).
     * - Les chapitres EXISTANTS ne voient que leurs métadonnées mises à jour
     *   (titre, URL, date) : `isRead`, `readAt`, `scrollPosition`, `isDownloaded`
     *   et `addedAt` sont PRÉSERVÉS (l'historique de lecture n'est plus effacé
     *   à chaque rafraîchissement).
     *
     * @param addedAt timestamp local d'ajout : > 0 signifie « détecté par une mise à jour »
     *                (visible dans l'onglet Mises à jour), 0 = chargement initial du novel
     *                (ajout en bibliothèque, non considéré comme une mise à jour).
     */
    suspend fun cacheChapters(
        novelSlug: String,
        chapters: List<ChapterPreview>,
        novelTitle: String = "",   // Titre lisible pour l'historique
        addedAt: Long = 0
    ) {
        val existing = chapterDao.getChaptersForNovelOnce(novelSlug).associateBy { it.id }
        val toInsert = mutableListOf<ChapterEntity>()
        val toUpdate = mutableListOf<ChapterEntity>()

        for (preview in chapters) {
            val id = chapterId(novelSlug, preview.chapterNumber)
            val old = existing[id]
            if (old != null) {
                // Mise à jour des métadonnées uniquement — état de lecture préservé
                toUpdate.add(
                    old.copy(
                        title = preview.title,
                        url = preview.url,
                        publishedAt = preview.publishedAt,
                        novelTitle = if (novelTitle.isNotBlank()) novelTitle else old.novelTitle
                    )
                )
            } else {
                toInsert.add(
                    ChapterEntity(
                        id = id,
                        novelSlug = novelSlug,
                        novelTitle = novelTitle,
                        chapterNumber = preview.chapterNumber,
                        title = preview.title,
                        url = preview.url,
                        publishedAt = preview.publishedAt,
                        addedAt = addedAt
                    )
                )
            }
        }

        chapterDao.upsertChapters(toInsert, toUpdate)
    }

    /** Marque un chapitre comme lu (et recalcule le badge non-lu si slug fourni). */
    suspend fun markChapterAsRead(chapterId: String, slug: String? = null) {
        chapterDao.markAsRead(chapterId)
        slug?.let { refreshUnreadCount(it) }
    }

    /** Marque un chapitre comme non lu (et recalcule le badge non-lu si slug fourni). */
    suspend fun markChapterAsUnread(chapterId: String, slug: String? = null) {
        chapterDao.markAsUnread(chapterId)
        slug?.let { refreshUnreadCount(it) }
    }

    /** Sauvegarde la position de scroll pour reprise de lecture. */
    suspend fun saveScrollPosition(chapterId: String, position: Int) {
        chapterDao.updateScrollPosition(chapterId, position)
    }

    /** Historique récent (30 derniers chapitres lus). */
    fun getRecentHistory(): Flow<List<ChapterEntity>> {
        return chapterDao.getRecentHistory(limit = 30)
    }

    // ===================== Mises à jour des chapitres =====================

    /**
     * Vérifie si de nouveaux chapitres sont disponibles pour un novel de la
     * bibliothèque : récupère la liste distante, la compare aux chapitres
     * locaux, met en cache les nouveaux et recalcule le compteur de non-lus.
     *
     * Logique partagée entre le worker périodique et le worker individuel.
     *
     * @return la liste des chapitres nouvellement découverts (vide si aucun)
     */
    suspend fun updateLibraryNovelChapters(slug: String, novelTitle: String = ""): List<ChapterPreview> {
        val localNumbers = chapterDao.getChaptersForNovelOnce(slug).map { it.chapterNumber }.toSet()
        // Requête optimisée (pagination bornée) : 1 appel API en général
        val newChapters = source.getNewChaptersSince(slug, localNumbers)
        if (newChapters.isNotEmpty()) {
            // addedAt > 0 → ces chapitres apparaissent dans l'onglet Mises à jour
            cacheChapters(slug, newChapters, novelTitle, addedAt = System.currentTimeMillis())
            refreshUnreadCount(slug)
        }
        return newChapters
    }

    /** Flux réactif des chapitres détectés par les mises à jour (onglet Mises à jour). */
    fun getLibraryUpdates(limit: Int = 50): Flow<List<ChapterEntity>> {
        return chapterDao.getLibraryUpdatesFlow(limit)
    }

    /**
     * Recalcule le compteur de chapitres non lus d'un novel à partir des
     * chapitres connus en local (source de vérité unique : la table chapters).
     * À appeler après un ajout de novel, une lecture, ou une détection de
     * nouveaux chapitres.
     */
    suspend fun refreshUnreadCount(slug: String) {
        novelDao.updateUnreadCount(slug, chapterDao.getUnreadCount(slug))
    }

    // ===================== Cache hors-ligne =====================

    /**
     * Télécharge un chapitre pour lecture hors-ligne.
     */
    suspend fun downloadChapter(chapterId: String, content: ChapterContent) {
        // Stocker les métadonnées et paragraphes dans le cache
        val meta = ChapterMeta(content.chapterTitle, content.novelTitle)
        val storageJson = json.encodeToString(StorageContent(meta, content.paragraphs.map {
            SerializableParagraph(it.index, it.htmlContent)
        }))

        chapterContentDao.insertChapterContent(
            ChapterContentEntity(
                chapterId = chapterId,
                paragraphsJson = storageJson
            )
        )

        // Marquer le chapitre comme téléchargé dans la table chapters
        chapterDao.getChapterById(chapterId)?.let { chapter ->
            chapterDao.updateChapter(chapter.copy(isDownloaded = true))
        }
    }

    /**
     * Récupère un chapitre depuis le cache.
     *
     * CORRECTION : on stocke ET restitue les titres du chapitre et du novel.
     */
    suspend fun getCachedChapter(chapterId: String): ChapterContent? {
        val entity = chapterContentDao.getChapterContent(chapterId) ?: return null

        return try {
            val storage = json.decodeFromString<StorageContent>(entity.paragraphsJson)
            ChapterContent(
                chapterTitle = storage.meta.chapterTitle,
                novelTitle = storage.meta.novelTitle,
                paragraphs = storage.paragraphs.map { it.toDomain() }
            )
        } catch (e: Exception) {
            // Fallback pour les anciens caches (avant correction)
            try {
                val paragraphs = json.decodeFromString<List<SerializableParagraph>>(entity.paragraphsJson)
                ChapterContent(
                    chapterTitle = "",  // Perdu — sera rafraîchi au prochain téléchargement
                    novelTitle = "",
                    paragraphs = paragraphs.map { it.toDomain() }
                )
            } catch (e2: Exception) {
                null
            }
        }
    }

    /** Nombre de chapitres en cache local. */
    suspend fun getCachedCount(): Int {
        return chapterContentDao.getCachedChapterCount()
    }

    /** Vide tous les chapitres téléchargés et réinitialise les flags isDownloaded. */
    suspend fun clearCache() {
        chapterContentDao.deleteOldContent(System.currentTimeMillis() + 1)
        chapterDao.resetAllDownloadedFlags()
    }

    // ===================== Suppression de téléchargements =====================

    /**
     * Supprime les données d'un chapitre téléchargé (cache + flag).
     */
    suspend fun deleteDownloadedChapterData(slug: String, chapterNumber: Int) {
        val chapterId = chapterId(slug, chapterNumber)
        chapterContentDao.deleteChapterContent(chapterId)
        chapterDao.resetDownloadedFlags(listOf(chapterId))
    }

    /**
     * Supprime les données de plusieurs chapitres téléchargés (cache + flags).
     */
    suspend fun deleteMultipleDownloadedChapters(slug: String, chapterNumbers: List<Int>) {
        val chapterIds = chapterNumbers.map { chapterId(slug, it) }
        chapterContentDao.deleteMultipleChapterContents(chapterIds)
        chapterDao.resetDownloadedFlags(chapterIds)
    }

    // ===================== Utilitaires =====================

    /**
     * Génère un ID de chapitre cohérent.
     * Format : "novelSlug_chapterNumber" (ex: "omniscient-readers-viewpoint_42")
     */
    companion object {
        fun chapterId(novelSlug: String, chapterNumber: Int): String {
            return "${novelSlug}_$chapterNumber"
        }
    }
}

// ===================== Modèles de sérialisation pour le cache =====================

/**
 * Métadonnées d'un chapitre stocké en cache.
 */
@Serializable
data class ChapterMeta(
    val chapterTitle: String,
    val novelTitle: String
)

/**
 * Contenu complet d'un chapitre pour le cache.
 * Inclut les métadonnées (titres) + les paragraphes.
 */
@Serializable
data class StorageContent(
    val meta: ChapterMeta,
    val paragraphs: List<SerializableParagraph>
)

@Serializable
data class SerializableParagraph(
    val index: Int,
    val htmlContent: String
) {
    fun toDomain() = com.novelreader.data.model.Paragraph(index, htmlContent)
}
