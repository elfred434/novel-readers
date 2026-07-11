package com.novelreader.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gère le téléchargement et l'installation de l'APK de mise à jour.
 *
 * Utilise le DownloadManager système Android (pas le DownloadManager de l'app)
 * pour garantir la compatibilité et éviter les conflits de signature.
 *
 * Fonctionnement :
 * 1. Télécharge l'APK via DownloadManager
 * 2. Écoute la fin du téléchargement via BroadcastReceiver
 * 3. Déclenche l'Intent d'installation système
 */
class AppUpdateInstaller(private val context: Context) {

    private var downloadId: Long = -1
    private var onComplete: (() -> Unit)? = null
    private var receiver: BroadcastReceiver? = null

    /**
     * Lance le téléchargement de l'APK et l'installe automatiquement.
     */
    fun downloadAndInstall(apkUrl: String, onStart: () -> Unit = {}, onComplete: () -> Unit = {}) {
        this.onComplete = onComplete

        // Nettoyer les anciens téléchargements
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir?.listFiles()?.filter { it.name.startsWith("NovelReader-") && it.name.endsWith(".apk") }
            ?.forEach { it.delete() }

        val fileName = "NovelReader-${System.currentTimeMillis()}.apk"

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("NovelReader")
            .setDescription("Téléchargement de la mise à jour…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        onStart()

        // Écouter la fin du téléchargement
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(fileName)
                    onComplete?.invoke()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED)
    }

    /**
     * Déclenche l'installation de l'APK via l'intent système.
     * Utilise FileProvider pour Android 7+ (Uri per-URI permissions).
     */
    private fun installApk(fileName: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) return

            val uri: Uri
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(context,
                    "${context.packageName}.fileprovider", file)
            } else {
                uri = Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateInstaller", "Erreur installation APK", e)
        }
    }

    fun cancel() {
        if (downloadId >= 0) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
        }
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
