package com.novelreader.data.remote.source

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel

/**
 * Interface "Source" pour l'extensibilité future.
 * Chaque source de novels (NovelFrance, bientôt d'autres) implémentera cette interface.
 *
 * Inspiré du pattern utilisé par Mihon/Tachiyomi.
 * Pour le MVP, seule NovelFranceSource l'implémente.
 */
interface NovelSource {

    /** Nom lisible de la source (ex: "NovelFrance") */
    val name: String

    /** URL de base de la source (ex: "https://novelfrance.fr") */
    val baseUrl: String

    /**
     * Récupère les derniers chapitres sortis (flux "mises à jour").
     * Correspond à la page /latest du site.
     */
    suspend fun getLatestUpdates(page: Int): List<ChapterPreview>

    /**
     * Recherche des novels par titre.
     * Correspond à la page /browse?search=... ou l'API /api/novels?search=...
     */
    suspend fun search(query: String, page: Int): List<Novel>

    /**
     * Parcourir les novels (page paginée).
     * Correspond à /api/novels?page=N&limit=20
     */
    suspend fun getBrowseList(page: Int, genre: String? = null, status: String? = null): List<Novel>

    /**
     * Récupère les détails complets d'un novel.
     * Correspond à /api/novels/{slug}
     */
    suspend fun getNovelDetails(novelSlug: String): Novel

    /**
     * Récupère la liste des chapitres d'un novel.
     * Les données sont extraites de la page HTML /novel/{slug}
     */
    suspend fun getChapterList(novelSlug: String): List<ChapterPreview>

    /**
     * Récupère le contenu texte d'un chapitre.
     * Les données sont extraites de la page HTML /novel/{slug}/chapter-{n}
     */
    suspend fun getChapterContent(chapterUrl: String): ChapterContent
}
