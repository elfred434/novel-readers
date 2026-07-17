# 🔍 Analyse complète du code — NovelReader

> Analyse fichier par fichier, bloc par bloc / ligne par ligne pour les points significatifs.
> Date : 2026-07-17 · Branche analysée : `arena/019f7023-novel-readers` (base `main` @ `6745533`)
> Périmètre : 64 fichiers Kotlin (~8 120 lignes), Gradle, ressources, CI/CD, tests.

---

## 1. Vue d'ensemble

**NovelReader** est une application Android de lecture de novels (web novels traduits en français), inspirée de Mihon/Tachiyomi. Elle scrape le site `novelfrance.fr` (Next.js) via son API REST et le parsing HTML.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ UI (Jetpack Compose, Material 3, thème "Studio Noir")       │
│  Screens ──► ViewModels (StateFlow) ──► hiltViewModel()     │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│ NovelRepository (source de vérité unique côté réseau/cache) │
└───┬──────────────┬───────────────┬──────────────┬───────────┘
    │              │               │              │
┌───▼────┐   ┌─────▼─────┐   ┌─────▼──────┐  ┌───▼───────────┐
│NovelSource│  Room (SQLite)│  StorageManager│  DownloadManager│
│(NovelFrance)│ novels       │ fichiers JSON  │ queue en mémoire│
│ API + Jsoup │ chapters     │ interne ou SAF │ + ForegroundSvc │
└────────┘   │ chapter_content│              └────────────────┘
             │ categories     │
             └───────────────┘
```

### Stack technique (cohérente et moderne)

| Couche | Choix | Verdict |
|---|---|---|
| Langage | Kotlin 2.0.21, JVM 17 | ✅ récent |
| UI | Compose BOM 2024.12.01 + Material 3 | ✅ |
| DI | Hilt 2.53.1 (KSP) | ✅ |
| DB | Room 2.6.1 (KSP) | ✅ |
| Réseau | OkHttp 4.12 + Jsoup 1.18.3 | ✅ (pas de Retrofit, justifié pour du scraping) |
| JSON | kotlinx.serialization 1.7.3 | ✅ |
| Async | Coroutines 1.9 + Flow | ✅ |
| Images | Coil 2.7 | ✅ |
| Background | WorkManager 2.9.1 + hilt-work | ✅ |
| Min SDK | 26 (Android 8.0), target/compile 35 | ✅ couvre ~95 %+ des appareils |

### Statistiques

- **64 fichiers Kotlin** dont **4 fichiers vides** (0 octet) : `ChapterFileManager.kt`, `MigrationManager.kt`, `OnboardingScreen.kt`, `OnboardingViewModel.kt`.
- **1 seul fichier de test** (`NovelFranceParserTest`) — des tests d'intégration réseau, pas des tests unitaires.
- Gros fichiers : `LibraryScreen.kt` (553 l.), `NovelFranceParser.kt` (417 l.), `DetailScreen.kt` (350 l.), `SettingsScreen.kt` (333 l.).

---

## 2. Configuration & Build

### 2.1 `settings.gradle.kts`

```kotlin
pluginManagement { repositories { google { content { includeGroupByRegex(...) } }; mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) ... }
rootProject.name = "NovelReader"; include(":app")
```

- **Ligne par ligne** : `pluginManagement` restreint le repo Google aux groupes `com.android.*`, `com.google.*`, `androidx.*` → build plus rapide et plus sûr (évite de résoudre n'importe quoi depuis google()). `FAIL_ON_PROJECT_REPOS` interdit les `repositories {}` au niveau module → centralisation stricte. Bonne pratique.
- ✅ Propre, mono-module `:app`.

### 2.2 `build.gradle.kts` (racine)

- Déclare les 6 plugins avec `apply false` : pattern standard « version catalog + plugins déclaratifs ». Rien à signaler.

### 2.3 `gradle/libs.versions.toml` (catalogue de versions)

- Versions homogènes et récentes (AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28 → compatibles).
- ⚠️ `hilt-work` et `androidx-navigation-compose` ont leur version **en dur** (`1.2.0`, `2.8.5`) au lieu d'une référence `[versions]` — incohérence mineure du catalogue.
- ⚠️ `compose-compiler = "1.5.15"` déclaré mais **inutilisé** (avec Kotlin 2.0 le compilateur Compose est le plugin `kotlin-compose`). Entrée morte.
- Dépendances de test complètes (JUnit4, MockK, Turbine, coroutines-test) mais **quasi inutilisées** (un seul test, sans MockK/Turbine).

### 2.4 `app/build.gradle.kts` — points notables

```kotlin
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val commitCount = try { Runtime.getRuntime().exec("git rev-list --count HEAD")... }
versionCode = runNumber ?: commitCount ?: 1
versionName = "1.0.${versionCode}"
```

- ✅ Astucieux : `versionCode` auto-incrémenté (CI → run number ; local → nombre de commits).
- ⚠️ `Runtime.exec("git ...")` à **chaque configuration Gradle** : ralentit le build, échoue silencieusement hors d'un clone git (retombe sur `1`).

```kotlin
signingConfigs {
    val keystoreFile = rootProject.file("novelreader.keystore")
    if (keystoreFile.exists()) {
        create("release") {
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "novelreader"
            keyAlias = System.getenv("KEY_ALIAS") ?: "novelreader"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "novelreader"
        }
    }
}
```

- 🔴 **CRITIQUE (sécurité)** : mots de passe de signature **en clair par défaut** (`"novelreader"`) dans le build script. Combiné au workflow CI qui génère le keystore avec ces mêmes secrets publics, **n'importe qui peut re-créer la clé de signature** et signer un APK malveillant accepté comme « mise à jour » légitime (même schéma de signature). Le keystore devrait être un secret GitHub (base64) avec mots de passe en secrets, sans valeurs par défaut.

```kotlin
release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.findByName("release") ?: signingConfigs.findByName("debug") }
debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
```

- ✅ Fallback debug signing explicite, suffixe `.debug` permettant l'installation parallèle debug/release.

### 2.5 `app/proguard-rules.pro` — 🔴 **bug majeur en release**

```
-keepclassmembers class com.novelreader.data.model.** { *; }
-keepclassmembers class com.novelreader.data.local.entity.** { *; }
-keep class org.jsoup.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
```

- 🔴 **Les DTO sérialisés ne sont PAS protégés** : `NovelFranceApi.BrowseResponse/ApiNovel/NovelDetailResponse/ChaptersResponse`, `NovelFranceParser.Raw*`, `NovelRepository.StorageContent/ChapterMeta/SerializableParagraph`, `DownloadManager.StoredDownload*`, `AppUpdateChecker.GitHubRelease*` sont tous dans `com.novelreader.data.**` **hors** `data.model` et `data.local.entity`. Avec `isMinifyEnabled = true` (R8 full mode), leurs champs seront **obfusqués** → `kotlinx.serialization` ne retrouvera plus les clés JSON → **tout le parsing API cassé en build release** (browse, détail, chapitres, cache, update-checker). Les règles `kotlinx.serialization.json.**` ne protègent que les classes de la lib, pas celles de l'app.
  - **Correctif** : ajouter `-keepclasseswithmembers class com.novelreader.** { kotlinx.serialization.KSerializer serializer(...); }` et `-keepattributes *Annotation*, InnerClasses, Signature` (ou annoter les DTO avec `@Keep`).
- ✅ `-keepattributes *Annotation*` présent (nécessaire pour `@Serializable`/`@SerialName`) — mais insuffisant seul.

### 2.6 `app/src/main/AndroidManifest.xml`

- Lignes 5-9 : permissions `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — cohérent avec les fonctionnalités.
- 🔴 **Permission manquante : `REQUEST_INSTALL_PACKAGES`**. `AppUpdateInstaller` lance un `Intent(ACTION_VIEW)` sur un APK : sur Android 8+, sans cette permission, l'installateur système **refuse l'installation** depuis une source inconnue de l'app → la fonctionnalité d'auto-update est **inutilisable**.
- ⚠️ `POST_NOTIFICATIONS` déclarée mais **jamais demandée à l'exécution** (Android 13+) : la notification du `DownloadService` n'apparaîtra pas dans le panneau (le service foreground fonctionne quand même).
- ⚠️ `android:allowBackup="true"` : la bibliothèque SQLite + DataStore sont incluses dans Auto Backup (à documenter ; acceptable).
- ✅ `usesCleartextTraffic="false"`, service non exporté, FileProvider correctement déclaré, initialiseur WorkManager par défaut désactivé (remplacé par la config Hilt dans `NovelReaderApp`).

### 2.7 `.github/workflows/build.yml`

- Pipeline clair : checkout (fetch-depth 0 → nécessaire pour `git rev-list --count`), JDK 21, SDK Android, cache Gradle, build release + debug, artifact, release GitHub.
- 🔴 **CRITIQUE (CI)** : `Generate keystore for signing` **régénère un keystore neuf à chaque run** (`keytool -genkey ... -storepass novelreader`). Conséquence : **chaque release est signée avec une clé différente** → Android refusera toute mise à jour d'une version à l'autre (signature mismatch). L'auto-update intégré (`AppUpdateChecker`/`AppUpdateInstaller`) ne pourra jamais fonctionner entre deux releases. Le keystore doit être persisté (secret `KEYSTORE_BASE64`) et restauré.
- ⚠️ Les tests (`./gradlew test`) ne sont **jamais exécutés** en CI.
- ⚠️ `GITHUB_RUN_NUMBER` comme `versionCode` : monotone tant qu'il n'y a qu'un workflow — OK ici.
- ✅ Release conditionnée à `push` sur `main`, body en français, APK attaché.

### 2.8 Divers racine

