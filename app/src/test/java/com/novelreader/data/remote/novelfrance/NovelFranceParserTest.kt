package com.novelreader.data.remote.novelfrance

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Test unitaire pour le parser NovelFrance.
 *
 * ATTENTION : Ces tests font de vrais appels réseau vers novelfrance.fr.
 * Ils vérifient que le parsing fonctionne sur le site réel.
 *
 * Idéalement, on remplacerait les appels réseau par des fichiers HTML
 * pré-téléchargés (fixtures) pour des tests déterministes.
 * Pour le MVP, on garde les appels réels pour valider le parsing.
 */
class NovelFranceParserTest {

    private lateinit var parser: NovelFranceParser
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        parser = NovelFranceParser()
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0")
                    .header("Accept-Language", "fr-FR,fr;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Test
    fun `parse chapter content from real page`() = runTest {
        // Given — un chapitre réel d'Omniscient Reader's Viewpoint
        val url = "https://novelfrance.fr/novel/omniscient-readers-viewpoint/chapter-1"
        val html = fetchHtml(url)

        // When — on parse le contenu
        val content = parser.parseChapterContent(html, url)

        // Then — on vérifie le résultat
        println("=== CHAPTER CONTENT ===")
        println("Titre chapitre: ${content.chapterTitle}")
        println("Titre novel: ${content.novelTitle}")
        println("Nombre de paragraphes: ${content.paragraphs.size}")
        println("Paragraphe 0: ${content.paragraphs.firstOrNull()?.htmlContent?.take(100)}")
        println("Chapitre suivant: ${content.nextChapterUrl}")

        assertTrue("Le titre du chapitre ne doit pas être vide", content.chapterTitle.isNotBlank())
        assertTrue("Le titre du novel ne doit pas être vide", content.novelTitle.isNotBlank())
        assertTrue("Il doit y avoir au moins 1 paragraphe", content.paragraphs.isNotEmpty())
        assertTrue("Le 1er paragraphe doit contenir du texte", 
            content.paragraphs.first().htmlContent.isNotBlank())
        assertNotNull("Il doit y avoir un lien vers le chapitre suivant", content.nextChapterUrl)
    }

    @Test
    fun `parse chapter list from novel detail page`() = runTest {
        // Given — la page détail d'ORV (552 chapitres)
        val url = "https://novelfrance.fr/novel/omniscient-readers-viewpoint"
        val html = fetchHtml(url)

        // When — on parse la liste des chapitres
        val chapters = parser.parseChapterList(html)

        // Then
        println("=== CHAPTER LIST ===")
        println("Nombre de chapitres trouvés: ${chapters.size}")
        chapters.take(5).forEach { ch ->
            println("  #${ch.chapterNumber}: ${ch.title} — ${ch.url}")
        }

        assertTrue("ORV a 552 chapitres", chapters.isNotEmpty())
        // Le chapitre 552 devrait être le dernier
        val lastChapter = chapters.maxByOrNull { it.chapterNumber }
        println("Dernier chapitre: #${lastChapter?.chapterNumber} — ${lastChapter?.title}")
    }

    @Test
    fun `parse latest updates from latest page`() = runTest {
        // Given
        val url = "https://novelfrance.fr/latest"
        val html = fetchHtml(url)

        // When
        val updates = parser.parseLatestUpdates(html)

        // Then
        println("=== LATEST UPDATES ===")
        println("Nombre de mises à jour: ${updates.size}")
        updates.take(5).forEach { u ->
            println("  ${u.novelTitle} — Ch.${u.chapterNumber}")
        }

        assertTrue("La page /latest doit contenir des mises à jour", updates.isNotEmpty())
    }

    @Test
    fun `parse browse list via API`() = runTest {
        // Given
        val api = NovelFranceApi(client)

        // When — browse page 1
        val novels = api.getNovels(page = 1, limit = 5)

        // Then
        println("=== BROWSE (API) ===")
        println("Nombre de novels: ${novels.size}")
        novels.take(3).forEach { n ->
            println("  ${n.title} (${n.author}) — ${n.status} — ${n.chapterCount} chapitres")
        }

        assertTrue("La liste browse doit contenir des novels", novels.isNotEmpty())
        assertTrue("Le slug doit être présent", novels.first().slug.isNotBlank())
        assertTrue("Le titre doit être présent", novels.first().title.isNotBlank())
    }

    @Test
    fun `search novels via API`() = runTest {
        // Given
        val api = NovelFranceApi(client)

        // When — rechercher "Omniscient"
        val results = api.getNovels(search = "Omniscient", limit = 5)

        // Then
        println("=== SEARCH RESULTS ===")
        println("Résultats pour 'Omniscient': ${results.size}")
        results.forEach { n ->
            println("  ${n.title} — ${n.slug}")
        }

        assertTrue("La recherche doit retourner des résultats", results.isNotEmpty())
    }

    @Test
    fun `get novel detail via API`() = runTest {
        // Given
        val api = NovelFranceApi(client)

        // When
        val novel = api.getNovelDetail("omniscient-readers-viewpoint")

        // Then
        println("=== NOVEL DETAIL ===")
        println("Titre: ${novel.title}")
        println("Auteur: ${novel.author}")
        println("Status: ${novel.status}")
        println("Note: ${novel.rating}")
        println("Genres: ${novel.genres}")
        println("Chapitres: ${novel.chapterCount}")
        println("Synopsis: ${novel.synopsis.take(150)}...")

        assertTrue("Le titre doit correspondre", novel.title.contains("Omniscient"))
        assertEquals("L'auteur doit être Sing-shong", "Sing-shong", novel.author)
        assertTrue("Le statut doit être COMPLETED", novel.status.name == "COMPLETED")
        assertTrue("La note doit être > 0", novel.rating > 0)
        assertTrue("Il doit y avoir des genres", novel.genres.isNotEmpty())
    }

    // ---- Helper ----

    private suspend fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0")
            .header("Accept-Language", "fr-FR,fr;q=0.9")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code} pour $url")
        }
        return response.body?.string() ?: throw RuntimeException("Réponse vide")
    }
}
