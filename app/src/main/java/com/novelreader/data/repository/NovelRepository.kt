package com.novelreader.data.repository

import com.novelreader.data.extension.ExtensionManager
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
import com.novelreader.data.remote.source.SourceGenre
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository central — pont entre le réseau (NovelSource) et la base locale (Room).
 *
 * CORRECTIONS AUDIT (v2) :
 * - cacheChapters() PRÉSERVE l'état local (isRead, readAt, scrollPosition,
 *   isDownloaded) via un upsert INSERT(IGNORE)+UPDATE au lieu de REPLACE :
 *   l'historique de lecture n'est plus effacé à chaque rafraîchissement,
 *   et la cascade ON DELETE de chapter_content n'est plus déclenchée.
 * - Les opérations réseau vérifient que la source est activée.
 *
 * @property source La source active (injectée via Hilt, typiquement NovelFranceSource)
 */
@Singleton
class NovelRepository @Inject constructor(
    private val source: NovelSource,          // Dépend de l'INTERFACE, pas de l'implémentation
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val chapterContentDao: ChapterContentDao,
    private val extensionManager: ExtensionManager
) {

    private val json = Json { ignoreUnknownKeys = true }

    // ===================== Opérations réseau =====================

    /** Vérifie que la source est activée avant tout appel réseau. */
    private fun requireSourceEnabled() {
        if (!extensionManager.isSourceEnabled(source.id)) {
            throw SourceDisabledException(source.name)
        }
    }

    /** Parcourir les novels depuis la source. */
    suspend fun browseNovels(page: Int, genre: String? = null, status: String? = null, sort: String? = null, order: String? = null): List<Novel> {
        requireSourceEnabled()
        return source.getBrowseList(page = page, genre = genre, status = status, sort = sort, order = order)
    }

    /** Rechercher des novels. */
    suspend fun searchNovels(query: String, page: Int = 1): List<Novel> {
        requireSourceEnabled()
        return source.search(query, page)
    }

    /** Dernières mises à jour depuis la source. */
    suspend fun getLatestUpdates(page: Int = 1): List<ChapterPreview> {
        requireSourceEnabled()
        return source.getLatestUpdates(page)
    }

    /** Détails d'un novel depuis la source. */
    suspend fun getNovelDetails(slug: String): Novel {
        requireSourceEnabled()
        return source.getNovelDetails(slug)
    }

    /**
     * Genres du catalogue avec compteurs (endpoint /api/genres de la source).
     * Utilisé pour les chips de filtre de l'écran Parcourir.
     * Retourne une liste vide si la source ne les expose pas.
     */
    suspend fun getSourceGenres(): List<SourceGenre> {
        requireSourceEnabled()
        return source.getGenres()
    }

    /** Liste des chapitres d'un novel depuis la source. */
    suspend fun getChapterList(slug: String): List<ChapterPreview> {
        requireSourceEnabled()
        return source.getChapterList(slug)
    }

    /** Contenu d'un chapitre depuis la source. */
    suspend fun getChapterContent(url: String): ChapterContent {
        requireSourceEnabled()
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
     * Sauvegarde la liste des chapitres en local SANS perdre l'état de lecture.
     *
     * Pour chaque chapitre distant :
     * - s'il existe déjà → UPDATE en conservant isRead, readAt, scrollPosition,
     *   isDownloaded (seuls les métadonnées distantes sont rafraîchies) ;
     * - sinon → INSERT (IGNORE) d'une nouvelle ligne.
     *
     * L'UPDATE (contrairement à INSERT OR REPLACE) ne supprime pas la ligne,
     * donc la cascade ON DELETE de chapter_content n'est PAS déclenchée et
     * le contenu téléchargé en DB est préservé.
     */
    suspend fun cacheChapters(
        novelSlug: String,
        chapters: List<ChapterPreview>,
        novelTitle: String = ""  // Titre lisible pour l'historique
    ) {
        val existing = chapterDao.getChaptersForNovelOnce(novelSlug).associateBy { it.id }
        val toInsert = mutableListOf<ChapterEntity>()
        val toUpdate = mutableListOf<ChapterEntity>()

        for (preview in chapters) {
            val id = chapterId(novelSlug, preview.chapterNumber)
            val old = existing[id]
            if (old != null) {
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
                        publishedAt = preview.publishedAt
                    )
                )
            }
        }

        chapterDao.upsertChapters(toInsert, toUpdate)
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

    /** Position de scroll sauvegardée d'un chapitre (0 si inconnue). */
    suspend fun getScrollPosition(chapterId: String): Int {
        return chapterDao.getChapterById(chapterId)?.scrollPosition ?: 0
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

/** Levée quand la source a été désactivée dans l'écran Extensions. */
class SourceDisabledException(sourceName: String) :
    Exception("La source « $sourceName » est désactivée. Active-la dans Paramètres → Extensions.")

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