- `.gitignore` : complet (Gradle, IDE, keystore, APK). ⚠️ **Mais `local.properties` est commité** alors qu'il est dans `.gitignore` (ajout forcé) : il contient le chemin SDK personnel `C:\Users\elfre\...` → fuite d'info locale + casse le build des autres machines. **À retirer du dépôt** (`git rm --cached local.properties`).
- `gradle.properties` : standard (`-Xmx2048m`, parallel, caching, AndroidX, nonTransitiveRClass).
- `README.md` : bien écrit, mais 🔗 les liens pointent vers `github.com/tonuser/NovelReader` (badge build, releases, issues) alors que le dépôt réel est `elfred434/novel-readers` → **badge et liens cassés**. Idem `CONTRIBUTING.md` (lien issues).
- `LICENSE` : GPL-3.0. ✅ cohérent avec le README.
- Templates d'issues (`bug_report.yml`, `feature_request.yml`) : propres, en français, champs requis.

---

## 3. Point d'entrée de l'application

### 3.1 `MainActivity.kt` (50 lignes)

| Lignes | Analyse |
|---|---|
| 20-24 | `@AndroidEntryPoint` + injection de `PreferencesManager` directement dans l'Activity — acceptable pour le thème, mais couple l'Activity à la couche data. |
| 28 | `enableEdgeToEdge()` — ✅ moderne ; le `Scaffold` gère les insets ensuite. |
| 31-38 | Collecte du thème : `collectAsState(initial = 1)` → **défaut DARK cohérent** avec le positionnement « Studio Noir ». Mapping `Int → AppTheme` par `when` avec `else -> DARK` (valeurs inconnues = sombre). ⚠️ La correspondance `0=SYSTEM, 1=DARK, 2=LIGHT, 3=AMOLED` repose sur l'ordre de l'enum `AppTheme` — fragile si l'enum est réordonnancé (les valeurs persistées dans DataStore deviendraient fausses). Mieux : mapper sur `AppTheme.entries[i]` avec fallback, ou stocker le nom. |
| 40-47 | `NovelReaderTheme` → `Surface` plein écran → `NovelReaderNavigation()`. Structure minimale et correcte. |

### 3.2 `NovelReaderApp.kt` (116 lignes)

- **Rôle** : classe `Application` Hilt + `Configuration.Provider` pour WorkManager.
- Lignes 87-90 : injection de `HiltWorkerFactory` dans la config WorkManager — c'est **pour ça** que le manifest désactive l'initialiseur par défaut. ✅ cohérent.
- Ligne 92 : `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` — scope application, jamais annulé (normal pour `Application`).
- `onCreate()` (94-100) : 4 initialisations — canaux de notification, stockage auto, check update, planification du worker.
- `checkAppUpdate()` (106-115) : ⚠️ silencieuse, se contente d'un `Log.i`. Le commentaire dit *« La notification sera affichée dans les paramètres »* mais **rien n'est stocké** — `SettingsViewModel` refait son propre appel réseau. Code partiellement mort (double vérification).
- `autoCreateStorage()` (122-133) : crée `filesDir/NovelReader/novels` au premier lancement → zéro permission, zéro friction. ✅ bonne UX.
- `createNotificationChannels()` (135-160) : 2 canaux (`novel_updates` IMPORTANCE_DEFAULT, `novel_downloads` IMPORTANCE_LOW sans badge). ⚠️ Le canal `novel_updates` n'est **jamais utilisé** (aucun code ne poste de notification de nouveaux chapitres — l'`UpdateWorker` ne notifie pas).
- `scheduleUpdates()` (162-165) : 🔴 **intervalle en dur `12h`** — la préférence `updateIntervalHours` réglable dans Settings (slider 4-48 h) **n'est jamais lue ici** → le réglage utilisateur est sans effet.

### 3.3 `di/AppModule.kt` (91 lignes)

- **Rôle** : module Hilt `@InstallIn(SingletonComponent)` — tous les providers sont `@Singleton`. ✅
- Lignes 199-205 : OkHttp unique, timeouts 15 s, intercepteur posant un **User-Agent Chrome mobile** + `Accept-Language: fr-FR` (nécessaire pour ne pas être bloqué par le site scrapé). ✅ pragmatique.
- Lignes 214-215 : `NovelFranceSource` est fournie **sous le type `NovelSource`** → le reste de l'app dépend de l'interface. ✅ bonne inversion de dépendance.
- Lignes 218-219 : `ExtensionManager` construit **manuellement** (`ExtensionManager().apply { registerSource(...) }`) alors que la classe a un `@Inject constructor` — ça fonctionne (le `@Provides` prime), mais le `@Inject constructor()` sur la classe est un piège : toute injection directe hors module créerait une instance **vide**. À nettoyer (retirer le `@Inject` de la classe).
- Ligne 227 : `Room.databaseBuilder(...).fallbackToDestructiveMigration()` — 🔴 **toute montée de version de schéma détruit la base** (bibliothèque, historique, états de lecture). La DB documente 4 versions (v1→v4) mais **aucune `Migration` n'existe** : un utilisateur de la v1 qui met à jour perd tout. Acceptable en MVP, à tracer.
- Lignes 222-223, 245-246, 249-250 : types pleinement qualifiés en ligne (`com.novelreader.data.network.NetworkStateManager`) au lieu d'imports — style à harmoniser.

---

## 4. `data/model` — modèles domaine

### 4.1 `Novel.kt` (44 lignes)

