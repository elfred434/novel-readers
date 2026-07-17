package com.novelreader.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.novelreader.data.local.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire de stockage.
 *
 * Deux modes :
 *   1. **Interne (auto-créé)** — utilise java.io.File dans context.filesDir
 *      Créé automatiquement au premier lancement, aucune action utilisateur requise.
 *   2. **SAF (Storage Access Framework)** — l'utilisateur choisit un dossier
 *      via le sélecteur Android.
 *
 * Structure (identique dans les deux modes) :
 *   {base}/novels/{slug_sanitized}/{chapterNumber}.json
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) {

    companion object {
        private const val BASE_FOLDER = "novels"
    }

    // ── Initialisation automatique ───────────────────────

    suspend fun autoCreateStorageLocation(): Boolean = withContext(Dispatchers.IO) {
        if (prefs.getSafTreeUri() != null) return@withContext true
        val internalPath = prefs.internalStoragePath.first()
        if (internalPath != null) return@withContext true

        val dir = File(context.filesDir, "NovelReader/$BASE_FOLDER")
        try {
            dir.mkdirs()
            if (dir.exists()) {
                prefs.setInternalStoragePath(dir.absolutePath)
                true
            } else false
        } catch (e: Exception) { false }
    }

    suspend fun isUsingInternalStorage(): Boolean = withContext(Dispatchers.IO) {
        prefs.getSafTreeUri() == null && prefs.internalStoragePath.first() != null
    }

    suspend fun isUsingSaf(): Boolean = prefs.getSafTreeUri() != null

    suspend fun hasStorageLocation(): Boolean = withContext(Dispatchers.IO) {
        prefs.getSafTreeUri() != null || prefs.internalStoragePath.first() != null
    }

    suspend fun getStorageDisplayPath(): String = withContext(Dispatchers.IO) {
        val safUri = prefs.getSafTreeUri()
        if (safUri != null) {
            Uri.parse(safUri).lastPathSegment ?: "Dossier SAF"
        } else {
            prefs.internalStoragePath.first() ?: "Non configuré"
        }
    }

    // ── Résolution du dossier de base ─────────────────────

    private suspend fun getBaseFile(): File? = withContext(Dispatchers.IO) {
        val path = prefs.internalStoragePath.first() ?: return@withContext null
        val f = File(path)
        if (f.exists()) f else null
    }

    private suspend fun getBaseDir(): DocumentFile? = withContext(Dispatchers.IO) {
        val uriStr = prefs.getSafTreeUri() ?: return@withContext null
        val treeUri = Uri.parse(uriStr)
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        doc?.findFile(BASE_FOLDER) ?: doc?.createDirectory(BASE_FOLDER)
    }

    // ── Dispatch SAF vs Interne ──────────────────────────

    private suspend fun <T> withStorage(
        safBlock: suspend (DocumentFile) -> T?,
        fileBlock: suspend (File) -> T?
    ): T? = withContext(Dispatchers.IO) {
        if (prefs.getSafTreeUri() != null) {
            val base = getBaseDir() ?: return@withContext null
            safBlock(base)
        } else {
            val base = getBaseFile() ?: return@withContext null
            fileBlock(base)
        }
    }

    private suspend fun <T> withNovelStorage(
        slug: String,
        safBlock: suspend (DocumentFile) -> T?,
        fileBlock: suspend (File) -> T?
    ): T? = withContext(Dispatchers.IO) {
        if (prefs.getSafTreeUri() != null) {
            val base = getBaseDir() ?: return@withContext null
            val folderName = sanitizeFolderName(slug)
            val dir = base.findFile(folderName) ?: base.createDirectory(folderName) ?: return@withContext null
            safBlock(dir)
        } else {
            val base = getBaseFile() ?: return@withContext null
            val dir = File(base, sanitizeFolderName(slug))
            dir.mkdirs()
            if (!dir.exists()) return@withContext null
            fileBlock(dir)
        }
    }

    // ── Opérations fichier ─────────────────────────────────

    suspend fun saveChapterFile(novelSlug: String, chapterNumber: Int, jsonContent: String): Boolean =
        withNovelStorage(novelSlug,
            safBlock = { dir ->
                val fileName = "$chapterNumber.json"
                dir.findFile(fileName)?.delete()
                // Certains providers SAF ajoutent l'extension d'après le MIME,
                // d'autres non → on vérifie et on renomme si nécessaire pour
                // que loadChapterFile("$chapterNumber.json") retrouve le fichier.
                val created = dir.createFile("application/json", chapterNumber.toString())
                    ?: return@withNovelStorage false
                if (created.name != fileName) created.renameTo(fileName)
                val target = dir.findFile(fileName) ?: created
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { stream ->
                    stream.write(jsonContent.toByteArray(Charsets.UTF_8)); true
                } ?: false
            },
            fileBlock = { dir ->
                try {
                    File(dir, "$chapterNumber.json").writeText(jsonContent, Charsets.UTF_8)
                    true
                } catch (e: Exception) { false }
            }
        ) ?: false

    suspend fun loadChapterFile(novelSlug: String, chapterNumber: Int): String? =
        withNovelStorage(novelSlug,
            safBlock = { dir ->
                val file = dir.findFile("$chapterNumber.json") ?: return@withNovelStorage null
                context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
            },
            fileBlock = { dir ->
                try {
                    val f = File(dir, "$chapterNumber.json")
                    if (f.exists()) f.readText(Charsets.UTF_8) else null
                } catch (e: Exception) { null }
            }
        )

    suspend fun isChapterDownloaded(novelSlug: String, chapterNumber: Int): Boolean =
        withNovelStorage(novelSlug,
            safBlock = { dir -> dir.findFile("$chapterNumber.json") != null },
            fileBlock = { dir -> File(dir, "$chapterNumber.json").exists() }
        ) ?: false

    suspend fun deleteChapterFile(novelSlug: String, chapterNumber: Int): Boolean =
        withNovelStorage(novelSlug,
            safBlock = { dir -> dir.findFile("$chapterNumber.json")?.delete() ?: false },
            fileBlock = { dir -> File(dir, "$chapterNumber.json").delete() }
        ) ?: false

    suspend fun deleteMultipleChapterFiles(novelSlug: String, chapterNumbers: List<Int>): Int =
        withNovelStorage(novelSlug,
            safBlock = { dir ->
                var deleted = 0
                for (num in chapterNumbers) {
                    if (dir.findFile("$num.json")?.delete() == true) deleted++
                }
                deleted
            },
            fileBlock = { dir ->
                var deleted = 0
                for (num in chapterNumbers) {
                    if (File(dir, "$num.json").delete()) deleted++
                }
                deleted
            }
        ) ?: 0

    suspend fun deleteNovelFiles(novelSlug: String): Boolean = withStorage(
        safBlock = { base -> base.findFile(sanitizeFolderName(novelSlug))?.delete() ?: false },
        fileBlock = { base -> File(base, sanitizeFolderName(novelSlug)).deleteRecursively(); true }
    ) ?: false

    // ── Compteurs ─────────────────────────────────────────

    suspend fun countDownloadedChapters(): Int = withStorage(
        safBlock = { base -> countFilesRecursive(base) },
        fileBlock = { base ->
            if (!base.exists()) 0
            else base.walkTopDown().count { it.isFile && it.name.endsWith(".json") }
        }
    ) ?: 0

    private fun countFilesRecursive(doc: DocumentFile): Int {
        var count = 0
        doc.listFiles().forEach {
            if (it.isDirectory) count += countFilesRecursive(it)
            else if (it.name?.endsWith(".json") == true) count++
        }
        return count
    }

    suspend fun getDownloadedNovelSlugs(): List<String> = withStorage(
        safBlock = { base ->
            base.listFiles().filter { it.isDirectory && it.listFiles().isNotEmpty() }.mapNotNull { it.name }
        },
        fileBlock = { base ->
            if (!base.exists()) emptyList()
            else base.listFiles()?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }?.map { it.name } ?: emptyList()
        }
    ) ?: emptyList()

    suspend fun getDownloadedChapterNumbers(novelSlug: String): List<Int> = withNovelStorage(novelSlug,
        safBlock = { dir ->
            dir.listFiles().filter { it.name?.endsWith(".json") == true }
                .mapNotNull { it.name?.substringBeforeLast(".")?.toIntOrNull() }.sorted()
        },
        fileBlock = { dir ->
            dir.listFiles()?.filter { it.name.endsWith(".json") }
                ?.mapNotNull { it.name.substringBeforeLast(".").toIntOrNull() }?.sorted() ?: emptyList()
        }
    ) ?: emptyList()

    suspend fun deleteAllFiles(): Boolean = withStorage(
        safBlock = { base ->
            var success = true
            base.listFiles().forEach { if (!it.delete()) success = false }
            success
        },
        fileBlock = { base ->
            try {
                base.listFiles()?.forEach { it.deleteRecursively() }
                true
            } catch (e: Exception) { false }
        }
    ) ?: false

    suspend fun getStorageSizeBytes(): Long = withStorage(
        safBlock = { base -> sizeRecursive(base) },
        fileBlock = { base ->
            if (!base.exists()) 0L
            else base.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }
    ) ?: 0L

    private fun sizeRecursive(doc: DocumentFile): Long {
        var size = 0L
        doc.listFiles().forEach { if (it.isDirectory) size += sizeRecursive(it) else size += it.length() }
        return size
    }

    fun sanitizeFolderName(slug: String): String = slug
        .replace(Regex("""[/\\:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ").trim().take(200).ifBlank { "novel" }
}
