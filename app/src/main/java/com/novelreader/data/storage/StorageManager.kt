package com.novelreader.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.novelreader.data.local.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire de stockage — SAF uniquement.
 * L'utilisateur choisit un dossier via le sélecteur Android (Storage Access Framework).
 * La permission est persistée via takePersistableUriPermission().
 *
 * Structure :
 *   {dossier_choisi}/novels/{slug_sanitized}/{chapterNumber}.json
 *
 * @property context Contexte Android
 * @property prefs Stocke l'URI du dossier racine
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) {

    companion object {
        private const val BASE_FOLDER = "novels"
    }

    // ── URI du dossier racine ──────────────────────────────

    suspend fun getSafTreeUri(): String? = prefs.getSafTreeUri()
    suspend fun setSafTreeUri(uri: String?) = prefs.setSafTreeUri(uri)

    fun hasStorageLocation(): Boolean = prefs.hasStorageLocationSync()

    // ── Dossier de base ────────────────────────────────────

    private suspend fun getBaseDir(): DocumentFile? = withContext(Dispatchers.IO) {
        val uriStr = getSafTreeUri() ?: return@withContext null
        val treeUri = Uri.parse(uriStr)
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        doc?.findFile(BASE_FOLDER) ?: doc?.createDirectory(BASE_FOLDER)
    }

    // ── Dossier d'un novel ─────────────────────────────────

    suspend fun getNovelDir(novelSlug: String): DocumentFile? = withContext(Dispatchers.IO) {
        val base = getBaseDir() ?: return@withContext null
        val folderName = sanitizeFolderName(novelSlug)
        base.findFile(folderName) ?: base.createDirectory(folderName)
    }

    // ── Opérations fichier ─────────────────────────────────

    suspend fun saveChapterFile(
        novelSlug: String, chapterNumber: Int, jsonContent: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug) ?: return@withContext false
            val fileName = "$chapterNumber.json"
            dir.findFile(fileName)?.delete()
            val newFile = dir.createFile("application/json", fileName.substringBeforeLast("."))
            newFile?.let { file ->
                context.contentResolver.openOutputStream(file.uri)?.use { stream ->
                    stream.write(jsonContent.toByteArray(Charsets.UTF_8)); true
                }
            } ?: false
        } catch (e: Exception) { false }
    }

    suspend fun loadChapterFile(novelSlug: String, chapterNumber: Int): String? = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug) ?: return@withContext null
            val file = dir.findFile("$chapterNumber.json") ?: return@withContext null
            context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) { null }
    }

    suspend fun isChapterDownloaded(novelSlug: String, chapterNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug) ?: return@withContext false
            dir.findFile("$chapterNumber.json") != null
        } catch (e: Exception) { false }
    }

    suspend fun deleteChapterFile(novelSlug: String, chapterNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug) ?: return@withContext false
            dir.findFile("$chapterNumber.json")?.delete() ?: false
        } catch (e: Exception) { false }
    }

    suspend fun deleteMultipleChapterFiles(novelSlug: String, chapterNumbers: List<Int>): Int = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug) ?: return@withContext 0
            var deleted = 0
            for (num in chapterNumbers) {
                if (dir.findFile("$num.json")?.delete() == true) deleted++
            }
            deleted
        } catch (e: Exception) { 0 }
    }

    suspend fun deleteNovelFiles(novelSlug: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val base = getBaseDir() ?: return@withContext false
            base.findFile(sanitizeFolderName(novelSlug))?.delete() ?: false
        } catch (e: Exception) { false }
    }

    // ── Compteurs ─────────────────────────────────────────

    suspend fun countDownloadedChapters(): Int = withContext(Dispatchers.IO) {
        try {
            val base = getBaseDir() ?: return@withContext 0
            countFilesRecursive(base)
        } catch (e: Exception) { 0 }
    }

    private fun countFilesRecursive(doc: DocumentFile): Int {
        var count = 0
        doc.listFiles().forEach { if (it.isDirectory) count += countFilesRecursive(it) else if (it.name?.endsWith(".json") == true) count++ }
        return count
    }

    suspend fun getDownloadedNovelSlugs(): List<String> = withContext(Dispatchers.IO) {
        try {
            val base = getBaseDir() ?: return@withContext emptyList()
            base.listFiles().filter { it.isDirectory && it.listFiles().isNotEmpty() }.mapNotNull { it.name }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getDownloadedChapterNumbers(novelSlug: String): List<Int> = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug) ?: return@withContext emptyList()
            dir.listFiles().filter { it.name?.endsWith(".json") == true }
                .mapNotNull { it.name?.substringBeforeLast(".")?.toIntOrNull() }.sorted()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteAllFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            val base = getBaseDir() ?: return@withContext false
            var success = true
            base.listFiles().forEach { if (!it.delete()) success = false }
            success
        } catch (e: Exception) { false }
    }

    suspend fun getStorageSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            val base = getBaseDir() ?: return@withContext 0L
            sizeRecursive(base)
        } catch (e: Exception) { 0L }
    }

    private fun sizeRecursive(doc: DocumentFile): Long {
        var size = 0L
        doc.listFiles().forEach { if (it.isDirectory) size += sizeRecursive(it) else size += it.length() }
        return size
    }

    fun sanitizeFolderName(slug: String): String = slug
        .replace(Regex("""[/\\:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ").trim().take(200).ifBlank { "novel" }
}