- `data class Novel` : 21 propriétés, modèle **domaine** découplé de Room et de l'API. ✅ bonne séparation (l'entité DB et le DTO API ont leurs propres classes).
- ⚠️ Plusieurs champs sans valeur par défaut alors que l'API les rend souvent absents (`slug`, `title`, `author`, `coverImageUrl`, `synopsis`, `rating`, `genres`, `chapterCount`, `sourceUrl`) — oblige les `toDomainModel()` à fournir des fallbacks (`"Inconnu"`, `""`, `0.0`) — c'est fait, mais le modèle autorise des états vides ambigus (`coverImageUrl = ""` géré dans l'UI, OK).
- `enum NovelStatus` (32-44) : `fromString()` tolère EN/FR (`"EN COURS"`, `"TERMINÉ"`, `"TERMINE"`). ⚠️ Ne gère pas `"HIATUS"`/`"CANCELLED"` alors que le thème définit `StatusHiatus`/`StatusCancelled` (Color.kt) → statuts affichés `UNKNOWN` pour ces cas. Mapping `"TERMINÉ"` : attention, `uppercase()` sur `"Terminé"` donne `"TERMINÉ"` (accent conservé) → OK ; `"termine"` sans accent donne `"TERMINE"` → aussi géré. ✅

### 4.2 `Chapter.kt` (36 lignes)

- `ChapterPreview` : représentation liste ; `novelTitle` et `wordCount` optionnels bien pensés (utilisés par /latest et le temps de lecture).
- `ChapterContent` : contenu complet avec navigation prev/next.
- `Paragraph(index, htmlContent)` : le HTML est conservé (rendu via `Html.fromHtml` côté lecteur). ⚠️ Aucun assainissement (whitelist) — le contenu vient d'un site tiers ; `Html.fromHtml` ignore les scripts, donc risque faible, mais du HTML inattendu peut casser la mise en page.

## 5. `data/local` — persistance Room

### 5.1 Entités

| Fichier | Points clés |
|---|---|
| `NovelEntity.kt` | PK = `slug` (naturelle, stable). `genres: List<String>` via TypeConverter. Champs de suivi : `addedAt`, `lastChapterRead`, `unreadChapterCount`, `storageFolderName` (ajout v4). ⚠️ `storageFolderName` documenté « basé sur le slug » mais **jamais renseigné** (défaut `""`, aucun code ne l'écrit ni ne le lit — `StorageManager` recalcule `sanitizeFolderName(slug)` à la volée). Champ mort. |
| `ChapterEntity.kt` | PK `id` = `"slug_number"` ; FK CASCADE vers `novels` ; index sur `novelSlug` et `chapterNumber`. ✅ Suit l'état : `isRead`, `readAt`, `isDownloaded`, `scrollPosition`. |
| `ChapterContentEntity.kt` | Cache du contenu : `paragraphsJson` (JSON sérialisé), FK CASCADE vers `chapters`. ⚠️ La cascade signifie que **tout REPLACE sur `chapters` supprime le contenu téléchargé** (voir bug §8.3 `cacheChapters`). |
| `CategoryEntity.kt` | `id` auto-généré, `position` pour l'ordre d'affichage. ✅ |
| `NovelCategoryCrossRef.kt` | Jonction N-N, PK composite, double CASCADE, index des deux côtés. ✅ modélisation correcte. |

### 5.2 `AppDatabase.kt` (47 lignes)

- Version 4, `exportSchema = false` (les schémas ne sont pas archivés → migrations futures à l'aveugle ; cohérent avec le `fallbackToDestructiveMigration` assumé).
- Le commentaire d'en-tête documente l'historique v1→v4. ✅ bonne trace.

### 5.3 `Converters.kt` (87 lignes)

- Un seul convertisseur `List<String> ↔ JSON` pour les genres, avec `ignoreUnknownKeys` et fallback `emptyList()` en cas de JSON corrompu (lignes 80-86). ✅ défensif.
- ⚠️ Instance `Json` créée par instance de `Converters` (Room en crée une) — coût négligeable.

### 5.4 DAO

**`NovelDao.kt` (56 lignes)** — CRUD complet + 2 Flow réactifs + requêtes ciblées (`updateUnreadCount`, `updateLastChapterRead`). ⚠️ Code mort : `getAllNovelsAlphabetical()`, `updateNovel()`, `deleteNovel()` ne sont appelés nulle part. ⚠️ `insertNovel(REPLACE)` réinitialise `addedAt/lastChapterRead/unreadChapterCount` si appelé sur un novel existant — aujourd'hui seulement utilisé à l'ajout, mais piège latent.

**`ChapterDao.kt` (68 lignes)** —
- `getChaptersForNovel` (Flow) trié `chapterNumber DESC` ; la version `Once` pour les workers. ✅
- `markAsRead` (l. 188-189) : `readAt = :readAt` avec valeur par défaut `System.currentTimeMillis()` — ✅ pratique.
- `resetAllDownloadedFlags` / `resetDownloadedFlags` : utilisés par la suppression de téléchargements. ✅
- ⚠️ Code mort : `getUnreadChapters()` et `getUnreadCount()` ne sont **jamais appelés** — pourtant `getUnreadCount` serait la **bonne** source pour `unreadChapterCount` (voir bug UpdateWorker §14).

**`ChapterContentDao.kt` (31 lignes)** — CRUD minimal ; `deleteOldContent(beforeTimestamp)` est détourné par `clearCache()` avec un timestamp futur pour tout effacer (hack lisible, fonctionne).

**`CategoryDao.kt` (101 lignes)** —
- Requêtes de jonction correctes (`INNER JOIN`), `@RewriteQueriesToDropUnusedColumns` pour le `SELECT *` en jointure. ✅
- `setCategoriesForNovel()` (l. 337-343) : `@Transaction` delete-all + ré-insertion — ✅ atomique.
- ⚠️ `addNovelToCategory` utilise `IGNORE` : un doublon est silencieusement ignoré (comportement souhaité ici).

### 5.5 `preferences/PreferencesManager.kt` (93 lignes)

- **Rôle** : façade DataStore (`preferencesDataStore("novelreader_settings")` délégué au Context, l. 17 — ✅ singleton implicite).
- 14 préférences typées (thème, intervalle, lecteur ×6, notifications, téléchargements ×3, SAF ×2). Pattern uniforme `Flow` + `edit {}`. ✅ propre.
- ⚠️ **6 préférences lecteur sont orphelines** : `readerFontSize`, `readerTheme`, `readerFont`, `readerLineHeight`, `readerPadding`, `readerPaginationMode` (l. 27-43) **ne sont lues/écrites par aucun écran** — `ReaderViewModel` garde ses réglages en mémoire seule (voir bug §17.4).
- Lignes 69-75 : `hasAnyStorageSync()` / `hasStorageLocationSync()` en `runBlocking` — ⚠️ risque d'ANR si appelées sur le thread UI ; de plus `hasStorageLocationSync()` ne vérifie **que SAF** (incohérent avec `hasAnyStorage()` qui accepte aussi l'interne). Les deux fonctions sont **inutilisées** → code mort à supprimer.
- `readerLineHeight` stockée en `Double` car DataStore n'a pas de clé `Float` — conversion correcte des deux côtés. ✅

## 6. `data/network/NetworkStateManager.kt` (124 lignes)

- **Rôle** : expose `isOnline`, `isOnWifi`, `networkType` en `callbackFlow` + requêtes ponctuelles synchrones.
- Lignes 124-141 : enregistre un `NetworkCallback` avec `NET_CAPABILITY_INTERNET`, émet l'état initial puis les changements, `awaitClose` désenregistre. ✅ pattern canonique, `distinctUntilChanged()` évite les doublons.
- ⚠️ **Triplication** : les 3 flows répètent la même structure (3 callbacks enregistrés si un écran collecte les 3 — c'est le cas de `SettingsViewModel`). Mutualisable via un seul flow d'événements partagé (`shareIn`).
- ⚠️ Subtilité `onLost` : avec plusieurs réseaux (WiFi + cellulaire en bascule), `onLost(wifi)` émet `false` alors que le cellulaire est actif → court moment « hors-ligne » suivi d'une correction via `onCapabilitiesChanged`. Impact faible ici (compteurs UI).
- Ligne 211 : Ethernet mappé sur `NetworkType.WIFI` — choix assumé (Ethernet = non mesuré → turbo autorisé). OK.

## 7. `data/remote` — scraping NovelFrance

### 7.1 `source/NovelSource.kt` (65 lignes)

- Interface complète à la Mihon : métadonnées (`id`, `name`, `baseUrl`, `lang`, `iconUrl`, `version`, `supportsLatest`) + 6 opérations suspendues. ✅
- `toExtensionInfo()` (l. 273-281) : fonction par défaut — léger couplage interface→data class d'affichage, acceptable.
- ✅ Permet d'ajouter d'autres sources sans toucher au repository.

### 7.2 `novelfrance/NovelFranceApi.kt` (211 lignes)

- **Rôle** : client REST des endpoints `/api/novels`, `/api/novels/{slug}`, `/api/chapters/{slug}`.
- `getNovels()` (33-51) : `limit.coerceIn(1, 50)`, paramètres optionnels posés seulement si non nuls. ✅
- `getChaptersPaginated()` (61-92) : boucle `skip/take=100` jusqu'à `hasMore=false` **ou page vide** (l. 88 — garde-fou anti-boucle infinie si l'API ment sur `hasMore`. ✅). `.reversed()` final : l'API renvoie DESC, l'app veut ASC. ⚠️ **Attention** : si l'API renvoie les pages en DESC, la concaténation est « page1(DESC) + page2(DESC)… » → le `reversed()` global ne restaure un ASC parfait que si chaque page est bien un bloc contigu DESC — c'est le cas avec `order=desc` et skip/take séquentiels. OK, mais fragile si l'API change.
- Ligne 78 : reconstruction d'URL `…/novel/$slug/${raw.slug ?: "chapter-${raw.chapterNumber}"}` — ⚠️ suppose que le slug de chapitre mène à une page lisible par le parser (vrai aujourd'hui).
- `executeGet()` (94-101) : exceptions `NovelFranceException(code, message)` — ✅ typées.
- `buildUrl()` (103-108) : ⚠️ encode les **valeurs** mais pas les clés (OK ici, clés constantes) ; ajoute toujours `?` même vide (cosmétique).
- DTO : `ApiNovel`/`NovelDetailResponse` avec `@SerialName("coverImage")`, `_count`, `firstChapter`… — bien alignés sur l'API réelle (les champs nullables ont tous des défauts). `toDomainModel()` préfixe les covers relatives avec le domaine (l. 142, 176). ✅
- 🔴 Rappel : ces DTO ne sont **pas couverts** par ProGuard (§2.5) → cassés en release minifiée.

### 7.3 `novelfrance/NovelFranceParser.kt` (417 lignes)

Le fichier le plus délicat du projet : extraction de JSON depuis le flux RSC de Next.js (`self.__next_f.push`).

- **Stratégie à 2 niveaux** (documentée l. 17-28) : JSON embarqué d'abord, DOM Jsoup en fallback. ✅ résilient.
- `extractJsonField()` (181-237) :
  - Cherche `\"fieldName\":` littéral (échappement Next.js), puis compte les accolades **sans tracker de chaînes** : `{`+1, `}`−1 jusqu'à profondeur 0 (l. 209-234).
  - Nettoie ensuite `\\\" → "`, `\\n → ⏎`, `\\/ → /`, `\\t`, `\\r` (l. 221-227).
  - ⚠️ **Limitation assumée en commentaire** (l. 175-179) : si le texte d'un chapitre contient `{` ou `}` **non échappés dans le flux**, le comptage se désynchronise. Le fallback DOM prend le relais — mais le fallback DOM (sélecteurs génériques `article p, main p`) peut ramasser des paragraphes parasites (nav, footer).
  - ⚠️ L'ordre des `replace` est important : `\\\"` d'abord ✅, mais `\\n`→newline **avant** que kotlinx ne parse : un `\\n` **littéral** dans le texte (l'utilisateur a tapé backslash-n) serait transformé en vrai saut de ligne — corruption marginale, acceptable.
  - ⚠️ Ne gère pas `\\uXXXX` ni `\\\\` (backslash seul). En pratique le contenu FR passe.
- `parseLatestUpdates()` (94-157) : double stratégie de sélecteurs (conteneurs `div:has(...)` puis liens `a[href*=chapter-]` filtrés). Filtrage des liens hors `/novel/` (l. 113). ✅ défensif.
- `extractChapterNumber()` (328-331) : regex `chapter-(\d+)(?:/)?$` — ⚠️ échoue sur tout slug non numérique (ex. `chapter-42-5` ou slug custom) → retourne 0 → **le chapitre est ignoré silencieusement** (l. 121, 142, 257). Même regex dupliquée dans `ReaderViewModel.extractChapterNumber` — à factoriser.
- `parseChapterContentFromDom()` (276-311) : fallback `paragraphs.ifEmpty { listOf(Paragraph(0, doc.body()?.text() ?: "Contenu non disponible")) }` — ⚠️ injecte **tout le texte de la page** comme unique paragraphe si les sélecteurs échouent : lisible mais brut.
- Modèles `Raw*` (337-416) : tous champs nullables avec défauts — ✅ robuste face aux variations de l'API. `RawChapterContent.toDomainModel()` reconstruit les URL prev/next à partir des slugs (l. 386-391) : si `novel?.slug` est null → URL `…/novel/null/…` invalide. ⚠️ cas rare mais possible.

### 7.4 `novelfrance/NovelFranceSource.kt` (73 lignes)

- Façade mince : délègue à l'API pour listes/détails/chapitres, au parser pour `/latest` et le contenu des chapitres (pages HTML). ✅ séparation nette.
- `fetchHtml()` (346-352) : `withContext(Dispatchers.IO)`, jette `NovelFranceException` si non-2xx ou corps vide. ✅
- ⚠️ `getLatestUpdates(page)` construit `…/latest?page=N` mais le parser ignore la pagination côté site — page 2+ fonctionne seulement si le site la supporte en HTML (non vérifié ; l'UI n'appelle que page 1 de toute façon).
- `NovelFranceException` : porte le code HTTP. ✅ (utilisé pour distinguer les erreurs).

## 8. `data/repository/NovelRepository.kt` (289 lignes)

### 8.1 Structure

- Dépend de `NovelSource` (interface) + 3 DAO. ✅ commentaire d'en-tête honnête sur les corrections d'audit.
- Les 6 premières méthodes (44-71) sont des **pass-through** vers la source — fines, OK.

### 8.2 Bibliothèque

- `addNovelToLibrary()` (81-94) : mappe le domaine vers l'entité ; les genres passent par le TypeConverter. ⚠️ Ne préserve pas `storageFolderName` ni les compteurs si le novel existe déjà (cf. §5.4 — non déclenché aujourd'hui car appelé uniquement hors bibliothèque).
- `removeNovelFromLibrary()` (97-99) : suppression en cascade (chapitres + contenus + jonctions). ⚠️ **Ne supprime pas les fichiers JSON** du stockage — c'est `DetailViewModel.toggleLibrary()` qui appelle `storageManager.deleteNovelFiles(slug)`. Mais `LibraryViewModel.confirmRemoveFromLibrary()` **ne le fait pas** → 🔴 **fuite de fichiers** : retirer un novel depuis la bibliothèque laisse les JSON téléchargés sur le disque (et l'écran Téléchargements continuera de les afficher).

