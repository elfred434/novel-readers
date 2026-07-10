package com.novelreader.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.data.local.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Type de stockage sélectionné par l'utilisateur.
 */
enum class StorageType(val value: Int) {
    INTERNAL(0),    // context.filesDir — toujours disponible, aucune permission requise
    SAF(1)          // Storage Access Framework — dossier choisi par l'utilisateur
}

/**
 * Gestionnaire de stockage pour les chapitres téléchargés.
 *
 * Deux modes :
 *   INTERNAL → stockage dans context.filesDir/novels/ (fiable, pas de permission)
 *   SAF → stockage dans un dossier choisi via SAF (persistable URI)
 *
 * Structure des dossiers (identique dans les deux modes) :
 *   {base}/novels/{slug_sanitized}/{chapterNumber}.json
 *
 * Le slug est utilisé comme nom de dossier (stable, jamais modifié).
 * Le titre du novel n'est utilisé que pour l'affichage.
 *
 * @property context Contexte Android
 * @property prefs Préférences (stocke l'URI SAF et le type de stockage)
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) {

    companion object {
        private const val BASE_FOLDER = "novels"
    }

    // ── État courant ───────────────────────────────────────

    /** Type de stockage actuel (INTERNAL ou SAF). */
    suspend fun getStorageType(): StorageType {
        val v = prefs.getStorageType()
        return StorageType.entries.firstOrNull { it.value == v } ?: StorageType.INTERNAL
    }

    suspend fun setStorageType(type: StorageType) {
        prefs.setStorageType(type.value)
    }

    suspend fun getSafTreeUri(): String? = prefs.getSafTreeUri()
    suspend fun setSafTreeUri(uri: String?) = prefs.setSafTreeUri(uri)

    // ── Dossier de base ────────────────────────────────────

    /**
     * Retourne le DocumentFile (SAF) ou File (interne) du dossier de base "novels/".
     * Crée le dossier s'il n'existe pas.
     */
    private suspend fun getBaseDir(): DocumentFile? = withContext(Dispatchers.IO) {
        when (getStorageType()) {
            StorageType.INTERNAL -> {
                val dir = File(context.filesDir, BASE_FOLDER)
                dir.mkdirs()
                DocumentFile.fromFile(dir)
            }
            StorageType.SAF -> {
                val uriStr = getSafTreeUri() ?: return@withContext null
                val treeUri = Uri.parse(uriStr)
                val doc = DocumentFile.fromTreeUri(context, treeUri)
                doc?.findFile(BASE_FOLDER) ?: doc?.createDirectory(BASE_FOLDER)
            }
        }
    }

    // ── Dossier d'un novel ─────────────────────────────────

    /**
     * Retourne le dossier d'un novel. Crée le dossier s'il n'existe pas.
     * Le nom du dossier est le slug nettoyé (stable, lié à l'ID interne).
     */
    suspend fun getNovelDir(novelSlug: String, novelTitle: String = ""): DocumentFile? = withContext(Dispatchers.IO) {
        val base = getBaseDir() ?: return@withContext null
        val folderName = sanitizeFolderName(novelSlug)
        base.findFile(folderName) ?: base.createDirectory(folderName)
    }

    /**
     * Construit le chemin d'affichage pour un novel donné.
     */
    suspend fun getNovelDisplayPath(novelSlug: String): String = withContext(Dispatchers.IO) {
        val base = when (getStorageType()) {
            StorageType.INTERNAL -> File(context.filesDir, BASE_FOLDER)
            StorageType.SAF -> {
                val uriStr = getSafTreeUri() ?: return@withContext "${context.filesDir}/$BASE_FOLDER"
                "$uriStr/$BASE_FOLDER"
            }
        }
        "$base/${sanitizeFolderName(novelSlug)}"
    }

    // ── Opérations fichier ─────────────────────────────────

    /**
     * Sauvegarde un fichier JSON dans le dossier du novel.
     */
    suspend fun saveChapterFile(
        novelSlug: String,
        chapterNumber: Int,
        jsonContent: String,
        novelTitle: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDir(novelSlug, novelTitle) ?: return@withContext false
            val fileName = "$chapterNumber.json"

            // Supprimer l'ancien fichier s'il existe
            dir.findFile(fileName)?.delete()

            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val file = File(context.filesDir, "$BASE_FOLDER/${sanitizeFolderName(novelSlug)}/$fileName")
                    file.parentFile?.mkdirs()
                    file.writeText(jsonContent)
                    true
                }
                StorageType.SAF -> {
                    val newFile = dir.createFile("application/json", fileName.substringBeforeLast("."))
                    newFile?.let { file ->
                        context.contentResolver.openOutputStream(file.uri)?.use { stream ->
                            stream.write(jsonContent.toByteArray(Charsets.UTF_8))
                            true
                        }
                    } ?: false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Charge le contenu JSON d'un chapitre depuis le stockage.
     */
    suspend fun loadChapterFile(novelSlug: String, chapterNumber: Int): String? = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val file = File(context.filesDir, "$BASE_FOLDER/${sanitizeFolderName(novelSlug)}/$chapterNumber.json")
                    if (file.exists()) file.readText() else null
                }
                StorageType.SAF -> {
                    val dir = getNovelDir(novelSlug) ?: return@withContext null
                    val file = dir.findFile("$chapterNumber.json") ?: return@withContext null
                    context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
                }
            }
        } catch (e: Exception) { null }
    }

    /**
     * Vérifie si un chapitre est téléchargé.
     */
    suspend fun isChapterDownloaded(novelSlug: String, chapterNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> File(context.filesDir, "$BASE_FOLDER/${sanitizeFolderName(novelSlug)}/$chapterNumber.json").exists()
                StorageType.SAF -> {
                    val dir = getNovelDir(novelSlug) ?: return@withContext false
                    dir.findFile("$chapterNumber.json") != null
                }
            }
        } catch (e: Exception) { false }
    }

    /**
     * Supprime un chapitre téléchargé.
     */
    suspend fun deleteChapterFile(novelSlug: String, chapterNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> File(context.filesDir, "$BASE_FOLDER/${sanitizeFolderName(novelSlug)}/$chapterNumber.json").delete()
                StorageType.SAF -> {
                    val dir = getNovelDir(novelSlug) ?: return@withContext false
                    dir.findFile("$chapterNumber.json")?.delete() ?: false
                }
            }
        } catch (e: Exception) { false }
    }

    /**
     * Supprime tous les fichiers d'un novel.
     */
    suspend fun deleteNovelFiles(novelSlug: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val dir = File(context.filesDir, "$BASE_FOLDER/${sanitizeFolderName(novelSlug)}")
                    dir.deleteRecursively()
                }
                StorageType.SAF -> {
                    val base = getBaseDir() ?: return@withContext false
                    base.findFile(sanitizeFolderName(novelSlug))?.delete() ?: false
                }
            }
        } catch (e: Exception) { false }
    }

    // ── Compteurs ─────────────────────────────────────────

    /**
     * Compte le nombre total de chapitres téléchargés.
     */
    suspend fun countDownloadedChapters(): Int = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val base = File(context.filesDir, BASE_FOLDER)
                    if (!base.exists()) return@withContext 0
                    base.listFiles()?.sumOf { dir -> dir.listFiles()?.count { it.extension == "json" } ?: 0 } ?: 0
                }
                StorageType.SAF -> {
                    val base = getBaseDir() ?: return@withContext 0
                    countFilesInDoc(base)
                }
            }
        } catch (e: Exception) { 0 }
    }

    private fun countFilesInDoc(doc: DocumentFile): Int {
        var count = 0
        val files = doc.listFiles()
        for (f in files) {
            if (f.isDirectory) count += countFilesInDoc(f)
            else if (f.name?.endsWith(".json") == true) count++
        }
        return count
    }

    /**
     * Liste les slugs des novels qui ont des fichiers téléchargés.
     */
    suspend fun getDownloadedNovelSlugs(): List<String> = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val base = File(context.filesDir, BASE_FOLDER)
                    if (!base.exists()) return@withContext emptyList()
                    base.listFiles()?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }?.map { it.name } ?: emptyList()
                }
                StorageType.SAF -> {
                    val base = getBaseDir() ?: return@withContext emptyList()
                    base.listFiles().filter { it.isDirectory && it.listFiles().isNotEmpty() }.mapNotNull { it.name }
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * Liste les numéros des chapitres téléchargés pour un novel.
     */
    suspend fun getDownloadedChapterNumbers(novelSlug: String): List<Int> = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val dir = File(context.filesDir, "$BASE_FOLDER/${sanitizeFolderName(novelSlug)}")
                    if (!dir.exists()) return@withContext emptyList()
                    dir.listFiles()?.filter { it.extension == "json" }?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }?.sorted() ?: emptyList()
                }
                StorageType.SAF -> {
                    val dir = getNovelDir(novelSlug) ?: return@withContext emptyList()
                    dir.listFiles().filter { it.name?.endsWith(".json") == true }
                        .mapNotNull { it.name?.substringBeforeLast(".")?.toIntOrNull() }
                        .sorted()
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── Utilitaires ───────────────────────────────────────

    /**
     * Nettoie un slug pour qu'il soit valide comme nom de dossier.
     * Les slugs NovelFrance sont déjà en format safe (lettres, chiffres, tirets).
     */
    fun sanitizeFolderName(slug: String): String {
        return slug
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(200)
            .ifBlank { "novel" }
    }

    /**
     * Affiche la taille approximative du stockage utilisé.
     */
    suspend fun getStorageSizeBytes(): Long = withContext(Dispatchers.IO) {
        try {
            when (getStorageType()) {
                StorageType.INTERNAL -> {
                    val base = File(context.filesDir, BASE_FOLDER)
                    if (!base.exists()) return@withContext 0L
                    base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }
                StorageType.SAF -> {
                    val base = getBaseDir() ?: return@withContext 0L
                    countSizeInDoc(base)
                }
            }
        } catch (e: Exception) { 0L }
    }

    private fun countSizeInDoc(doc: DocumentFile): Long {
        var size = 0L
        doc.listFiles().forEach { f ->
            if (f.isDirectory) size += countSizeInDoc(f)
            else size += f.length()
        }
        return size
    }
}
