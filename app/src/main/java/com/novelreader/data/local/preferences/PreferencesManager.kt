package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "novelreader_settings")

class PreferencesManager(private val context: Context) {

    val themeType: Flow<Int> = context.dataStore.data.map { it[KEY_THEME] ?: 0 }
    suspend fun setThemeType(type: Int) { context.dataStore.edit { it[KEY_THEME] = type } }

    val updateIntervalHours: Flow<Int> = context.dataStore.data.map { it[KEY_UPDATE_INTERVAL] ?: 12 }
    suspend fun setUpdateIntervalHours(h: Int) { context.dataStore.edit { it[KEY_UPDATE_INTERVAL] = h } }

    val readerFontSize: Flow<Int> = context.dataStore.data.map { it[KEY_READER_FONT_SIZE] ?: 18 }
    suspend fun setReaderFontSize(s: Int) { context.dataStore.edit { it[KEY_READER_FONT_SIZE] = s } }

    val readerTheme: Flow<Int> = context.dataStore.data.map { it[KEY_READER_THEME] ?: 0 }
    suspend fun setReaderTheme(t: Int) { context.dataStore.edit { it[KEY_READER_THEME] = t } }

    val readerFont: Flow<Int> = context.dataStore.data.map { it[KEY_READER_FONT] ?: 0 }
    suspend fun setReaderFont(f: Int) { context.dataStore.edit { it[KEY_READER_FONT] = f } }

    val readerLineHeight: Flow<Float> = context.dataStore.data.map { it[KEY_READER_LINE_HEIGHT]?.toFloat() ?: 1.6f }
    suspend fun setReaderLineHeight(h: Float) { context.dataStore.edit { it[KEY_READER_LINE_HEIGHT] = h.toDouble() } }

    val readerPadding: Flow<Int> = context.dataStore.data.map { it[KEY_READER_PADDING] ?: 20 }
    suspend fun setReaderPadding(p: Int) { context.dataStore.edit { it[KEY_READER_PADDING] = p } }

    val readerPaginationMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_READER_PAGINATION] ?: false }
    suspend fun setReaderPaginationMode(e: Boolean) { context.dataStore.edit { it[KEY_READER_PAGINATION] = e } }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
    suspend fun setNotificationsEnabled(e: Boolean) { context.dataStore.edit { it[KEY_NOTIFICATIONS] = e } }

    val downloadMaxConcurrent: Flow<Int> = context.dataStore.data.map { it[KEY_DOWNLOAD_MAX_CONCURRENT] ?: 2 }
    suspend fun setDownloadMaxConcurrent(n: Int) { context.dataStore.edit { it[KEY_DOWNLOAD_MAX_CONCURRENT] = n } }

    val downloadOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[KEY_DOWNLOAD_ON_WIFI_ONLY] ?: true }
    suspend fun setDownloadOnWifiOnly(e: Boolean) { context.dataStore.edit { it[KEY_DOWNLOAD_ON_WIFI_ONLY] = e } }

    val wifiHighDataMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_WIFI_HIGH_DATA] ?: true }
    suspend fun setWifiHighDataMode(e: Boolean) { context.dataStore.edit { it[KEY_WIFI_HIGH_DATA] = e } }

    // ===== SAF Storage =====
    suspend fun getSafTreeUri(): String? = context.dataStore.data.first()[KEY_STORAGE_SAF_URI]
    suspend fun setSafTreeUri(uri: String?) { context.dataStore.edit { if (uri != null) it[KEY_STORAGE_SAF_URI] = uri else it.remove(KEY_STORAGE_SAF_URI) } }

    fun hasStorageLocationSync(): Boolean {
        return try { runBlocking { getSafTreeUri() != null } } catch (e: Exception) { false }
    }

    companion object {
        private val KEY_THEME = intPreferencesKey("theme_type")
        private val KEY_UPDATE_INTERVAL = intPreferencesKey("update_interval_hours")
        private val KEY_READER_FONT_SIZE = intPreferencesKey("reader_font_size")
        private val KEY_READER_THEME = intPreferencesKey("reader_theme")
        private val KEY_READER_FONT = intPreferencesKey("reader_font")
        private val KEY_READER_LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        private val KEY_READER_PADDING = intPreferencesKey("reader_padding")
        private val KEY_READER_PAGINATION = booleanPreferencesKey("reader_pagination")
        private val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val KEY_DOWNLOAD_MAX_CONCURRENT = intPreferencesKey("download_max_concurrent")
        private val KEY_DOWNLOAD_ON_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        private val KEY_WIFI_HIGH_DATA = booleanPreferencesKey("wifi_high_data_mode")
        private val KEY_STORAGE_SAF_URI = stringPreferencesKey("storage_saf_uri")
    }
}