### 8.3 🔴 BUG CRITIQUE — `cacheChapters()` (124-141)

```kotlin
val entities = chapters.map { preview -> ChapterEntity(id = chapterId(...), ..., publishedAt = preview.publishedAt) }
chapterDao.insertChapters(entities)   // OnConflictStrategy.REPLACE
```

- Chaque réinsertion crée une entité **neuve** : `isRead=false`, `readAt=null`, `isDownloaded=false`, `scrollPosition=0`, `novelTitle` écrasé.
- Avec REPLACE, Room fait DELETE+INSERT → **la FK CASCADE de `chapter_content` supprime aussi le contenu téléchargé en DB**.
- Conséquence : **à chaque rafraîchissement de la liste des chapitres** (ajout à la bibliothèque, `UpdateWorker` toutes les 12 h, `SingleNovelUpdateWorker`), **tout l'historique de lecture, les positions de scroll et le cache de contenu sont effacés**.
- **Correctif** : conserver l'existant — par ex. récupérer les entités actuelles et faire `existing?.copy(title=..., url=..., publishedAt=...) ?: newEntity`, ou insérer avec `IGNORE` puis mettre à jour les champs volatils.

### 8.4 Cache hors-ligne

- `downloadChapter()` (168-186) : stocke `StorageContent{meta, paragraphs}` en JSON dans `chapter_content`, puis `updateChapter(copy(isDownloaded = true))`. ✅ restaure aussi les titres (correction documentée).
- `getCachedChapter()` (193-216) : double lecture — nouveau format `StorageContent`, sinon **fallback ancien format** (liste nue de paragraphes, titres vides). ✅ migration douce pensée.
- `clearCache()` (224-227) : hack du timestamp futur (§5.4) + reset des flags. ⚠️ Ne supprime pas les fichiers JSON (fait par `DownloadManager.clearAll`).
- `deleteDownloadedChapterData()` / `deleteMultipleDownloadedChapters()` (234-247) : cohérents, DB + flags. ✅
- `chapterId()` (256-258) : format unique `"slug_N"` utilisé partout — ✅ centralisé dans le `companion`.

### 8.5 Modèles de cache (262-289)

