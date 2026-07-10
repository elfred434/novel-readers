package com.novelreader.di

import android.content.Context
import androidx.room.Room
import com.novelreader.data.download.ChapterFileManager
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.extension.ExtensionManager
import com.novelreader.data.storage.StorageManager
import com.novelreader.data.local.AppDatabase
import com.novelreader.data.local.dao.CategoryDao
import com.novelreader.data.local.dao.ChapterContentDao
import com.novelreader.data.local.dao.ChapterDao
import com.novelreader.data.local.dao.NovelDao
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.remote.novelfrance.NovelFranceApi
import com.novelreader.data.remote.novelfrance.NovelFranceParser
import com.novelreader.data.remote.novelfrance.NovelFranceSource
import com.novelreader.data.remote.source.NovelSource
import com.novelreader.data.repository.NovelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ===================== Networking =====================

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideNovelFranceApi(client: OkHttpClient): NovelFranceApi = NovelFranceApi(client)

    @Provides
    @Singleton
    fun provideNovelFranceParser(): NovelFranceParser = NovelFranceParser()

    @Provides
    @Singleton
    fun provideNovelFranceSource(
        client: OkHttpClient, api: NovelFranceApi, parser: NovelFranceParser
    ): NovelSource {
        return NovelFranceSource(httpClient = client, api = api, parser = parser)
    }

    // ===================== Extensions =====================

    @Provides
    @Singleton
    fun provideExtensionManager(novelFranceSource: NovelSource): ExtensionManager {
        return ExtensionManager().apply { registerSource(novelFranceSource) }
    }

    // ===================== Download Manager =====================

    @Provides
    @Singleton
    fun provideChapterFileManager(@ApplicationContext context: Context): ChapterFileManager {
        return ChapterFileManager(context)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        repository: NovelRepository,
        chapterFileManager: ChapterFileManager
    ): DownloadManager {
        return DownloadManager(repository, chapterFileManager)
    }

    // ===================== Base de données Room =====================

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideNovelDao(database: AppDatabase): NovelDao = database.novelDao()

    @Provides
    @Singleton
    fun provideChapterDao(database: AppDatabase): ChapterDao = database.chapterDao()

    @Provides
    @Singleton
    fun provideChapterContentDao(database: AppDatabase): ChapterContentDao = database.chapterContentDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    // ===================== Préférences =====================

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
}

    // ===================== Storage =====================

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context, prefs: PreferencesManager): StorageManager {
        return StorageManager(context, prefs)
    }
}
