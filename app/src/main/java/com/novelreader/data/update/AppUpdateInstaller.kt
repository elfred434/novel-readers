package com.novelreader.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gère le téléchargement et l'installation de l'APK de mise à jour.
 *
 * Utilise le DownloadManager système Android (pas le DownloadManager de l'app)
 * pour garantir la compatibilité et éviter les conflits de signature.
 *
 * Robustesse :
 * - L'ID du téléchargement + le nom du fichier sont persistés (SharedPreferences) :
 *   le receiver déclaré dans le manifest ([ApkDownloadReceiver]) installe l'APK
 *   même si le process de l'app a été tué pendant le téléchargement.
 * - Le receiver dynamique (chemin rapide, app vivante) se désenregistre lui-même
 *   après réception : plus de fuite de receiver.
 * - Avant installation, le statut réel du téléchargement est vérifié
 *   via DownloadManager.query (pas seulement la réception du broadcast).
 * - Les échecs sont remontés via onError au lieu d'être avalés silencieusement.
 */
class AppUpdateInstaller(private val context: Context) {

    private val appContext = context.applicationContext

    private var downloadId: Long = -1
    private var onComplete: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var receiver: BroadcastReceiver? = null

    /**
     * Lance le téléchargement de l'APK et l'installe automatiquement.
     *
     * @param onStart  appelé dès que le téléchargement est enregistré
     * @param onComplete appelé quand l'installation a été déclenchée avec succès
     * @param onError  appelé en cas d'échec (téléchargement, permission, installeur)
     */
    fun downloadAndInstall(
        apkUrl: String,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        this.onComplete = onComplete
        this.onError = onError

        // Un seul téléchargement à la fois : annuler l'éventuel précédent
        cancelPending()

        // Nettoyer les anciens téléchargements
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir?.listFiles()?.filter { it.name.startsWith("NovelReader-") && it.name.endsWith(".apk") }
            ?.forEach { it.delete() }

        val fileName = "NovelReader-${System.currentTimeMillis()}.apk"

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("NovelReader")
            .setDescription("Téléchargement de la mise à jour…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        // Persister pour que le receiver du manifest puisse finir le travail
        setPendingDownloadId(appContext, downloadId)
        setPendingFileName(appContext, fileName)

        onStart()

        // Receiver dynamique : chemin rapide si l'app est vivante.
        // Il se désenregistre après la première réception.
        val dynamicReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                unregisterSelf()
                handleDownloadComplete()
            }
        }
        receiver = dynamicReceiver
        try {
            appContext.registerReceiver(
                dynamicReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            // En cas d'échec d'enregistrement, le receiver du manifest fera le travail
            receiver = null
        }
    }

    /** Fin de téléchargement détectée : vérifie le statut puis installe ou signale l'erreur. */
    private fun handleDownloadComplete() {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = getPendingFileName(appContext)
        val ok = isDownloadSuccessful(dm, downloadId)
        clearPending(appContext)

        if (!ok) {
            try { dm.remove(downloadId) } catch (_: Exception) {}
            fileName?.let { deleteFile(it) }
            onError?.invoke("Le téléchargement de la mise à jour a échoué.")
            return
        }
        if (fileName == null) {
            onError?.invoke("APK introuvable après téléchargement.")
            return
        }
        installApk(appContext, fileName, onError)
        onComplete?.invoke()
    }

    private fun deleteFile(fileName: String) {
        try {
            File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).delete()
        } catch (_: Exception) {}
    }

    private fun unregisterSelf() {
        receiver?.let {
            try { appContext.unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        receiver = null
    }

    /** Annule le téléchargement en cours (s'il existe) et nettoie. */
    private fun cancelPending() {
        unregisterSelf()
        if (downloadId >= 0) {
            try {
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.remove(downloadId)
            } catch (_: Exception) {}
            downloadId = -1
        }
        clearPending(appContext)
    }

    fun cancel() {
        cancelPending()
        onComplete = null
        onError = null
    }

    companion object {
        private const val PREFS_NAME = "apk_update"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_FILE_NAME = "file_name"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** ID du téléchargement APK en attente (-1 si aucun). */
        fun getPendingDownloadId(context: Context): Long =
            prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)

        fun getPendingFileName(context: Context): String? =
            prefs(context).getString(KEY_FILE_NAME, null)

        fun setPendingDownloadId(context: Context, id: Long) {
            prefs(context).edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        }

        fun setPendingFileName(context: Context, name: String) {
            prefs(context).edit().putString(KEY_FILE_NAME, name).apply()
        }

        fun clearPending(context: Context) {
            prefs(context).edit().remove(KEY_DOWNLOAD_ID).remove(KEY_FILE_NAME).apply()
        }

        /** Vérifie que le téléchargement s'est réellement terminé avec succès. */
        fun isDownloadSuccessful(dm: DownloadManager, id: Long): Boolean {
            if (id < 0) return false
            val query = DownloadManager.Query().setFilterById(id)
            var cursor: Cursor? = null
            try {
                cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    return status == DownloadManager.STATUS_SUCCESSFUL
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
            return false
        }

        /**
         * Déclenche l'installation de l'APK via l'intent système.
         * Utilise FileProvider pour Android 7+ (Uri per-URI permissions).
         * Les échecs (aucun installeur, permission refusée…) sont remontés via onError.
         */
        fun installApk(context: Context, fileName: String, onError: ((String) -> Unit)?) {
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: run {
                    onError?.invoke("Dossier de téléchargement indisponible.")
                    return
                }
                val file = File(dir, fileName)
                if (!file.exists()) {
                    onError?.invoke("APK introuvable après téléchargement.")
                    return
                }

                val uri: Uri =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } else {
                        Uri.fromFile(file)
                    }

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                onError?.invoke("Aucune application d'installation trouvée sur cet appareil.")
            } catch (e: SecurityException) {
                onError?.invoke("Installation refusée : autorisez « Installer des applications inconnues » pour NovelReader, puis réessayez.")
                openInstallSettings(context)
            } catch (e: Exception) {
                android.util.Log.e("AppUpdateInstaller", "Erreur installation APK", e)
                onError?.invoke("Erreur lors de l'installation : ${e.message}")
            }
        }

        /** Redirige vers l'écran système « Installer des applications inconnues ». */
        private fun openInstallSettings(context: Context) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
