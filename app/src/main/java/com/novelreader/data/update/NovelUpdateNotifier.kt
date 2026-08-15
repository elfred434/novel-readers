package com.novelreader.data.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novelreader.MainActivity
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.model.ChapterPreview
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Publie les notifications « nouveaux chapitres » sur le canal `novel_updates`.
 *
 * - Respecte le réglage utilisateur « Notifications » (PreferencesManager).
 * - Ne poste rien si la permission POST_NOTIFICATIONS (Android 13+) manque.
 * - Un appui sur la notification ouvre directement le détail du novel concerné
 *   (deep link via MainActivity.EXTRA_NOVEL_SLUG).
 */
class NovelUpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) {

    companion object {
        /** Canal créé dans NovelReaderApp. */
        const val CHANNEL_ID = "novel_updates"
        private const val NOTIFICATION_BASE_ID = 1000
    }

    suspend fun notifyNewChapters(novelSlug: String, novelTitle: String, newChapters: List<ChapterPreview>) {
        if (newChapters.isEmpty()) return
        if (!prefs.notificationsEnabled.first()) return
        if (!canPostNotifications()) return

        val count = newChapters.size
        val text = if (count == 1) {
            val ch = newChapters.first()
            "Nouveau chapitre : Ch. ${ch.chapterNumber} — ${ch.title}"
        } else {
            "$count nouveaux chapitres"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NOVEL_SLUG, novelSlug)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(novelSlug),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(novelTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId(novelSlug), notification)
        } catch (e: SecurityException) {
            android.util.Log.w("NovelUpdateNotifier", "Permission de notification refusée", e)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(novelSlug: String): Int =
        NOTIFICATION_BASE_ID + (novelSlug.hashCode() and 0x7fffffff)
}
