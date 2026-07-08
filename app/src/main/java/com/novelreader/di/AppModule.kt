package com.novelreader.di

import android.content.Context
import androidx.room.Room
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Module Hilt principal.
 * Fournit les dépendances singleton de l'application.
 *
 * Correction de l'audit :
 * - OkHttpClient est configuré avec timeouts et User-Agent
 * - NovelFranceSource reçoit l'Api et le Parser injectés, pas créés par défaut
 * - NovelFranceApi reçoit le même client configuré
 * - Le Repository dépend de l'interface NovelSource (pas de la classe concrète)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ===================== Networking =====================

    /**
     * OkHttpClient partagé, configuré avec timeouts longs (15s) et User-Agent
     * pour les appels vers novelfrance.fr et le parsing HTML.
     */
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

    /**
     * NovelFranceApi : client pour l'API REST JSON
     * Reçoit l'OkHttpClient déjà configuré.
     */
    @Provides
    @Singleton
    fun provideNovelFranceApi(client: OkHttpClient): NovelFranceApi {
        return NovelFranceApi(client)
    }

    /**
     * NovelFranceParser : parseur HTML/JSON pour les données embarquées
     * Stateless, peut être singleton.
     */
    @Provides
    @Singleton
    fun provideNovelFranceParser(): NovelFranceParser {
        return NovelFranceParser()
    }

    /**
     * NovelFranceSource : implémentation concrète de NovelSource.
     * Reçoit toutes ses dépendances par injection (pas de valeurs par défaut).
     * Fournie en tant que NovelSource (interface) pour que le Repository
     * dépende de l'abstraction, pas de l'implémentation.
     */
    @Provides
    @Singleton
    fun provideNovelFranceSource(
        client: OkHttpClient,
        api: NovelFranceApi,
        parser: NovelFranceParser
    ): NovelSource {
        return NovelFranceSource(
            httpClient = client,
            api = api,
            parser = parser
        )
    }

    // ===================== Base de données Room =====================

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideNovelDao(database: AppDatabase): NovelDao {
        return database.novelDao()
    }

    @Provides
    @Singleton
    fun provideChapterDao(database: AppDatabase): ChapterDao {
        return database.chapterDao()
    }

    @Provides
    @Singleton
    fun provideChapterContentDao(database: AppDatabase): ChapterContentDao {
        return database.chapterContentDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }

    // ===================== Préférences =====================

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
}