- `ChapterMeta`, `StorageContent`, `SerializableParagraph` — `@Serializable` propres. 🔴 Non couverts par ProGuard (§2.5) → **le cache DB devient illisible en release** (JSON obfusqué à l'écriture ET à la lecture : en fait les clés seraient obfusquées de façon stable dans un même build, donc la lecture d'un cache écrit par le même build fonctionnerait, mais tout cache écrit avant une mise à jour de l'app serait perdu, et `getCachedChapter` lève des exceptions silencieuses → retour `null` → retéléchargement réseau. Dégradation, pas crash).

## 9. `data/download`

### 9.1 `DownloadManager.kt` (202 lignes)

- **Rôle** : file de téléchargement en mémoire (`MutableStateFlow<List<DownloadItem>>`), parallélisme adaptatif (2 par défaut → 5 en WiFi+haute vitesse), 3 essais max.
- `DownloadItem` / `DownloadStatus` (22-35) : machine à états claire (QUEUED → DOWNLOADING → COMPLETED/FAILED/CANCELLED).
- `updateMaxConcurrent()` (64-67) + observation du WiFi dans `init` (78-84) : ✅ réactif. ⚠️ `highDataModeEnabled` (setter custom l. 69-76) relit `isOnWifi.first()` — première émission d'un callbackFlow, OK.
- `enqueue()` / `enqueueAll()` (88-106) : anti-doublon sur `chapterId` sauf si FAILED (permet le retry), priorité en tête de file. ✅
- 🔴 **BUG — `cancel()` (108-111) est impuissant sur un téléchargement en cours** : il passe le statut à CANCELLED, mais `downloadItem()` (145-177) ne vérifie jamais ce statut — la coroutine continue, télécharge, écrit le fichier et **écrase CANCELLED par COMPLETED** (l. 165). Il n'y a pas de `Job` conservé ni de `ensureActive()`. Correctif : garder `Map<chapterId, Job>` et appeler `job.cancel()`, ou tester le statut avant chaque `_queue.update`.
- ⚠️ **Race condition** dans `processQueue()` (134-143) : appelé depuis plusieurs threads (setters, init collector, finally des coroutines) sans synchronisation ; `activeCount++/--` n'est pas atomique → peut dépasser `maxConcurrent` par rafales. En pratique l'effet est borné (quelques téléchargements en trop), mais `@Volatile` + `synchronized` ou un `Mutex`/canal serait plus sûr.
- `downloadItem()` (145-177) : progression simulée (0.1 → 0.5 → 1.0) car OkHttp ne stream pas la progression ici — honnête. Double écriture **DB + fichier JSON** (redondance assumée : le fichier survit à un clear de cache DB). Retry avec compteur dans `error` (l. 169). ✅
- `clearAll()` (179-185) : vide la file + DB + fichiers. ⚠️ N'annule pas les téléchargements en cours (même bug que `cancel`).
- Compteurs (187-189) : dérivés de la file — ✅.
- `StoredDownload`/`StoredDownloadPara` (192-202) : format fichier JSON versionné implicitement (pas de champ version — ⚠️ une évolution future du format cassera la lecture des vieux fichiers ; `ReaderViewModel` attrape l'exception et retombe sur DB/réseau, donc dégradation acceptable).

### 9.2 `DownloadService.kt` (199 lignes)

- **Rôle** : foreground service `dataSync` qui maintient le processus vivant + notification de progression ; s'auto-arrête.
- `start()` (47-55) : `startForegroundService` sur O+. ✅
- `onStartCommand` (67-79) : deux actions (UPDATE → monitoring, CANCEL_ALL → `downloadManager.cancelAll()`), `START_NOT_STICKY` — ✅ (pas de redémarrage intempestif).
- `startDownloadMonitoring()` (86-160) :
  - Collecte la file et met à jour la notification à chaque émission. ⚠️ Une notification `notify()` **par changement d'état de file** (plusieurs par seconde pendant un téléchargement) — Android limite le taux de rafraîchissement, mais un `debounce(500ms)`/`sample()` serait plus propre.
  - Condition d'arrêt (l. 109) : `total == 0 || (active.isEmpty() && total == cancelled)` — ✅ gère « tout annulé ».
  - Fin de batch (l. 118-143) : notification finale de synthèse, `delay(2000)` puis `stopSelf()`. ✅ UX soignée.
  - ⚠️ `notificationJob?.cancel()` **depuis l'intérieur du `collect`** (l. 114, 141) : s'annuler soi-même fonctionne (le `return@collect` suit), mais c'est fragile — mieux : `collectLatest` + exception de fin, ou sortir du flow avec un booléen.
  - ⚠️ Les compteurs incluent les items COMPLETED **de sessions précédentes** (la file n'est jamais purgée) → « 152/153 » alors qu'on n'a demandé que 3 chapitres. Cosmétique.
- `buildNotification()` (171-198) : action « Annuler » via `PendingIntent.getService(FLAG_IMMUTABLE)` ✅, icônes système `android.R.drawable.*` (⚠️ look non Material, mais fiable).
- ⚠️ Sans `POST_NOTIFICATIONS` runtime (Android 13+), la notification n'apparaît pas (§2.6).

### 9.3 `ChapterFileManager.kt`

- 🔴 **Fichier vide (0 octet)** — reste d'un refactoring (la logique a fusionné dans `StorageManager`). **À supprimer** : un fichier Kotlin vide fait échouer certains outils d'analyse et prête à confusion.

## 10. `data/extension`

### 10.1 `ExtensionInfo.kt` (17 lignes)
- Simple data class d'affichage (nom, url, langue, version, états). ✅

### 10.2 `ExtensionManager.kt` (92 lignes)

- Registre en mémoire de `NovelSource` + ensemble d'IDs activés, en `StateFlow`. `registerSource()` remplace ou ajoute (l. 253-261) et active par défaut. ✅
- 🔴 **Le toggle est sans effet réel** : `toggleSource()` met à jour `_enabledSourceIds`, mais (1) `ExtensionsViewModel` ne recalcule `extensions` que sur émission de `_sources` (qui ne change pas au toggle) → **l'UI ne reflète jamais le changement** ; (2) `NovelRepository` utilise la source injectée **quel que soit** l'état activé → désactiver une extension ne désactive rien. Fonctionnalité cosmétique.
- ⚠️ Code mort : `uninstallSource()`, `getSourceByName()`, `enabledSources` inutilisés.
- ⚠️ État non persisté : les sources désactivées redeviennent actives au redémarrage.

## 11. `data/storage`

### 11.1 `StorageManager.kt` (264 lignes)

- **Rôle** : abstraction double mode **interne** (`java.io.File` sous `filesDir/NovelReader/novels`) / **SAF** (`DocumentFile` sous un arbre choisi par l'utilisateur). Structure `{base}/{slug_sanitized}/{N}.json`.
- `autoCreateStorageLocation()` (39-52) : priorité SAF existant, sinon crée l'interne et le persiste. ✅ idempotent.
- `withStorage()` / `withNovelStorage()` (90-120) : **dispatch générique SAF vs File** — ✅ très bonne factorisation, évite la duplication dans les 10 opérations.
- ⚠️ `getBaseDir()` (81-86) : `findFile("novels") ?: createDirectory("novels")` — si « novels » existe comme **fichier**, `findFile` le retourne et les opérations suivantes échouent silencieusement (retour null). Cas marginal.
- 🔴 **BUG SAF potentiel** dans `saveChapterFile()` (124-142) : `dir.createFile("application/json", fileName.substringBeforeLast("."))` — selon le fournisseur de documents (external storage, SD, cloud), le fichier créé peut s'appeler `42` **ou** `42.json` (certains providers ajoutent l'extension d'après le MIME). Or `loadChapterFile`/`isChapterDownloaded` cherchent **exactement** `42.json` → risque d'écrire des fichiers introuvables ensuite. Correctif : après `createFile`, utiliser `newFile.name` réel, ou renommer via `renameTo("42.json")`.
- `loadChapterFile` (144-156) : SAF via `contentResolver.openInputStream`, interne via `readText`. ✅
- `deleteNovelFiles()` (188-191) : ⚠️ asymétrie — SAF `delete()` sur un dossier DocumentFile supprime récursivement (OK), interne `deleteRecursively(); true` retourne `true` même si rien n'existait (cosmétique).
- `countDownloadedChapters` / `getStorageSizeBytes` (195-210, 247-259) : parcours récursif des deux mondes. ⚠️ SAF `listFiles()` est **lent** (requêtes ContentResolver par nœud) — appelé dans Settings, acceptable.
- `sanitizeFolderName()` (261-263) : remplace les caractères interdits, tronque à 200 — ✅ (évite les noms illégaux sur FAT/exFAT).

### 11.2 `MigrationManager.kt`

- 🔴 **Fichier vide (0 octet)** — prévu pour migrer les fichiers interne↔SAF, jamais implémenté. **À supprimer** ou à implémenter : changer de dossier dans Settings **ne migre pas** les fichiers existants (ils restent dans l'ancien emplacement et deviennent invisibles pour l'app).

## 12. `data/update`

### 12.1 `AppUpdateChecker.kt` (111 lignes)

- Interroge l'API GitHub `releases/latest` du **bon** dépôt (`elfred434/novel-readers` — ✅ contrairement au README), parse tag/assets, compare sémantiquement `x.y.z` par segments numériques (`compareVersions`, l. 76-87 — gère des longueurs différentes via `getOrElse(0)`. ✅ simple et correct).
- Retourne `null` sur toute erreur (réseau, 404 « aucune release », parse) — ✅ silencieux pour un check de fond.
- ⚠️ Son propre `OkHttpClient` (timeouts 10 s) au lieu de réutiliser le singleton Hilt (avec le UA) — certains réseaux filtrent les requêtes sans UA ; GitHub tolère, mais l'UA « AppUpdateChecker » par défaut OkHttp est moins propre.
- ⚠️ Sans authentification, limite API GitHub = 60 requêtes/h/IP — OK pour cet usage.
- 🔴 Dépend de la CI : tant que le keystore est régénéré à chaque release (§2.7), la mise à jour téléchargée **ne s'installera jamais** (signature différente).

### 12.2 `AppUpdateInstaller.kt` (110 lignes)

- Utilise le **DownloadManager système** (bon choix : reprise, notification native) vers `getExternalFilesDir(DIRECTORY_DOWNLOADS)` (pas de permission de stockage requise ✅), nettoie les vieux APK `NovelReader-*.apk` avant (l. 150-152 ✅).
- Écoute `ACTION_DOWNLOAD_COMPLETE` via receiver enregistré au runtime avec `RECEIVER_EXPORTED` (l. 179-180) — ⚠️ `EXPORTED` n'est pas nécessaire pour un broadcast système protégé ; `RECEIVER_NOT_EXPORTED` serait plus sûr (un broadcast forgé par une app tierce avec le bon `downloadId` pourrait déclencher `installApk` — faisable, impact faible car le fichier installé reste celui téléchargé… mais l'intent d'install serait relancé).
- `installApk()` (187-211) : FileProvider sur N+ ✅, `FLAG_GRANT_READ_URI_PERMISSION` ✅ — mais 🔴 **bloqué par l'absence de `REQUEST_INSTALL_PACKAGES`** (§2.6).
- ⚠️ **Fuite de receiver** : si l'utilisateur quitte l'écran pendant le téléchargement, le receiver reste enregistré jusqu'à `cancel()` (jamais appelé automatiquement) ou à la fin du téléchargement. Un `onCleared` côté ViewModel ou un receiver dans le manifest serait plus robuste.
- ⚠️ Si l'app est tuée avant la fin du téléchargement, l'APK reste orphelin (nettoyé au prochain `downloadAndInstall` — couvert).

## 13. `data/worker/UpdateWorker.kt` (137 lignes)

- `@HiltWorker` + `@AssistedInject` ✅ (d'où la config WorkManager custom dans l'App).
- `schedule()` (42-48) : périodique, contrainte réseau CONNECTED, `ExistingPeriodicWorkPolicy.UPDATE` — ✅ bien choisi (met à jour l'intervalle si la requête change… mais l'intervalle est **codé en dur à 12 h** depuis `NovelReaderApp`, cf. §3.2).
- `doWork()` (67-86) : boucle séquentielle sur la bibliothèque, isole les échecs par novel, `Result.retry()` si **tout** a échoué (l. 81 — heuristique raisonnable : probablement hors-ligne malgré la contrainte). ✅
- 🔴 **BUG — `updateUnreadCount(slug, newChapters.size)`** (l. 99) : écrase le compteur avec le nombre de chapitres **nouvellement détectés**, pas le nombre réel de non-lus. Ex. : 30 chapitres non lus + 2 nouveaux → compteur affiché = 2. Devrait être `chapterDao.getUnreadCount(slug)` (qui existe, inutilisé !).
- 🔴 Aggrave le bug §8.3 : `cacheChapters(slug, remoteChapters, title)` réécrit **tous** les chapitres en REPLACE → historique de lecture effacé toutes les 12 h pour chaque novel ayant un nouveau chapitre.
- `SingleNovelUpdateWorker` (110-137) : variante unitaire parallélisable + `scheduleNovelUpdate()` — ⚠️ **jamais appelée** (code mort, mais prête pour un « rafraîchir » manuel).
- ⚠️ Aucune notification « nouveau chapitre » n'est postée malgré le canal dédié (§3.2) et la préférence `notificationsEnabled` (jamais lue) — la promesse README « Notifications nouveaux chapitres » n'est pas implémentée.

## 14. `ui/theme` — Design system « Studio Noir »

### 14.1 `Color.kt` (87 lignes)

- Palette documentée et disciplinée : 5 niveaux de gris dark, un accent unique rubis `#CC3344`, déclinaisons light/AMOLED, couleurs sémantiques (Error/Success/Warning), couleurs lecteur dédiées (`ReaderBackground #131313`…). ✅ excellent niveau de soin, commentaires d'intention.
- ⚠️ `StatusHiatus`/`StatusCancelled` définies mais inutilisables (l'enum `NovelStatus` ne les produit pas — §4.1).
- ⚠️ `enum AppTheme` porte `isDark` en dur (`SYSTEM` marqué `true`) — heureusement seul `displayName` est exploité dans l'UI ; le champ est trompeur.

### 14.2 `Theme.kt` (101 lignes)

- Trois `ColorScheme` complets (dark, light, AMOLED) mappés proprement sur Material 3. ✅
- `NovelReaderTheme()` (153-188) : résolution `SYSTEM → isSystemInDarkTheme()`, `SideEffect` peint la status bar de la couleur de fond + icônes claires/sombres selon le thème, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`. ✅
- ⚠️ Ne touche pas `navigationBarColor` (reste système) — avec `enableEdgeToEdge()` c'est acceptable (barre de geste transparente).

### 14.3 `Type.kt` (109 lignes) et `Shape.kt` (10 lignes)

- Typographie à 4 niveaux commentée, letter-spacing négatif sur les display/headline (rendu éditorial soigné). ✅
- Shapes 6→28 dp cohérents avec l'usage dans les écrans. ✅

## 15. `ui/navigation`

### 15.1 `Screen.kt` (88 lignes)

- `sealed class Screen` avec route/titre/icône ; 4 tabs bottom nav + 6 écrans secondaires. ✅
- `Reader.createRoute()` URL-encode l'URL du chapitre (l. 58-61) — nécessaire car elle contient `/`. ⚠️ `%2F` dans un segment de path : fonctionne avec Navigation 2.8 (décodage post-matching), mais reste un point fragile historique ; un passage par `SavedStateHandle` ou un ID numérique serait plus robuste.
- ⚠️ **`Screen.Search` est défini mais jamais enregistré dans le NavGraph** → route morte (la recherche vit dans Browse).

### 15.2 `NavGraph.kt` (108 lignes)

- Bottom bar conditionnée aux 4 routes principales (l. 130-131) ✅ ; navigation tab avec `popUpTo(startDestination) + saveState/restoreState + launchSingleTop` — ✅ pattern canonique de conservation d'état des onglets.
- Indicateur de sélection personnalisé (alpha 0.12 du primary) — cohérent avec le design.
- Transitions fade 250 ms globales. ✅
- ⚠️ Les arguments sont typés (`NavType.StringType`) mais jamais validés non-vides : un slug vide ouvre `DetailScreen` qui affichera « Impossible de charger » (géré dans le VM — OK).
- ⚠️ Pas de deep links (non requis ici).

## 16. `ui/components`

### 16.1 `LoadingError.kt` (153 lignes)

- `LoadingIndicator`, `ErrorView` (icône « ! » cerclée + bouton Réessayer), `EmptyView` (« ~ »), `ShimmerPlaceholder` (dégradé animé infini). ✅ cohérents et réutilisés partout.
- ⚠️ `ShimmerPlaceholder` est **inutilisé** (aucun écran ne l'affiche — dommage, les écrans utilisent le spinner).
- ⚠️ Icônes « ! » et « ~ » en texte brut : cheap mais assumé.

### 16.2 `SearchBar.kt` (94 lignes)

- `BasicTextField` custom avec placeholder par `decorationBox`, bouton clear animé (`scaleIn + fadeIn`). ✅ propre ; évite la lourdeur du `SearchBar` M3.
- ⚠️ Pas de gestion IME (`imeAction = Search`, fermeture clavier) — la recherche est live donc acceptable.

### 16.3 `StyledComponents.kt` (141 lignes)

- `StatusBadge` (point + label), `RatingBadge` (★ %.1f), `SectionLabel` (titre + compteur chip), `SubtleDivider`, `StatusChip`. ✅ facteurs de forme réutilisés.
- ⚠️ `StatusBadge` retourne **vide** pour UNKNOWN alors que `StatusChip` affiche « ? » — incohérence mineure entre les deux variantes.
- ⚠️ `"%.1f".format(rating)` dépend de la locale système (virgule en FR : « 4,5 ») — en fait c'est **souhaitable** en français. OK.

### 16.4 `NovelGridItem.kt` (212 lignes)

- Carte 2:3 avec `AsyncImage` (Coil), fallback initiales sur fond noir, badges superposés : note+votes (bas-gauche), vues (bas-droite, **seulement si pas de note** — l. 92 : choix anti-surcharge ✅), téléchargé (haut-droite), non-lus (haut-gauche, « 99+ » max).
- **3 surcharges** : paramètres explicites / `NovelEntity` / `Novel` (domaine) — ✅ bonne DX, évite les conversions dans les écrans.
- ⚠️ `combinedClickable` sans indication visuelle du long-press (pas de ripple custom — le ripple par défaut fonctionne).
- `formatViews()` (208-212) : division entière → 1 999 999 vues = « 1M ». Cosmétique.

## 17. `ui/screens`

### 17.1 Bibliothèque

**`LibraryViewModel.kt` (163 lignes)**
- Collecte 3 flows (novels, catégories, historique) dans `init` ✅ ; `applyFilter()` refiltrant à chaque émission.
- ⚠️ **Fuite logique** : quand une catégorie est sélectionnée, la liste vient de `getNovelsInCategoryOnce` (one-shot) — mais `applyFilter()` est rappelé à chaque émission des novels/catégories donc ça reste frais en pratique. ⚠️ Race possible : deux `applyFilter()` concurrents (depuis 2 collectors) peuvent écrire dans le désordre — impact visuel transitoire seulement.
- ⚠️ Injecte `CategoryDao` **directement** (bypass repository) — incohérent avec l'architecture affichée dans CONTRIBUTING (« Repository = seule source de vérité »).
- 🔴 `confirmRemoveFromLibrary()` (99-104) ne supprime **pas les fichiers téléchargés** (contrairement à `DetailViewModel.toggleLibrary`) → fuite de stockage (§8.2).
- Dialogues (création catégorie, transfert, suppression) pilotés par l'état UI ✅.

**`LibraryScreen.kt` (553 lignes)**
- TopAppBar avec titre contextuel (catégorie) + compteur, bascule grille/liste, reset filtre, accès paramètres. ✅
- « Continuer la lecture » = premier item de l'historique (l. 103-114) — ⚠️ cliquer ouvre le **détail du novel**, pas le chapitre lui-même (demi-fonctionnalité : l'URL du chapitre est disponible dans `ChapterEntity`).
- Grille 2 colonnes via `chunked(2)` dans une `LazyColumn` (l. 164) : clés `row_${pair.first().slug}` — ⚠️ quand la liste change, les clés de lignes bougent (recomposition complète, perte d'état des items) ; `LazyVerticalGrid` comme dans Browse serait plus idiomatique. Nombre de colonnes **fixe** (2) quelle que soit la largeur — tablette non optimisée.
- Menu contextuel long-press (transférer / retirer) ancré par item — ✅ ; 3 dialogues Material cohérents.
- `LibraryListItem` : duplique ~80 % de `NovelGridItem` en version ligne — acceptable (composant privé).

### 17.2 Découverte

**`BrowseViewModel.kt` (198 lignes)**
- Pagination infinie (`loadMore`, garde `isLoadingMore/hasMore/searchQuery`), tri et filtres via l'API (`sort`, `status`, `genre`). ✅
- Genres extraits **des résultats courants** (l. 241-245) : chips limitées aux genres de la page 1 — approximation assumée (« P3 »).
- 🔴 **BUG MAJEUR — `performSearch()` (317-351) n'utilise pas l'API de recherche** : `repository.searchNovels()` existe (et l'API `/api/novels?search=` est **testée et fonctionnelle**, cf. test §19), mais le code **télécharge jusqu'à 50 pages de catalogue** et filtre côté client titre/auteur. Coût : jusqu'à **50 requêtes HTTP séquentielles** par recherche (~25 s), charge inutile sur le site, résultats plafonnés à 50.
- 🔴 **Pas de debounce ni d'annulation** : chaque caractère tapé relance `performSearch()` ; les coroutines précédentes **continuent** et peuvent écraser les résultats d'une frappe plus récente (race). Correctif : `searchQuery.debounce(300).collectLatest { … }` ou annuler le `Job` précédent.
- `displayedNovels` / `isShowingSearchResults` exposés en getters (l. 353-360) — ⚠️ non réactifs hors recomposition pilotée par `uiState` (fonctionne car l'écran lit `uiState` d'abord, mais fragile).

**`BrowseScreen.kt` (199 lignes)**
- Détection fin de liste via `derivedStateOf` sur le `LazyGridState` (l. 39-45, seuil -6) + `LaunchedEffect` — ✅ pattern standard sans clignotement.
- Grille **adaptative** `GridCells.Adaptive(150.dp)` ✅ (contraste avec la bibliothèque à 2 colonnes fixes).
- Chips de filtre horizontales scrollables, séparateur visuel, état « genre sélectionné » repliable. ✅ UX soignée.
- États Loading/Error/Empty/searching correctement exclusifs (l. 143-177).

### 17.3 Détail

**`DetailViewModel.kt` (227 lignes)**
- `slug` depuis `SavedStateHandle` ✅. Observe la file de téléchargement en ne rafraîchissant `downloadedChapters` (scan disque !) **qu'à la transition COMPLETED** (l. 66-81) — ✅ optimisation intelligente (évite un `listFiles()` à chaque émission).
- `loadNovelDetails()` (86-123) : chaîne réseau → **fallback hors-ligne** depuis Room avec `isOffline = true`. ✅ robuste.
  - 🔴 Ligne 108 : `chapterCount = localNovel.unreadChapterCount` — le **compteur de non-lus est affiché comme nombre total de chapitres** en mode hors-ligne. Devrait être `localChapters.size`.
- `toggleLibrary()` (125-138) : ajout → `cacheChapters` (déclenche le bug §8.3 si des chapitres existaient déjà… en fait à l'ajout la table est vide pour ce novel, sauf ré-ajout après retrait → perte acceptable) ; retrait → supprime **aussi les fichiers** (✅ contrairement à LibraryViewModel).
- Sélection multiple (179-226) : entrée par long-press sur un chapitre **téléchargé** (l. 248 de l'écran), sortie auto quand la sélection devient vide (l. 197-201 ✅), suppression groupée fichiers + DB. ✅ complet.
- ⚠️ `markChapterAsUnread` exposé par chapitre mais il n'existe pas de « marquer lu » symétrique dans l'écran (le Reader marque lu automatiquement).
- ⚠️ `previousCompleted` (var d'instance) n'est pas protégé en cas de recréation du VM — sans conséquence (état dérivable du disque).

**`DetailScreen.kt` (350 lignes)**
- Header riche : cover floutée en fond (cover + scrim 0.85), cover nette 120dp, titre/auteur/traducteur, chips statut/note/type/année/rang, compteurs, genres+tags fusionnés (dédoublonnés, max 4 + « +N »), 3 boutons (Suivre / Télécharger tout / Lire ch.1). ✅ très complet.
- ⚠️ Le bouton « Lire » (l. 59-66) n'apparaît que si `firstChapterSlug != null` mais utilise `chapters.first().url` — incohérence bénigne (le slug de l'API n'est pas utilisé).
- `ChapterCard` : numéro coloré si téléchargé, badge « Lu » sur `lastReadChapterNumber`, temps de lecture (mots/200 wpm), actions contextuelles (télécharger/supprimer/marquer non lu), mode sélection avec checkbox et TopAppBar dédiée. ✅
- ⚠️ « Lu » ne reflète que le **dernier** chapitre lu ; les autres chapitres lus n'ont pas d'indicateur (l'info existe : `ChapterEntity.isRead`, mais l'écran travaille sur `ChapterPreview` réseau, pas sur les entités locales — l'état lu n'est **jamais affiché** pour la liste complète).
- Dialogues de suppression simple et groupée (avec aperçu des 5 premiers numéros). ✅

### 17.4 Lecteur

**`ReaderViewModel.kt` (180 lignes)**
- Décodage de l'URL depuis `SavedStateHandle` (l. 55-56) ✅.
- `loadChapter()` (67-119) : **cascade à 3 niveaux** — fichier JSON disque → cache DB → réseau — avec marquage « lu » et préchargement du suivant à chaque étape. ✅ excellente conception hors-ligne.
- `prefetchNextOnWifi()` (125-155) : précharge le chapitre suivant en WiFi s'il n'est ni sur disque ni en DB ; échecs silencieux (bonus). ✅ — ⚠️ mais il écrit en DB via `downloadChapter` **sans** écrire le fichier : incohérence mineure (le « téléchargé » n'apparaîtra pas dans l'écran Téléchargements, c'est voulu : cache ≠ téléchargement, mais `isDownloaded=true` est posé en DB sans fichier correspondant → le Reader le lira depuis la DB, OK).
- 🔴 **BUG MAJEUR — réglages non persistés** : `updateFontSize/Font/Theme/LineHeight/Padding/Pagination` (165-170) ne modifient que le `StateFlow` ; `PreferencesManager` a toutes les clés prêtes (§5.5) mais n'est **ni injecté ni utilisé** → **tous les réglages du lecteur sont perdus à chaque ouverture**.
- 🔴 **BUG — position de scroll jamais restaurée** : `persistScrollPosition()` écrit en DB (173-176), mais **rien ne relit** `ChapterEntity.scrollPosition` au chargement pour scroller. Écriture sans lecture → fonctionnalité morte (et aggravée par §8.3 qui la réinitialise à 0).
- ⚠️ `markRead()` marque lu **dès le chargement du chapitre**, même si l'utilisateur ne fait qu'y passer — comportement type Mihon, assumé.
- Regex d'extraction dupliquées du parser (§7.3) — même fragilité sur les slugs non numériques.

**`ReaderSettings.kt` (30 lignes)**
- Réglages + enums police/thème + `ReaderColors.fromTheme()` (dark #1A1B1E / light / sépia). ⚠️ Les couleurs du lecteur **dupliquent** celles de `Color.kt` (`ReaderBackground #131313` vs `0xFF1A1B1E` ici — deux « dark » différents !). Incohérence de design system.

**`ReaderScreen.kt` (256 lignes)**
- `LazyColumn` de paragraphes rendus par `AndroidView(TextView + Html.fromHtml)` (l. 195-206) — ⚠️ un `TextView` Android par paragraphe : lourd en mémoire sur les gros chapitres (60+ vues recyclées par la LazyColumn, acceptable), mais permet la sélection de texte et les span HTML. `FROM_HTML_MODE_COMPACT` ✅. Le bloc `update` ré-applique couleur/taille/interligne à chaque recomposition — ✅ pour les réglages live.
- Zones de tap : 25 % gauche/droite = chapitre préc./suiv., centre = toggle contrôles (l. 108-117) — ✅ type Mihon ; ⚠️ aucun indicateur visuel de ces zones.
- Sauvegarde du scroll encodée `index*10000+offset` à chaque défilement (l. 80-83) — ⚠️ écrit dans le StateFlow à chaque pixel (recomposition du VM seulement, pas d'I/O — OK), mais ⚠️ `handleBack` lance `persistScrollPosition()` **en parallèle** de `onBack()` (l. 89-92) : le scope est lié à la composition détruite au retour → **la coroutine peut être tuée avant l'écriture DB** → perte possible de la position (doublon avec le bug « jamais restaurée »).
- `controlsVisible` en `mutableIntStateOf(1)` utilisé comme booléen (0/1) — ⚠️ style.
- Bottom sheet de réglages complète (3 sliders avec pas, polices, thèmes, pagination) — ⚠️ **le toggle « Pagination » n'a aucun effet** sur le rendu (aucun mode paginé implémenté) ; `fontFamily` n'est pas appliqué au titre (seuls les paragraphes via TextView).
- Fin de chapitre : « — Fin — » + boutons Précédent/Suivant ✅.

### 17.5 Mises à jour & Historique

**`UpdatesViewModel.kt` (51 l.) / `UpdatesScreen.kt` (144 l.)** — simple chargement de `/latest` page 1, états loading/error/empty, items cliquables vers le détail. ⚠️ Pas de pagination ni de pull-to-refresh (bouton retry seulement). ⚠️ Clé `${novelSlug}_${chapterNumber}` : si /latest liste 2 nouveaux chapitres du même novel… OK ils ont des numéros différents. Si le site renvoie des doublons exacts → crash de clé dupliquée (peu probable).

**`HistoryViewModel.kt` (31 l.) / `HistoryScreen.kt` (133 l.)** — Flow des 30 derniers chapitres lus, affichage simple. ⚠️ Cliquer ouvre le détail du novel plutôt que de **reprendre la lecture** au chapitre (l'URL est dans l'entité — demi-fonctionnalité, même remarque que « Continuer la lecture »). ⚠️ Victime du bug §8.3 : l'historique disparaît au prochain refresh de chapitres.

### 17.6 Téléchargements

**`DownloadsViewModel.kt` (93 lignes)**
- Combine la file en mémoire + scan du disque (`scanDownloadedFiles`). ⚠️ Le titre du novel est **reconstruit depuis le slug** (`omniscient-readers-viewpoint` → « Omniscient Readers Viewpoint », l. 203) au lieu de lire les métadonnées des JSON ou de la DB — hack affichage.
- ⚠️ Le scan disque n'est fait qu'à l'init et via `refreshFiles()` (jamais appelé depuis l'écran — pas de pull-to-refresh) ; les nouveaux téléchargements terminés n'apparaissent dans la section « sauvegardés » qu'après ré-ouverture de l'écran.

**`DownloadsScreen.kt` (287 lignes)**
- Deux sections (file active / fichiers sauvegardés) avec compteurs, icônes d'état, actions retry/cancel. ✅ clair.
- ⚠️ `cancel()` sur un DOWNLOADING est inefficace (bug §9.1) : l'utilisateur croit annuler, le chapitre arrive quand même.
- ⚠️ Aucune action sur les fichiers sauvegardés (pas de suppression depuis cet écran — seulement depuis Détail).

### 17.7 Extensions

**`ExtensionsViewModel.kt` (35 l.) / `ExtensionsScreen.kt` (116 l.)** — liste des sources avec switch. 🔴 **Switch sans effet** (§10.2 : UI non rafraîchie + repository ignore l'état). Texte de pied de page honnête (« sources compilées dans l'APK »).

### 17.8 Paramètres

**`SettingsViewModel.kt` (196 lignes)**
- Collecte exhaustive des préférences vers l'état UI ✅ ; propage `wifiHighDataMode` et `userMaxConcurrent` au `DownloadManager` en live (l. 72-75, 168-176). ✅
- ⚠️ `setDownloadMaxConcurrent` : variable `onWifi` lue puis **inutilisée** (l. 173) — code mort.
- ⚠️ `downloadUpdate()` (123-142) **re-appelle** `checkForUpdate()` au lieu de réutiliser l'URL déjà obtenue → double requête réseau + risque de version différente entre l'affichage et le téléchargement.
- ⚠️ `AppUpdateInstaller(app)` créé à la volée et **jamais annulé** si l'utilisateur quitte (fuite receiver §12.2).
- 🔴 Les réglages `updateIntervalHours`, `downloadOnWifiOnly`, `notificationsEnabled` sont **persistés mais jamais appliqués** (§3.2, §9.1, §13) — l'écran donne une illusion de contrôle.

**`SettingsScreen.kt` (333 lignes)**
- Sections en cartes (`SectionCard`) : thème (4 boutons), file d'attente (avec retry des échecs), extensions, **intervalle de vérification (slider 4-48 h)**, notifications, **stockage SAF** (`OpenDocumentTree` + `takePersistableUriPermission` l. 78-84 ✅ — lecture+écriture persistées correctement), simultané (slider 1-5), mode haute vitesse (avec état réseau en direct ✅), cache (vidage), **mise à jour de l'app** (check → changelog markdown épuré → téléchargement). ✅ écran très complet et bien organisé.
- ⚠️ Import dupliqué `ArrowBack` (l. 24-25) — warning de compilation mineur.
- ⚠️ Le changelog est strippé des `#` markdown par regex — cosmétique OK.
- ⚠️ Aucune gestion du retour arrière pendant le téléchargement de l'APK.

### 17.9 Onboarding

- 🔴 `OnboardingScreen.kt` et `OnboardingViewModel.kt` sont **vides (0 octet)** → fonctionnalité abandonnée en cours de route. À supprimer ou implémenter (le NavGraph ne les référence pas, heureusement).

## 18. Ressources

| Fichier | Analyse |
|---|---|
| `values/strings.xml` | Une seule string (`app_name`) : **tous les textes UI sont en dur dans le code Kotlin** (français). ⚠️ Pas d'i18n possible, pas de revue de traduction. Pour une app 100 % FR assumée, acceptable mais non scalable. |
| `values/themes.xml` | 🔴 `Theme.Material.Light.NoActionBar` — thème **clair** au démarrage pour une app « Studio Noir » → **flash blanc** au lancement avant Compose. Utiliser un thème sombre (ou `Theme.SplashScreen`). |
| `xml/file_paths.xml` | `external-files-path Download/` — ✅ correspond exactement à l'usage d'`AppUpdateInstaller`. |
| `drawable/ic_launcher_*.xml` + mipmaps | Icône adaptative vectorielle simple (livre rubis sur fond quasi-noir). ✅ cohérent avec la marque ; ⚠️ les mipmaps density-specific sont des **XML pointant vers les mêmes drawables** (pas de PNG) — inoffensif, juste inhabituel. |

## 19. Tests — `NovelFranceParserTest.kt` (189 lignes)

- 6 tests couvrant : contenu de chapitre, liste de chapitres, /latest, browse API, recherche API, détail API.
- 🔴 **Ce sont des tests d'intégration réseau réels** (le commentaire l'assume) : ils **échouent hors-ligne**, dépendent du contenu vivant du site (le test `get novel detail via API` assert `"Sing-shong"` en dur — casse si la fiche change), et ne peuvent pas tourner en CI (qui d'ailleurs ne lance pas `test`, §2.7).
- ⚠️ `assertEquals` utilisé sans import explicite (hérité de `junit.framework` via Kotlin ? Non — en fait l'import manque : `org.junit.Assert.assertEquals` n'est pas importé, le test **ne compile peut-être pas** — à vérifier ; Kotlin ne l'importe pas automatiquement).
- ✅ Leur valeur : ils prouvent que l'API de recherche fonctionne → rend le bug §17.2 (recherche côté client) d'autant plus incompréhensible.
- Recommandation : convertir en tests unitaires avec **fixtures HTML/JSON** enregistrées (le commentaire l'envisage déjà).

---

## 20. Synthèse transversale

### 20.1 🔴 Bugs critiques (casse fonctionnelle majeure)

| # | Fichier | Bug | Impact |
|---|---|---|---|
| C1 | `app/proguard-rules.pro` (§2.5) | DTO `kotlinx.serialization` hors `data.model`/`data.local.entity` non protégés contre R8 | **Parsing API/cache/releases cassé en build release minifiée** |
| C2 | `.github/workflows/build.yml` (§2.7) | Keystore **régénéré à chaque run CI** + secrets en clair | Chaque release a une signature différente → **mises à jour impossibles** ; clé reproductible par quiconque → risque d'usurpation |
| C3 | `NovelRepository.cacheChapters()` (§8.3) | REPLACE d'entités neuves (`isRead=false`, `scrollPosition=0`) + cascade sur `chapter_content` | **Historique de lecture, positions de scroll et cache contenu effacés** à chaque rafraîchissement de chapitres (worker 12 h inclus) |
| C4 | `AndroidManifest.xml` (§2.6) | Permission `REQUEST_INSTALL_PACKAGES` absente | L'auto-update ne peut pas s'installer (même avec une signature valide) |
| C5 | `BrowseViewModel.performSearch()` (§17.2) | Recherche = 50 pages de catalogue filtrées côté client au lieu de `searchNovels()` ; pas de debounce/annulation | Recherches lentes (~25 s), 50× la charge réseau, races sur les résultats |

### 20.2 🟠 Bugs majeurs (fonctionnalité silencieusement inopérante)

| # | Fichier | Bug |
|---|---|---|
| M1 | `ReaderViewModel` (§17.4) | Réglages lecteur jamais persistés malgré les clés DataStore existantes |
| M2 | `ReaderViewModel`/`ReaderScreen` (§17.4) | Position de scroll écrite mais **jamais restaurée** (+ écriture course avec la destruction de l'écran) |
| M3 | `NovelReaderApp.scheduleUpdates()` (§3.2) | Intervalle du worker en dur à 12 h ; la préférence `updateIntervalHours` est ignorée |
| M4 | `DownloadManager` (§9.1) | `downloadOnWifiOnly` jamais appliqué ; `cancel()` n'interrompt pas un téléchargement en cours (pas de Job conservé, statut écrasé par COMPLETED) ; race sur `activeCount` |
| M5 | `UpdateWorker` (§13) | `unreadChapterCount` = nb de **nouveaux** chapitres au lieu de `getUnreadCount()` ; aucune notification malgré canal + préférence |
| M6 | `ExtensionManager`/`ExtensionsViewModel` (§10.2) | Le switch d'activation ne met pas à jour l'UI et n'a aucun effet fonctionnel |
| M7 | `LibraryViewModel.confirmRemoveFromLibrary()` (§17.1) | Retirer un novel de la bibliothèque **laisse les fichiers JSON** sur le disque |
| M8 | `StorageManager.saveChapterFile()` SAF (§11.1) | Nom de fichier créé incertain (`42` vs `42.json`) selon le provider → fichiers potentiellement introuvables à la relecture |
| M9 | `DetailViewModel` (§17.3) | Hors-ligne : `chapterCount = unreadChapterCount` (mauvais champ) ; l'état « lu » de chaque chapitre n'est pas affiché dans la liste |
| M10 | `themes.xml` (§18) | Thème de démarrage **clair** pour une app sombre → flash blanc au lancement |

### 20.3 🟡 Code mort / restes de refactoring

- **4 fichiers vides** : `ChapterFileManager.kt`, `MigrationManager.kt`, `OnboardingScreen.kt`, `OnboardingViewModel.kt`.
- `Screen.Search` (route jamais enregistrée) ; `SingleNovelUpdateWorker.scheduleNovelUpdate()` (jamais appelé) ; `ShimmerPlaceholder` (inutilisé) ; `NovelEntity.storageFolderName` (champ jamais écrit/lu) ; 6 préférences lecteur (§5.5) ; `hasAnyStorageSync`/`hasStorageLocationSync` (runBlocking inutilisés) ; `NovelDao.getAllNovelsAlphabetical/updateNovel/deleteNovel` ; `ChapterDao.getUnreadChapters/getUnreadCount` (pourtant utile pour M5 !) ; `ExtensionManager.uninstallSource/getSourceByName` ; variable `onWifi` dans `SettingsViewModel.setDownloadMaxConcurrent` ; toggle « Pagination » sans implémentation ; entrée `compose-compiler` du catalog.
- Import dupliqué dans `SettingsScreen.kt` (l. 24-25).
- `local.properties` **commité** malgré `.gitignore` (chemin SDK personnel Windows) → `git rm --cached`.

### 20.4 🔐 Sécurité — résumé

1. **Signature** : secrets en clair + keystore régénéré (C2) = chaîne de confiance des mises à jour inexistante.
2. **Auto-update** : téléchargement HTTPS depuis GitHub Releases ✅, mais l'intégrité repose entièrement sur la signature (cassée par C2) ; aucune vérification de hash.
3. **Receiver** d'installation enregistré en `RECEIVER_EXPORTED` (préférer `NOT_EXPORTED`).
4. **Scraping** : contenu HTML tiers rendu sans whitelist (risque faible via `Html.fromHtml`, mais à noter) ; User-Agent mobile pour contourner d'éventuels filtres — point légal/ToS à assumer vis-à-vis de novelfrance.fr.
5. `allowBackup=true` non documenté.

### 20.5 ✅ Points forts (à conserver)

- **Architecture MVVM propre** : ViewModels ↔ Repository ↔ Source/Room/Storage, injection Hilt systématique, `StateFlow` partout, séparation domaine/entité/DTO.
- **Résilience hors-ligne remarquable** : cascade fichier → DB → réseau dans le Reader ; fallback local complet dans Détail ; parser à 2 niveaux (JSON RSC + DOM).
- **Qualité d'écriture** : commentaires d'intention en français, décisions documentées (« CORRECTIONS AUDIT », limitations connexes du parser), KDoc sur les entités.
- **Design system discipliné** (Studio Noir) appliqué de façon cohérente sur 10 écrans.
- **Factorisation** : `StorageManager.withStorage` (dispatch SAF/File), 3 surcharges de `NovelGridItem`, composants `LoadingError` partagés.
- **CI/CD** qui produit APK + release automatiquement (hors bugs C2).
- Défenses pragmatiques : garde anti-boucle infinie de pagination, fallback ancien format de cache, `distinctUntilChanged`, timeouts partout.

### 20.6 🛠️ Plan de correction priorisé

| Priorité | Action | Fichiers |
|---|---|---|
| P0 | Ajouter les règles ProGuard pour tous les `@Serializable` de l'app | `proguard-rules.pro` |
| P0 | Persister le keystore en secret CI ; retirer les mots de passe par défaut | `build.yml`, `app/build.gradle.kts` |
| P0 | `cacheChapters` : préserver `isRead/readAt/scrollPosition/isDownloaded` (merge avec l'existant, `IGNORE` + update ciblé) | `NovelRepository.kt` |
| P0 | Ajouter `REQUEST_INSTALL_PACKAGES` ; demander `POST_NOTIFICATIONS` au runtime | `AndroidManifest.xml`, `SettingsScreen.kt` |
| P1 | Recherche : utiliser `repository.searchNovels()` + `debounce(300)` + annulation du job précédent | `BrowseViewModel.kt` |
| P1 | Persister/restaurer réglages lecteur + position de scroll (et lire `scrollPosition` au chargement) | `ReaderViewModel.kt`, `ReaderScreen.kt`, `PreferencesManager.kt` |
| P1 | Brancher les préférences orphelines : intervalle worker (replanifier au changement), WiFi-only (garde dans `processQueue`), notifications (poster depuis `UpdateWorker`) | `NovelReaderApp.kt`, `DownloadManager.kt`, `UpdateWorker.kt`, `SettingsViewModel.kt` |
| P1 | `unreadChapterCount = chapterDao.getUnreadCount(slug)` | `UpdateWorker.kt` |
| P2 | Annulation réelle des téléchargements (Map de Jobs) + sérialiser `processQueue` | `DownloadManager.kt` |
| P2 | Supprimer les fichiers au retrait bibliothèque ; corriger `chapterCount` hors-ligne ; `updateChapter` → `IGNORE`-safe | `LibraryViewModel.kt`, `DetailViewModel.kt` |
| P2 | Vérifier le nom réel du fichier créé en SAF ; corriger le thème de démarrage sombre | `StorageManager.kt`, `themes.xml` |
| P3 | Nettoyer : fichiers vides, routes et DAO morts, `local.properties` du dépôt, liens README/CONTRIBUTING (`tonuser` → `elfred434`), import dupliqué | divers |
| P3 | Extensions : rendre le toggle effectif (recalcul UI + filtrage dans le repository) ou masquer l'écran | `ExtensionManager.kt`, `ExtensionsViewModel.kt` |
| P3 | Tests : fixtures HTML hors-ligne ; importer `assertEquals` ; exécuter `test` en CI | `NovelFranceParserTest.kt`, `build.yml` |

### 20.7 Note globale

| Critère | Appréciation |
|---|---|
| Architecture & structure | ★★★★☆ — MVVM/DI/Flow exemplaires pour un MVP |
| Lisibilité & documentation | ★★★★☆ — commentaires utiles, KDoc, français cohérent |
| Robustesse réseau/parsing | ★★★★☆ — fallbacks pensés ; parser fragile mais assumé |
| Exactitude fonctionnelle | ★★☆☆☆ — plusieurs réglages « décoratifs », bugs C3/M1-M6 qui minent la confiance |
| Préparation release | ★☆☆☆☆ — ProGuard + signature CI bloquants |
| Couverture de tests | ★☆☆☆☆ — intégration réseau seule, non jouée en CI |

**En résumé** : une base de code d'hobby **très au-dessus de la moyenne** en architecture et en soin UI, avec une vraie vision produit (hors-ligne, SAF, turbo WiFi). Mais elle est actuellement **cassée en release** (ProGuard, signature), **perd les données de lecture** au fil des rafraîchissements (`cacheChapters`), et expose plusieurs réglages **sans effet**. Les correctifs P0/P1 sont localisés et peu coûteux — après quoi l'application serait réellement distribuable.
