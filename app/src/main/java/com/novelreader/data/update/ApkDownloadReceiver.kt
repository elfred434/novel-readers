package com.novelreader.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver déclaré dans le manifest : garantit que l'APK de mise à jour est
 * installé même si le process de l'app a été tué pendant le téléchargement.
 *
 * Garde-fous (anti-broadcasts forgés / double traitement) :
 * - on ne fait rien si aucun téléchargement APK n'est en attente
 *   (l'ID persisté ne correspond pas) ;
 * - le statut réel est vérifié via DownloadManager.query avant installation ;
 * - l'ID persistant est effacé après traitement (le premier receiver qui
 *   passe rend les suivants inopérants).
 */
class ApkDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val pendingId = AppUpdateInstaller.getPendingDownloadId(context)
        if (id < 0 || id != pendingId) return

        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = AppUpdateInstaller.getPendingFileName(appContext)

        if (fileName != null && AppUpdateInstaller.isDownloadSuccessful(dm, id)) {
            AppUpdateInstaller.installApk(appContext, fileName, null)
        } else {
            // Téléchargement échoué / annulé : nettoyer
            try { dm.remove(id) } catch (_: Exception) {}
        }
        AppUpdateInstaller.clearPending(appContext)
    }
}
