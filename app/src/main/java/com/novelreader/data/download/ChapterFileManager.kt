package com.novelreader.data.download

import android.content.Context
import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.Paragraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire de fichiers pour les chapitres téléchargés.
 * Stocke chaque chapitre dans un fichier JSON dans le stockage interne.
 * Les fichiers survivent au cache et sont accessibles hors-ligne.
 *
 * Structure :
 *   {internal storage}/novels/{novelSlug}/{chapterNumber}.json
 *
 * Format JSON : {"chapterTitle":"...", "novelTitle":"...", "paragraphs":[...], "prevChapterUrl":"...", "nextChapterUrl":"..."}
 */
@Singleton
class ChapterFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val novelsDir: File
        get() = File(context.filesDir, "novels").also { it.mkdirs() }

    private fun chapterFile(novelSlug: String, chapterNumber: Int): File {
        val dir = File(novelsDir, novelSlug).also { it.mkdirs() }
        return File(dir, "$chapterNumber.json")
    }

    /**
     * Sauvegarde un chapitre téléchargé dans un fichier.
     */
    fun saveChapter(novelSlug: String, chapterNumber: Int, content: ChapterContent) {
        val file = chapterFile(novelSlug, chapterNumber)
        val data = StoredChapter(
            chapterTitle = content.chapterTitle,
            novelTitle = content.novelTitle,
            paragraphs = content.paragraphs.map { StoredParagraph(it.index, it.htmlContent) },
            prevChapterUrl = content.prevChapterUrl,
            nextChapterUrl = content.nextChapterUrl
        )
        file.writeText(json.encodeToString(data))
    }

    /**
     * Charge un chapitre depuis un fichier.
     */
    fun loadChapter(novelSlug: String, chapterNumber: Int): ChapterContent? {
        val file = chapterFile(novelSlug, chapterNumber)
        if (!file.exists()) return null
        return try {
            val data = json.decodeFromString<StoredChapter>(file.readText())
            ChapterContent(
                chapterTitle = data.chapterTitle,
                novelTitle = data.novelTitle,
                paragraphs = data.paragraphs.map { Paragraph(it.index, it.htmlContent) },
                prevChapterUrl = data.prevChapterUrl,
                nextChapterUrl = data.nextChapterUrl
            )
        } catch (e: Exception) { null }
    }

    /**
     * Vérifie si un chapitre est téléchargé.
     */
    fun isChapterDownloaded(novelSlug: String, chapterNumber: Int): Boolean {
        return chapterFile(novelSlug, chapterNumber).exists()
    }

    /**
     * Supprime un chapitre téléchargé.
     */
    fun deleteChapter(novelSlug: String, chapterNumber: Int) {
        chapterFile(novelSlug, chapterNumber).delete()
    }

    /**
     * Supprime tous les chapitres d'un novel.
     */
    fun deleteNovel(novelSlug: String) {
        File(novelsDir, novelSlug).deleteRecursively()
    }

    /**
     * Supprime tous les chapitres téléchargés.
     */
    fun deleteAll() {
        novelsDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Compte le nombre de chapitres téléchargés.
     */
    fun countDownloaded(): Int {
        return novelsDir.listFiles()?.sumOf { dir ->
            dir.listFiles()?.count { it.extension == "json" } ?: 0
        } ?: 0
    }

    /**
     * Liste les slugs des novels qui ont des chapitres téléchargés.
     */
    fun getDownloadedNovels(): List<String> {
        return novelsDir.listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * Liste les numéros des chapitres téléchargés pour un novel.
     */
    fun getDownloadedChapters(novelSlug: String): List<Int> {
        val dir = File(novelsDir, novelSlug)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            ?: emptyList()
    }
}

@Serializable
data class StoredChapter(
    val chapterTitle: String,
    val novelTitle: String,
    val paragraphs: List<StoredParagraph>,
    val prevChapterUrl: String? = null,
    val nextChapterUrl: String? = null
)

@Serializable
data class StoredParagraph(
    val index: Int,
    val htmlContent: String
)
