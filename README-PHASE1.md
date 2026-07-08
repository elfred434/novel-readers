# NovelReader — Phase 1 : Fondations

## ✅ Ce qui a été généré

### Architecture du projet
```
NovelReader/
├── build.gradle.kts                    # Build racine (plugins)
├── settings.gradle.kts                 # Configuration Gradle
├── gradle/libs.versions.toml           # Version catalog (dépendances)
└── app/
    ├── build.gradle.kts                # Build app (Compose, Hilt, Room, OkHttp, Jsoup)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/values/
        │   │   ├── strings.xml
        │   │   └── themes.xml
        │   └── java/com/novelreader/
        │       ├── NovelReaderApp.kt           # @HiltAndroidApp
        │       ├── MainActivity.kt              # @AndroidEntryPoint (placeholder UI)
        │       ├── di/
        │       │   └── AppModule.kt             # Module Hilt (OkHttp, Room, sources)
        │       └── data/
        │           ├── model/
        │           │   ├── Novel.kt             # Modèle domaine Novel
        │           │   └── Chapter.kt           # Modèles ChapterPreview, ChapterContent, Paragraph
        │           ├── local/
        │           │   ├── AppDatabase.kt       # Base Room (version 1)
        │           │   ├── entity/
        │           │   │   ├── NovelEntity.kt       # Table novels
        │           │   │   ├── ChapterEntity.kt     # Table chapters
        │           │   │   └── ChapterContentEntity.kt # Cache chapitres téléchargés
        │           │   └── dao/
        │           │       ├── NovelDao.kt
        │           │       ├── ChapterDao.kt
        │           │       └── ChapterContentDao.kt
        │           ├── remote/
        │           │   ├── source/
        │           │   │   └── NovelSource.kt       # Interface extensible (pattern Mihon)
        │           │   └── novelfrance/
        │           │       ├── NovelFranceSource.kt # Implémentation NovelFrance
        │           │       ├── NovelFranceApi.kt    # Client API REST
        │           │       └── NovelFranceParser.kt # Parser HTML + JSON embarqué
        │           └── repository/
        │               └── NovelRepository.kt       # Repository central
        └── test/
            └── java/com/novelreader/data/remote/novelfrance/
                └── NovelFranceParserTest.kt    # Tests unitaires avec vrais appels réseau
```

### Choix techniques clés

| Composant | Choix | Justification |
|-----------|-------|---------------|
| UI | Jetpack Compose | Moderne, réactif, standard Android 2025+ |
| DI | Hilt | Standard Android, intégration ViewModel native |
| DB | Room | Robuste, Flow réactif, DAO testables |
| HTTP | OkHttp | Léger, performant, intercepteurs |
| Parsing HTML | Jsoup | Standard pour l'extraction de contenu web |
| Sérialisation | kotlinx.serialization | Natif Kotlin, type-safe, performant |

### Résultats des tests sur le site réel (juillet 2026)

| Fonctionnalité | Statut | Détails |
|---------------|--------|---------|
| Browse novels (API) | ✅ OK | `/api/novels` — 499 novels, paginé |
| Recherche (API) | ⚠️ Limité | L'API retourne tout — filtre à implémenter côté client |
| Détail novel (API) | ✅ OK | `/api/novels/{slug}` — Titre, auteur, genres, status, chapitres |
| Liste chapitres (HTML) | ✅ OK (partiel) | 50 premiers chapitres via JSON embarqué |
| Contenu chapitre (HTML) | ✅ OK | Paragraphes avec HTML `<i>` préservé, 66 para pour ch.1 |
| Dernières mises à jour | ✅ OK | Parsing Jsoup du DOM /latest |

## 📝 Découvertes importantes sur le site

### 1. Site Next.js 14+ avec RSC
Le site utilise React Server Components. Le contenu est sérialisé en JSON
dans le flux `__next_f.push([1, "...data..."])`. **Mais** le texte est présent
dans le HTML initial (SSR) — pas de JS nécessaire pour le contenu.

### 2. Parsing prioritaire : JSON embarqué
Notre `NovelFranceParser` extrait les objets JSON directement :
- **`initialChapter`** → contenu du chapitre avec `paragraphs[]`
- **`initialChaptersResponse`** → liste des chapitres du novel

### 3. Limitations connues (MVP)

| Problème | Cause | Solution envisagée |
|----------|-------|-------------------|
| **50 chapitres max** par page | Next.js ne charge que `take:50` | Charge via scroll (WebView + injection JS) OU pagination API (à découvrir) |
| **Recherche API inefficace** | `/api/novels?search=X` ignore le paramètre | Filtrer côté client OU utiliser l'endpoint `/search` |
| **Fallback Jsoup DOM** | Le DOM final n'est pas dans le HTML initial | L'extraction JSON embarqué couvre 100% des cas testés |

### 4. Pas de WebView nécessaire
Le contenu texte des chapitres est bien présent dans le HTML initial.
La stratégie de fallback WebView n'est pas nécessaire pour le MVP.
Le parsing JSON embarqué fonctionne à 100% sur les pages testées.

## Prochaines étapes (Phase 2)

Une fois la Phase 1 validée, la Phase 2 ajoutera :
- Écrans Browse + recherche (UI Jetpack Compose)
- Écran Bibliothèque (grille/liste des novels suivis)
- Écran Détail novel (synopsis, infos, liste chapitres)
- Navigation Compose entre les écrans
- ViewModels et StateFlow
