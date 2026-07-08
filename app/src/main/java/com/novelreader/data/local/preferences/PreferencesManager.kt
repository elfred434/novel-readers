package com.novelreader.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "novelreader_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_UPDATE_INTERVAL = intPreferencesKey("update_interval_hours")
        private val KEY_READER_FONT_SIZE = intPreferencesKey("reader_font_size")
        private val KEY_READER_THEME = intPreferencesKey("reader_theme")
        private val KEY_READER_FONT = intPreferencesKey("reader_font")
        private val KEY_READER_LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        private val KEY_READER_PADDING = intPreferencesKey("reader_padding")
        private val KEY_READER_PAGINATION = booleanPreferencesKey("reader_pagination")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[KEY_DARK_THEME] ?: true }
    suspend fun setDarkTheme(dark: Boolean) { context.dataStore.edit { it[KEY_DARK_THEME] = dark } }

    val updateIntervalHours: Flow<Int> = context.dataStore.data.map { it[KEY_UPDATE_INTERVAL] ?: 12 }
    suspend fun setUpdateIntervalHours(hours: Int) { context.dataStore.edit { it[KEY_UPDATE_INTERVAL] = hours } }

    val readerFontSize: Flow<Int> = context.dataStore.data.map { it[KEY_READER_FONT_SIZE] ?: 18 }
    suspend fun setReaderFontSize(size: Int) { context.dataStore.edit { it[KEY_READER_FONT_SIZE] = size } }

    val readerTheme: Flow<Int> = context.dataStore.data.map { it[KEY_READER_THEME] ?: 0 }
    suspend fun setReaderTheme(theme: Int) { context.dataStore.edit { it[KEY_READER_THEME] = theme } }

    val readerFont: Flow<Int> = context.dataStore.data.map { it[KEY_READER_FONT] ?: 0 }
    suspend fun setReaderFont(font: Int) { context.dataStore.edit { it[KEY_READER_FONT] = font } }

    val readerLineHeight: Flow<Float> = context.dataStore.data.map { it[KEY_READER_LINE_HEIGHT]?.toFloat() ?: 1.6f }
    suspend fun setReaderLineHeight(height: Float) { context.dataStore.edit { it[KEY_READER_LINE_HEIGHT] = height.toDouble() } }

    val readerPadding: Flow<Int> = context.dataStore.data.map { it[KEY_READER_PADDING] ?: 20 }
    suspend fun setReaderPadding(padding: Int) { context.dataStore.edit { it[KEY_READER_PADDING] = padding } }

    val readerPaginationMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_READER_PAGINATION] ?: false }
    suspend fun setReaderPaginationMode(enabled: Boolean) { context.dataStore.edit { it[KEY_READER_PAGINATION] = enabled } }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }
    suspend fun setNotificationsEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_NOTIFICATIONS_ENABLED] = enabled } }
}
