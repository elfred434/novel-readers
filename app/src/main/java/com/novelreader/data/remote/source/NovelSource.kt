package com.novelreader.data.remote.source

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel

/**
 * Interface Source — version améliorée inspirée de Mihon.
 *
 * Chaque source de novels implémente cette interface.
 * Les sources peuvent être chargées dynamiquement (ExtensionManager)
 * ou compilées directement dans l'APK (comme NovelFranceSource pour le MVP).
 */
interface NovelSource {

    /** Identifiant unique de la source */
    val id: Long

    /** Nom lisible (ex: "NovelFrance") */
    val name: String

    /** URL de base (ex: "https://novelfrance.fr") */
    val baseUrl: String

    /** Code langue ISO (ex: "fr", "en", "all") */
    val lang: String

    /** URL d'une icône représentative (optionnelle) */
    val iconUrl: String?

    /** Version du code source (pour mises à jour) */
    val version: Int

    /** Supporte le flux "derniers chapitres" (/latest) */
    val supportsLatest: Boolean

    /** Récupère les derniers chapitres publiés */
    suspend fun getLatestUpdates(page: Int): List<ChapterPreview>

    /** Recherche des novels par titre */
    suspend fun search(query: String, page: Int): List<Novel>

    /** Parcourir le catalogue paginé */
    suspend fun getBrowseList(page: Int, genre: String? = null, status: String? = null, sort: String? = null, order: String? = null): List<Novel>

    /** Détails complets d'un novel */
    suspend fun getNovelDetails(novelSlug: String): Novel

    /** Liste des chapitres d'un novel */
    suspend fun getChapterList(novelSlug: String): List<ChapterPreview>

    /** Contenu texte d'un chapitre */
    suspend fun getChapterContent(chapterUrl: String): ChapterContent

    /** Convertit cette source en métadonnées pour l'affichage */
    fun toExtensionInfo() = com.novelreader.data.extension.ExtensionInfo(
        id = id,
        name = name,
        baseUrl = baseUrl,
        lang = lang,
        iconUrl = iconUrl,
        version = version,
        supportsLatest = supportsLatest
    )
}
