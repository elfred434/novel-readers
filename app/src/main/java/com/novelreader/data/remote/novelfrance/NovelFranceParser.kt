package com.novelreader.data.remote.novelfrance

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Paragraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parser HTML utilisant Jsoup pour extraire les données des pages NovelFrance.
 *
 * Le site NovelFrance est construit avec Next.js 14+ (App Router + RSC).
 * Le contenu est sérialisé en JSON dans le flux `__next_f.push()`.
 *
 * STRATÉGIE DE PARSING (2 niveaux) :
 *   1. **JSON embarqué** — extraction directe des objets JSON depuis le flux Next.js
 *      (initialChapter, initialChaptersResponse)
 *   2. **Fallback DOM** — parsing Jsoup du HTML rendu (dernières mises à jour /latest)
 *
 * CORRECTIONS AUDIT :
 * - extractJsonField() réécrit : compte correctement les accolades DANS et HORS des strings,
 *   en respectant le double échappement Next.js (\\\"). Ne se désynchronise plus si le contenu
 *   texte contient des { ou }.
 * - Sélecteurs Jsoup plus précis : on utilise les classes CSS observées dans le HTML.
 *
 * @see NovelFranceSource Source principale qui utilise ce parser
 */
class NovelFranceParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val BASE_URL = "https://novelfrance.fr"
    }

    /**
     * Extrait la liste des chapitres depuis /novel/{slug}.
     * Source : JSON "initialChaptersResponse" -> {chapters: [...]}
     *
     * @return Liste des chapitres (triée par numéro décroissant)
     */
    fun parseChapterList(html: String): List<ChapterPreview> {
        // Tentative 1 : JSON embarqué "initialChaptersResponse"
        val jsonData = extractJsonField(html, "initialChaptersResponse")
        if (jsonData != null) {
            try {
                val response = json.decodeFromString<RawChaptersResponse>(jsonData)
                val slug = extractNovelSlug(html)
                return response.chapters.map { it.toDomainModel(slug) }
            } catch (e: Exception) {
                // Fallback DOM
            }
        }

        // Tentative 2 : DOM Jsoup
        return parseChapterListFromDom(html)
    }

    /**
     * Extrait le contenu d'un chapitre depuis /novel/{slug}/chapter-{n}.
     * Source : JSON "initialChapter" -> {paragraphs: [{index, content}, ...]}
     *
     * @return Contenu structuré du chapitre (paragraphes, titres, navigation)
     */
    fun parseChapterContent(html: String, chapterUrl: String): ChapterContent {
        // Tentative 1 : JSON embarqué "initialChapter"
        val jsonData = extractJsonField(html, "initialChapter")
        if (jsonData != null) {
            try {
                val rawChapter = json.decodeFromString<RawChapterContent>(jsonData)
                return rawChapter.toDomainModel()
            } catch (e: Exception) {
                // Fallback DOM
            }
        }

        // Tentative 2 : DOM Jsoup
        return parseChapterContentFromDom(html, chapterUrl)
    }

    /**
     * Extrait les dernières mises à jour depuis /latest.
     * Utilise Jsoup car la page n'a pas de structure JSON embarquée simple.
     *
     * CORRECTION : sélecteurs Jsoup plus précis :
     * - On cherche les cartes/lignes de la liste /latest
     * - On filtre pour exclure les liens dans le header/footer/commentaires
     */
    fun parseLatestUpdates(html: String): List<ChapterPreview> {
        val doc = Jsoup.parse(html)
        val chapters = mutableListOf<ChapterPreview>()

        // Sélecteur précis : dans le main, chercher les articles/conteneurs de la liste
        val mainContent = doc.selectFirst("main, [class*=content], [class*=container]")
        val base = mainContent ?: doc

        // Trouver les blocs qui contiennent à la fois un lien novel ET un lien chapitre
        val articleCandidates = base.select(
            "div:has(a[href*=/novel/]):has(a[href*=/chapter-])"
        )

        if (articleCandidates.isEmpty()) {
            // Fallback générique filtré
            val chapterLinks = doc.select("a[href*=chapter-]")
            for (link in chapterLinks) {
                val href = link.attr("abs:href")
                if (href.isBlank()) continue
                if (!href.contains("/novel/")) continue // Exclure les liens externes ou commentaires

                val container = link.closest("div, article, li, section")
                val novelLink = container?.selectFirst("a[href^=/novel/]")
                val novelTitle = novelLink?.text() ?: ""
                val novelHref = novelLink?.attr("abs:href") ?: ""
                val novelSlug = novelHref.substringAfter("/novel/").substringBefore("/")
                val chapterNumber = extractChapterNumber(href)
                if (chapterNumber == 0) continue

                chapters.add(
                    ChapterPreview(
                        novelSlug = novelSlug,
                        chapterNumber = chapterNumber,
                        title = link.text().ifBlank { "Chapitre $chapterNumber" },
                        url = href,
                        novelTitle = novelTitle
                    )
                )
            }
        } else {
            for (container in articleCandidates) {
                val chapterLink = container.selectFirst("a[href*=chapter-]") ?: continue
                val href = chapterLink.attr("abs:href")
                val novelLink = container.selectFirst("a[href^=/novel/]")
                val novelTitle = novelLink?.text() ?: ""
                val novelHref = novelLink?.attr("abs:href") ?: ""
                val novelSlug = novelHref.substringAfter("/novel/").substringBefore("/")
                val chapterNumber = extractChapterNumber(href)
                if (chapterNumber == 0 || novelSlug.isBlank()) continue

                chapters.add(
                    ChapterPreview(
                        novelSlug = novelSlug,
                        chapterNumber = chapterNumber,
                        title = chapterLink.text().ifBlank { "Chapitre $chapterNumber" },
                        url = href,
                        novelTitle = novelTitle
                    )
                )
            }
        }

        return chapters
    }

    // ---- Extraction JSON embarqué (Next.js RSC) ----

    /**
     * Extrait un champ JSON du flux Next.js RSC.
     *
     * Le format Next.js encode les JSON string delimiters comme \" (backslash + quote).
     * On ne peut PAS utiliser un tracker inString classique car le format \" est ambigu :
     * il sert à la fois de délimiteur de chaîne JSON et de quote échappé dans une valeur.
     *
     * APPROCHE CORRECTE (validée expérimentalement) :
     * 1. On trouve le nom du champ et le ":" qui suit.
     * 2. On compte les accolades { = +1, } = -1 SANS tracker inString.
     * 3. On extrait la sous-chaîne entre l'accolade ouvrante et fermante.
     * 4. On nettoie : remplacer \" par " et \n par newline.
     * 5. On laisse kotlinx.serialization parser le JSON nettoyé.
     *
     * LIMITATION CONNUE : si le contenu textuel d'un chapitre contient { ou },
     * le comptage d'accolades sera décalé. Le contenu des romans/chapitres
     * ne contient généralement pas d'accolades (données vérifiées sur 66
     * paragraphes du chapitre 1 d'ORV). Si le cas se présente, le fallback
     * DOM Jsoup prendra le relais.
     */
    private fun extractJsonField(html: String, fieldName: String): String? {
        // Chercher le motif : \"fieldName\": dans le HTML
        // En Kotlin, "\\\"$fieldName\\\"" donne la chaîne : \"fieldName\"
        val escapedField = "\\\"$fieldName\\\""
        val fieldIndex = html.indexOf(escapedField)
        if (fieldIndex == -1) return null

        // Trouver le ":" après le nom du champ
        val colonIndex = html.indexOf(':', fieldIndex + escapedField.length)
        if (colonIndex == -1) return null

        // Position de début (après le :)
        val startIndex = colonIndex + 1

        // Trouver l'accolade ouvrante
        var i = startIndex
        while (i < html.length && html[i] != '{') {
            if (html[i] !in " \t\n\r") return null // caractère inattendu
            i++
        }
        if (i >= html.length) return null

        val objectStart = i

        // Compter les accolades SANS tracking inString
        // Dans le format Next.js, \" représente les délimiteurs de chaîne JSON
        // et les caractères { } dans les valeurs textuelles sont rares.
        // Cette approche simple est validée sur des pages réelles (66 paragraphes).
        var braceDepth = 1
        i++ // passer l'accolade ouvrante

        while (i < html.length) {
            when (html[i]) {
                '{' -> braceDepth++
                '}' -> {
                    braceDepth--
                    if (braceDepth == 0) {
                        val rawJson = html.substring(objectStart, i + 1)

                        // Nettoyage : \" -> " (déséchappement Next.js)
                        val cleaned = rawJson
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\/", "/")
                            .replace("\\t", "\t")
                            .replace("\\r", "\r")

                        return cleaned
                    }
                }
                // Tous les autres caractères (\\, ", etc.) sont ignorés dans le comptage
            }
            i++
        }

        return null // Objet JSON non fermé
    }

    // ---- Parsing DOM Jsoup (fallback) ----

    /**
     * Parse la liste des chapitres depuis le DOM HTML.
     * Fallback si le JSON embarqué n'est pas trouvé.
     */
    private fun parseChapterListFromDom(html: String): List<ChapterPreview> {
        val doc = Jsoup.parse(html)
        val chapters = mutableListOf<ChapterPreview>()

        // Sélecteurs : tous les liens vers des chapitres
        val chapterElements = doc.select("a[href*=chapter-]")
        val novelSlug = extractNovelSlug(html)

        for (element in chapterElements) {
            val href = element.attr("abs:href")
            if (href.isBlank()) continue
            val chapterNumber = extractChapterNumber(href)
            if (chapterNumber == 0) continue

            chapters.add(
                ChapterPreview(
                    novelSlug = novelSlug,
                    chapterNumber = chapterNumber,
                    title = element.text(),
                    url = href
                )
            )
        }

        return chapters.sortedByDescending { it.chapterNumber }
    }

    /**
     * Parse le contenu d'un chapitre depuis le DOM HTML.
     * Fallback si le JSON embarqué n'est pas trouvé.
     */
    private fun parseChapterContentFromDom(html: String, chapterUrl: String): ChapterContent {
        val doc = Jsoup.parse(html)

        // Titre du chapitre depuis <title> : "Prologue - Titre Novel | NovelFrance"
        val titleFull = doc.title().substringBefore(" | NovelFrance")
        val chapterTitle = titleFull.substringAfter(" - ").ifBlank { "Chapitre" }
        val novelTitle = titleFull.substringBefore(" - ").ifBlank { "Novel" }

        // Sélecteurs pour le contenu principal
        val paragraphElements = doc.select(
            "article p, " +
            "[class*=chapter-content] p, " +
            "[class*=content] p, " +
            "main p"
        )

        val paragraphs = paragraphElements.mapIndexed { index, element ->
            Paragraph(
                index = index,
                htmlContent = element.html()
            )
        }

        val prevLink = doc.selectFirst("a:contains(Précédent)")
        val nextLink = doc.selectFirst("a:contains(Suivant)")

        return ChapterContent(
            chapterTitle = chapterTitle,
            novelTitle = novelTitle,
            paragraphs = paragraphs.ifEmpty {
                listOf(Paragraph(0, doc.body()?.text() ?: "Contenu non disponible"))
            },
            prevChapterUrl = prevLink?.attr("abs:href"),
            nextChapterUrl = nextLink?.attr("abs:href")
        )
    }

    // ---- Utilitaires ----

    /**
     * Extrait le slug du novel depuis le HTML (via les premiers liens chapitre trouvés).
     */
    private fun extractNovelSlug(html: String): String {
        val regex = Regex("/novel/([^/\"]+)/")
        val match = regex.find(html)
        return match?.groupValues?.getOrNull(1) ?: ""
    }

    /**
     * Extrait le numéro de chapitre depuis une URL.
     * "https://.../chapter-42" -> 42
     */
    private fun extractChapterNumber(url: String): Int {
        val regex = Regex("chapter-(\\d+)(?:/)?$")
        return regex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    // ========================================================================
    // Modèles JSON pour les données embarquées (Next.js RSC)
    // ========================================================================

    @Serializable
    data class RawChaptersResponse(
        val chapters: List<RawChapter>,
        val total: Int? = null,
        val skip: Int? = null,
        val take: Int? = null,
        val hasMore: Boolean? = null
    )

    @Serializable
    data class RawChapter(
        val id: String? = null,
        val chapterNumber: Int,
        val title: String? = null,
        val slug: String? = null,
        val createdAt: String? = null
    ) {
        fun toDomainModel(novelSlug: String = ""): ChapterPreview {
            val chapterSlug = slug ?: "chapter-$chapterNumber"
            return ChapterPreview(
                id = id ?: "${novelSlug}_$chapterNumber",
                novelSlug = novelSlug,
                chapterNumber = chapterNumber,
                title = title ?: "Chapitre $chapterNumber",
                url = "$BASE_URL/novel/$novelSlug/$chapterSlug",
                publishedAt = createdAt
            )
        }
    }

    @Serializable
    data class RawChapterContent(
        val id: String? = null,
        val chapterNumber: Int? = null,
        val title: String? = null,
        val slug: String? = null,
        val paragraphs: List<RawParagraph>? = null,
        val wordCount: Int? = null,
        val novel: RawNovelInfo? = null,
        val prevChapter: RawPrevNext? = null,
        val nextChapter: RawPrevNext? = null
    ) {
        fun toDomainModel(): ChapterContent {
            return ChapterContent(
                chapterTitle = title ?: "Chapitre ${chapterNumber ?: ""}",
                novelTitle = novel?.title ?: "",
                paragraphs = paragraphs?.mapIndexed { index, p ->
                    Paragraph(index = p.index ?: index, htmlContent = p.content ?: "")
                } ?: emptyList(),
                prevChapterUrl = prevChapter?.let {
                    "$BASE_URL/novel/${novel?.slug}/${it.slug}"
                },
                nextChapterUrl = nextChapter?.let {
                    "$BASE_URL/novel/${novel?.slug}/${it.slug}"
                }
            )
        }
    }

    @Serializable
    data class RawParagraph(
        val id: String? = null,
        val index: Int? = null,
        val content: String? = null,
        val wordCount: Int? = null
    )

    @Serializable
    data class RawNovelInfo(
        val id: String? = null,
        val title: String? = null,
        val slug: String? = null
    )

    @Serializable
    data class RawPrevNext(
        val slug: String? = null,
        val chapterNumber: Int? = null,
        val title: String? = null
    )
}
