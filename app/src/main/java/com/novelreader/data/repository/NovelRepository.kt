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
    suspend fun browseNovels(page: Int, genre: String? = null): List<Novel> {
        return source.getBrowseList(page = page, genre = genre)
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

    /** Sauvegarde la liste des chapitres en local. */
    suspend fun cacheChapters(
        novelSlug: String,
        chapters: List<ChapterPreview>,
        novelTitle: String = ""  // Titre lisible pour l'historique
    ) {
        val entities = chapters.map { preview ->
            ChapterEntity(
                id = chapterId(novelSlug, preview.chapterNumber),
                novelSlug = novelSlug,
                novelTitle = novelTitle,
                chapterNumber = preview.chapterNumber,
                title = preview.title,
                url = preview.url,
                publishedAt = preview.publishedAt
            )
        }
        chapterDao.insertChapters(entities)
    }

    /** Marque un chapitre comme lu. */
    suspend fun markChapterAsRead(chapterId: String) {
        chapterDao.markAsRead(chapterId)
    }

    /** Marque un chapitre comme non lu. */
    suspend fun markChapterAsUnread(chapterId: String) {
        chapterDao.markAsUnread(chapterId)
    }

    /** Sauvegarde la position de scroll pour reprise de lecture. */
    suspend fun saveScrollPosition(chapterId: String, position: Int) {
        chapterDao.updateScrollPosition(chapterId, position)
    }

    /** Historique récent (30 derniers chapitres lus). */
    fun getRecentHistory(): Flow<List<ChapterEntity>> {
        return chapterDao.getRecentHistory(limit = 30)
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
