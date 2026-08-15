# 🔄 Analyse de la fonction « Mise à jour » — NovelReader

> Analyse statique du code source (clone `elfred434/novel-readers`, branche `main`).
> Date : 2026-08-15

---

## 1. Vue d'ensemble : il y a en réalité DEUX mécanismes de « mise à jour »

| | **A. Mise à jour de l'app** (auto-update) | **B. Mise à jour des novels** (nouveaux chapitres) |
|---|---|---|
| But | Remplacer l'APK installé par une version plus récente | Détecter/cacher les nouveaux chapitres des novels suivis |
| Fichiers | `AppUpdateChecker.kt`, `AppUpdateInstaller.kt`, UI dans `Settings` | `UpdateWorker.kt`, `UpdatesViewModel/Screen`, `NovelRepository` |
| Déclenchement | Au lancement (silencieux) + bouton dans les Paramètres | WorkManager périodique (12 h) + onglet « Updates » |
| Ajout récent | ✅ (commits `Feat: mise à jour intégrée…`) | Ancien (pré-existant) |

Ce document analyse **en profondeur le mécanisme A** (objet des derniers commits) et résume B en fin de document.

---

## 2. Architecture du mécanisme A (auto-update de l'app)

```
GitHub Releases API ──► AppUpdateChecker ──► UpdateInfo
                          (version, URL APK, changelog)
                                 │
                                 ▼
NovelReaderApp.onCreate ──► checkAppUpdate()  (log silencieux uniquement)
SettingsViewModel ──────► checkForUpdate()    (état UI)
SettingsScreen ─────────► « Télécharger vX.X.X » ──► downloadUpdate()
                                                          │
                                                          ▼
                              AppUpdateInstaller (DownloadManager SYSTÈME)
                                                          │
                          BroadcastReceiver ACTION_DOWNLOAD_COMPLETE
                                                          │
                                                          ▼
                              Intent ACTION_VIEW (FileProvider) → installeur système
```

---

## 3. Les 4 briques

### 3.1 `AppUpdateChecker` — la vérification (111 lignes)
- **Singleton Hilt**, injecté dans `NovelReaderApp` et `SettingsViewModel`.
- Appelle `https://api.github.com/repos/elfred434/novel-readers/releases/latest` (OkHttp, timeouts 10 s, `Accept: application/vnd.github.v3+json`).
- Parse la réponse avec **kotlinx.serialization** (`ignoreUnknownKeys = true`, `isLenient = true`).
- Compare `tag_name` (sans le `v`) à `BuildConfig.VERSION_NAME` via `compareVersions()` : découpage par `.`, `toIntOrNull() ?: 0`, comparaison segment par segment (équivalent d'un semver simple).
- Si plus récent → cherche un asset `.apk` dans la release → retourne `UpdateInfo(versionName, apkUrl, changelog, publishedAt)`.
- **Toute erreur → `null` silencieux** (simple `Log.w`). Aucune propagation d'échec réseau à l'UI.

### 3.2 `AppUpdateInstaller` — le téléchargement + installation (111 lignes)
- **Pas un singleton** : instancié à la volée dans `SettingsViewModel.downloadUpdate()`.
- Utilise le **`DownloadManager` système Android** (pas le `DownloadManager` de l'app qui sert aux chapitres) — choix assumé dans le commentaire : « garantir la compatibilité et éviter les conflits de signature ».
- **Nettoyage préalable** : supprime les anciens `NovelReader-*.apk` dans `getExternalFilesDir(DIRECTORY_DOWNLOADS)`.
- Enqueue avec : titre « NovelReader », `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`, destination **interne à l'app** (`setDestinationInExternalFilesDir` → pas de permission de stockage requise), réseau data/roaming autorisés.
- Enregistre un **`BroadcastReceiver` dynamique** sur `DownloadManager.ACTION_DOWNLOAD_COMPLETE` (avec `Context.RECEIVER_EXPORTED`), filtre sur `EXTRA_DOWNLOAD_ID`.
- À la fin : `installApk(fileName)` :
  - Android 7+ → `FileProvider.getUriForFile("${packageName}.fileprovider")` (déclaré dans le manifest, `file_paths.xml` = `external-files-path Download/`) ;
  - sinon → `Uri.fromFile`.
  - `Intent.ACTION_VIEW` + `application/vnd.android.package-archive` + `FLAG_ACTIVITY_NEW_TASK | FLAG_GRANT_READ_URI_PERMISSION` → l'installeur système prend le relais.
- `cancel()` : retire le download + désenregistre le receiver.

### 3.3 L'UI — `SettingsScreen` + `SettingsViewModel`
- `SettingsUiState` expose : `updateAvailable: String?`, `updateChangelog`, `isDownloadingUpdate`, `currentVersion`.
- **État machine à 3 états** (fragile, voir §4) :
  - `""` = vérification en cours (« Vérification… ») ;
  - `null` = à jour (« À jour » + bouton « Vérifier les mises à jour ») ;
  - `"1.0.X"` = mise à jour dispo (« v1.0.X disponible » + changelog tronqué + bouton « Télécharger v1.0.X »).
- `checkForUpdate()` appelé **dès l'`init`** du ViewModel (donc à chaque ouverture des Paramètres).
- Changelog affiché : 200 premiers caractères, `#` stripés, 5 lignes max.
- `downloadUpdate()` : **re-vérifie l'API** puis appelle `installer.downloadAndInstall(apkUrl, onComplete)`.
- `NovelReaderApp.onCreate()` fait aussi un `checkForUpdate()` **silencieux** (log seulement) + `scheduleUpdates()` (WorkManager 12 h pour le mécanisme B).

### 3.4 Versionnage & CI — le maillon qui rend tout possible (ou pas)
`app/build.gradle.kts` :
- `versionCode = GITHUB_RUN_NUMBER ?: git rev-list --count HEAD ?: 1`
- `versionName = "1.0.${versionCode}"` ; debug : `versionNameSuffix = "-debug"`, `applicationIdSuffix = ".debug"`.
- `buildConfig = true` (requis pour `BuildConfig.VERSION_NAME` — réactivé par le commit `0b43a73`).

`.github/workflows/build.yml` :
- Build sur chaque push `main` / PR, JDK 21 + Android SDK.
- **Génère un keystore jetable** (`keytool -genkey …` dans le job) avec le mot de passe en dur (`novelreader`).
- `assembleRelease` + `assembleDebug`, version `1.0.${run_number}`.
- Crée une **GitHub Release** `v1.0.$run_number` avec l'APK attaché (softprops/action-gh-release).

---

## 4. 🔴 Problèmes & risques identifiés (classés par gravité)

### 4.1 🔴 CRITIQUE — Signature incohérente en CI : la mise à jour est ininstallable en pratique
Le workflow génère un **keystore aléatoire à chaque build** (`keytool -genkey` dans le job, sans persistance). Conséquence :
- L'APK de la release **v1.0.5** et celui de la **v1.0.6** sont signés par **deux clés différentes**.
- Android refuse d'installer par-dessus une app existante dont la signature diffère → **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**.
- Le commentaire de `AppUpdateChecker` dit utiliser le DownloadManager système pour « résoudre le conflit de signature » — **c'est une fausse solution** : aucun DownloadManager ne peut résoudre un conflit de clé ; seule une **clé de signature stable et persistée** le peut (uploader le keystore en secret GitHub, ou garder une copie entre builds).

### 4.2 🟠 ÉLEVÉ — Pas de gestion de la permission « Installer des applications inconnues »
Le manifest ne déclare **pas `REQUEST_INSTALL_PACKAGES`**. Sur Android 8+ :
- `context.startActivity(intent)` vers l'installeur échoue (SecurityException / ActivityNotFoundException) pour une app qui n'a pas la permission ;
- l'exception est avalée dans le `catch` → **l'utilisateur ne voit que « Téléchargement… » puis rien**, sans message d'erreur ni redirection vers les réglages.

### 4.3 🟠 ÉLEVÉ — Fuite de `BroadcastReceiver` + ré-enregistrement à chaque téléchargement
- Le receiver n'est **jamais désenregistré après la première réception** (`cancel()` n'est appelé nulle part dans le flux nominal).
- `AppUpdateInstaller` est recréé à chaque `downloadUpdate()` → **les anciens receivers restent enregistrés** sur le context de l'Application.
- Si l'app est **tuée par le système** pendant le téléchargement, le receiver dynamique est perdu → l'APK est téléchargé mais **jamais installé** (un receiver déclaré dans le manifest avec l'ID persisté serait robuste).

### 4.4 🟠 ÉLEVÉ — `downloadUpdate()` refait un appel réseau redondant et fragile
`SettingsViewModel.downloadUpdate()` rappelle `updateChecker.checkForUpdate()` pour obtenir l'URL, alors que `checkForUpdate()` avait déjà tourné pour afficher l'état. Si le **2ᵉ appel échoue** (réseau instable), le téléchargement ne démarre pas alors que l'utilisateur a cliqué « Télécharger ». Correctif : stocker l'`UpdateInfo` dans le `SettingsUiState`.

### 4.5 🟠 MOYEN — `registerReceiver(..., Context.RECEIVER_EXPORTED)` non filtré
- `RECEIVER_EXPORTED` est requis pour capter le broadcast système `ACTION_DOWNLOAD_COMPLETE` (Android 13+), mais il expose le receiver à **tout broadcast forgé** par une autre app (l'action n'est pas protégée par signature système).
- Le seul garde-fou est `intent.getLongExtra(EXTRA_DOWNLOAD_ID)` + `file.exists()`. Amélioration : vérifier le statut réel via une requête `DownloadManager.query(downloadId)` et idéalement restreindre l'intent d'installation.

### 4.6 🟡 MOYEN — Comparaison de versions naïve
`compareVersions` ne gère pas les suffixes : `"1.0.5-debug".split(".")` → `["1","0","5-debug"]` → `5-debug.toIntOrNull() = null → 0` → la version debug locale est comparée comme `1.0.0`. Résultat : **la détection d'update ne fonctionne pas en build debug** (seulement en release, où `versionName` est propre). Pas de support des pré-release (`-beta`, `-rc`).

### 4.7 🟡 MOYEN — État UI à base de sentinelles `String`
`updateAvailable` encode 3 états dans une String (`""` / `null` / version). Fragile et peu lisible ; un `sealed class`/`enum UpdateState` serait plus sûr.

### 4.8 🟡 FAIBLE — Pas de message d'erreur utilisateur
Toutes les erreurs du checker → `null` → l'UI affiche « À jour » même en cas d'échec réseau. Un utilisateur hors-ligne voit « À jour » (faux). Pas de distinction « erreur réseau » vs « pas de mise à jour ».

### 4.9 🟡 FAIBLE — Rate limiting GitHub (60 req/h/IP) sans cache
Chaque lancement d'app + chaque ouverture des Paramètres = 1 à 2 appels API. Sans cache ni ETag, une utilisation intensive peut atteindre la limite. Échec silencieux en cas de rate-limit (mauvaise réponse HTTP → `null`).

### 4.10 ⚪ INFO — Points OK / bien pensés
- Destination APK dans le **stockage interne de l'app** → zéro permission de stockage (scoped storage OK).
- Nettoyage des anciens APK avant enqueue.
- Appel réseau sur `Dispatchers.IO` avec timeouts 10 s.
- `BuildConfig` activé ; versionCode automatique ; asset `.apk` cherché dans la release.
- `enqueueUniquePeriodicWork` avec `ExistingPeriodicWorkPolicy.UPDATE` → pas de double planification du worker B.

---

## 5. Le mécanisme B — mise à jour des novels (état initial)

- `UpdateWorker` (WorkManager) planifié **toutes les 12 h** (`UpdateWorker.schedule`) depuis `NovelReaderApp.onCreate()`.
- Traite les novels **en séquence** (commentaire : MVP, parallélisable par novel via `SingleNovelUpdateWorker`).
- Pour chaque novel : fetch liste distante → compare aux chapitres locaux → **nouveaux chapitres** = `chapterNumber` absents → `novelDao.updateUnreadCount(slug, n)` + `repository.cacheChapters(...)`.
- `Result.retry()` si échec total, `Result.success()` sinon (même avec des échecs partiels).
- L'onglet **Updates** (`UpdatesViewModel`) affiche `repository.getLatestUpdates()` (aperçus de chapitres), sans lien direct avec le worker.
- Paramètre utilisateur `update_interval_hours` (défaut 12 h) **existe dans les prefs mais n'est pas re-lu par le worker** → le réglage des Paramètres est décoratif pour l'instant.

### Limites identifiées (corrigées en §8)
1. **Aucune notification envoyée** : le canal `novel_updates` et le réglage « Notifications » existent, mais rien n'est jamais posté → réglage décoratif.
2. **Aucune demande de permission `POST_NOTIFICATIONS`** (Android 13+) → même avec le code, rien ne s'afficherait.
3. **Badge non-lu faux** : `updateUnreadCount(slug, newChapters.size)` **écrase** le compteur (au lieu de le recalculer) et il n'est **jamais décrémenté** à la lecture → badge incohérent.
4. **Traitement séquentiel** et `SingleNovelUpdateWorker`/`scheduleNovelUpdate` **jamais appelés** (code mort).
5. **Aucun déclenchement manuel** : impossible de lancer une vérification sans attendre le cycle 12 h.

---

## 6. Recommandations prioritaires

1. **Persister le keystore de signature** (secret GitHub) et le réutiliser dans le workflow → sinon toute la fonction est morte à la première mise à jour (4.1).
2. Ajouter `REQUEST_INSTALL_PACKAGES` + gérer `startActivity` (activité de réglages « sources inconnues ») si échec (4.2).
3. `unregisterReceiver` dans `onComplete` / `onDestroy`, ou passer à un receiver déclaré dans le manifest (4.3).
4. Stocker `UpdateInfo` dans l'état du ViewModel au lieu de re-fetcher (4.4).
5. Découpler `compareVersions` des suffixes (stripper `-debug`, gérer `-beta`), et comparer aussi `versionCode` si possible (4.6).
6. Modéliser l'état d'update en type explicite + distinguer « hors-ligne » de « à jour » (4.7, 4.8).

---

## 7. ✅ Correctifs appliqués (2026-08-15)

| Problème | Correctif | Fichier(s) |
|---|---|---|
| 4.1 Signature jetable en CI | Workflow réécrit : keystore chargé depuis le secret `KEYSTORE_BASE64`, mots de passe via secrets (fallback jetable uniquement pour les builds de test, avec warning) | `.github/workflows/build.yml` |
| 4.2 Pas de permission d'installation | `REQUEST_INSTALL_PACKAGES` ajouté ; `installApk` gère `ActivityNotFoundException`/`SecurityException` et redirige vers l'écran « Installer des applications inconnues » | `AndroidManifest.xml`, `AppUpdateInstaller.kt` |
| 4.3 Fuite de receiver + mort du process | Le receiver dynamique **se désenregistre après réception** ; nouveau receiver **déclaré dans le manifest** (`ApkDownloadReceiver`) qui installe l'APK même si l'app a été tuée (ID + nom persistés en SharedPreferences, anti-doublon et anti-forgery par ID) | `AppUpdateInstaller.kt`, `ApkDownloadReceiver.kt` (nouveau), `AndroidManifest.xml` |
| 4.3' Broadcast forgé | Avant installation, vérification du **statut réel via `DownloadManager.query`** (pas seulement la réception du broadcast) | `AppUpdateInstaller.kt` |
| 4.4 Appel réseau redondant | `UpdateInfo` stocké dans `UpdateState.Available` ; `downloadUpdate()` le réutilise (aucun re-fetch) | `SettingsViewModel.kt` |
| 4.6 Comparaison de versions | `normalizeVersion()` ignore les suffixes (`-debug`, `-beta…`) | `AppUpdateChecker.kt` |
| 4.7/4.8 État UI + erreurs muettes | `UpdateState` (sealed) : `Idle/Checking/UpToDate/Available/Error` ; le checker retourne `UpdateCheckResult` tri-état ; erreurs affichées en rouge avec bouton « Réessayer » ; échec réseau ≠ « À jour » | `AppUpdateChecker.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` |
| Bonus : intervalle décoratif | `NovelReaderApp.scheduleUpdates()` lit `prefs.updateIntervalHours` (borné 4–48 h) avant de planifier le worker | `NovelReaderApp.kt` |

**Notes**
- Non corrigé (demande une décision produit) : le taux GitHub (60 req/h) reste sans cache — mitigé par le fait que la vérification n'a lieu qu'au lancement et à l'ouverture des Paramètres.
- Reste à faire côté déploiement (action manuelle du propriétaire du repo) : **créer le secret `KEYSTORE_BASE64`** (instructions dans `.github/workflows/build.yml` et `README.md`), sinon la CI retombe sur la clé jetable.
- Pas de compilation vérifiée dans le bac à sable (pas de SDK Android) : revue manuelle effectuée, un build local `./gradlew assembleDebug` reste recommandé avant release.

---

## 8. ✅ Correctifs appliqués — mises à jour des chapitres (2026-08-15)

| Problème | Correctif | Fichier(s) |
|---|---|---|
| Aucune notification envoyée | Nouveau `NovelUpdateNotifier` : notification « X nouveaux chapitres » sur le canal existant, respecte le réglage `notificationsEnabled`, vérifie la permission, appui → deep link vers le détail du novel | `NovelUpdateNotifier.kt` (nouveau) |
| Permission POST_NOTIFICATIONS jamais demandée | Demande au premier lancement (Android 13+) via `rememberLauncherForActivityResult` | `MainActivity.kt` |
| Badge non-lu écrasé / jamais décrémenté | `updateLibraryNovelChapters()` recalcule le badge depuis la table chapters (`refreshUnreadCount` = source de vérité unique) ; lecture (`markChapterAsRead`) et non-lecture (`markChapterAsUnread`) recalculent aussi ; ajout en bibliothèque initialise le badge | `NovelRepository.kt`, `ReaderViewModel.kt`, `DetailViewModel.kt` |
| Traitement séquentiel + code mort | `UpdateWorker` distribue désormais **un worker unique par novel** (`SingleNovelUpdateWorker`, en parallèle, retry individuel) ; logique commune centralisée dans `updateLibraryNovelChapters()` | `UpdateWorker.kt` |
| Pas de déclenchement manuel | Bouton « Vérifier » dans l'onglet Mises à jour → `UpdateWorker.runNow()` (worker one-time dédié, n'écrase pas le périodique) + confirmation par snackbar | `UpdatesScreen.kt`, `UpdatesViewModel.kt`, `UpdateWorker.kt` |
| Deep link vers le détail | Notification → `MainActivity.EXTRA_NOVEL_SLUG` → `NovelReaderNavigation(deepLinkSlug)` navigue vers `novel/{slug}` | `MainActivity.kt`, `NavGraph.kt` |

**Comportement final du flux « nouveaux chapitres »**
1. Cycle périodique (intervalle des Paramètres) **ou** bouton « Vérifier » → `UpdateWorker` distribue un worker par novel de la bibliothèque.
2. Chaque worker : fetch distante → diff → cache des nouveaux chapitres → badge non-lu recalculé → notification (si activée) → tap ouvre le détail du novel.
3. À la lecture d'un chapitre, le badge décrémente automatiquement.
4. Échec individuel → retry WorkManager (backoff exponentiel), sans bloquer les autres novels.

---

## 11. ✅ Audit « environnement de compilation » — build réel (2026-08-15)

Objectif : vérifier le code comme le ferait GitHub Actions, sans compiler à la main.
Environnement reconstitué dans le sandbox : **JDK 17 Temurin + Gradle 9.3.0 (wrapper du repo) + AGP 8.7.3 + SDK Android 35** (download via sdkmanager).

### Ce qui a été vérifié en réel
| Étape | Résultat |
|---|---|
| Configuration Gradle 9.3.0 + AGP 8.7.3 (`:app:help`) | ✅ BUILD SUCCESSFUL (pas d'incompatibilité de versions) |
| KSP — génération Room + Hilt (`kspDebugKotlin`) | ✅ OK |
| Compilation Kotlin complète (`compileDebugKotlin`) | ✅ **BUILD SUCCESSFUL** |
| Compilation Java + Hilt + dexing (D8) | ✅ OK |
| Packaging APK final (`mergeDebugGlobalSynthetics`) | ⚠️ Bloqué par la **RAM du sandbox** (~1 Go disponible, OOM du conteneur) — pas une erreur de code. Sur GitHub Actions (7 Go standard) ça passe. |

### 🔴 Erreurs réelles trouvées et corrigées par le compilateur
1. **Erreur de compilation `Int` vs `Long`** dans `NovelReaderApp.kt` (commit `90b47d7`) :
   `updateIntervalHours` est un `Flow<Int>`, `UpdateWorker.schedule()` attend un `Long` → ajout de `.toLong()`.
2. **Incohérence de schéma Room** (commit `9d765f3`) : la migration v4→v5 crée la colonne avec `DEFAULT 0`, mais l'entité ne le déclarait pas → la **validation de schéma Room aurait crashé au runtime** sur les installations existantes (`Migration didn't properly handle`), malgré une compilation OK. Correctif : `@ColumnInfo(defaultValue = "0")`.
3. **`local.properties` committé** avec un chemin SDK Windows de l'auteur → **retiré du suivi git** (fichier machine-spécifique, déjà dans `.gitignore` ; le CI définit `ANDROID_HOME` via `android-actions/setup-android`).

### ⚪ Observations (non bloquantes)
- Warning pré-existant : `statusBarColor` déprécié dans `ui/theme/Theme.kt:88` (hors périmètre des correctifs, à migrer vers `enableEdgeToEdge` si souhaité).
- ProGuard release : les classes `@Serializable` sont protégées par les règles auto du plugin kotlinx-serialization + règles existantes (`data.model.**`, `data.local.entity.**`) ; Hilt/Room apportent leurs consumer rules.
- La CI fait `assembleRelease` (R8/minify) puis `assembleDebug` : le minify ajoute du travail mémoire mais rien d'incompatible avec le code (aucune réflexion sur les nouvelles classes).

### Conclusion
Le code **compile** (Kotlin, KSP Room/Hilt, Java, dexing). Les 3 problèmes d'audit réels ont été corrigés et commités. Le packaging final de l'APK n'a pas pu être validé ici uniquement pour cause de RAM du sandbox — il se fera sur GitHub Actions.

---

## 9. ✅ Corrections basées sur le site réel — novelfrance.fr (2026-08-15)

Vérification live de `https://novelfrance.fr` : l'API REST est riche et **fiable**,
le parsing HTML n'était nécessaire que pour une partie du flux.

| Constat site | Problème dans le code | Correctif | Fichier(s) |
|---|---|---|---|
| `GET /api/chapters/latest` existe (skip/take, 20 par défaut) et renvoie **titre réel du chapitre + novel (titre, couverture, auteur, note)** | L'onglet Mises à jour parsait le HTML de `/latest` → titres tronqués (« Ch. 3157 » au lieu de « Choc et Stupeur »), fragile | `getLatestUpdates()` utilise **l'API** en priorité, parsing HTML en **fallback** ; items enrichis d'une **vignette de couverture** (Coil) | `NovelFranceApi.kt` (`getLatestChapters`), `NovelFranceSource.kt`, `Chapter.kt` (`novelCoverUrl`), `UpdatesScreen.kt` |
| `GET /api/chapters/{slug}` pagine par skip/take, `order=desc` | La vérification téléchargeait **TOUS** les chapitres de chaque novel (ex. ORV : 552 chapitres = **6 appels** par novel par cycle) | Nouvelle méthode `getNewChaptersSince(slug, connus)` : pagine en descendant et **s'arrête dès le plus haut numéro connu** → **1 appel** dans la quasi-totalité des cas (garde-fou 1000 chapitres) | `NovelFranceApi.kt`, `NovelSource.kt` (défaut filtrage), `NovelFranceSource.kt` (surcharge), `NovelRepository.updateLibraryNovelChapters` |

**Validation live de l'algorithme optimisé** (novel ORV, 552 chapitres) :
- à jour (max local 552) → 0 nouveau, **1 appel**
- max local 551 → 1 nouveau, 1 appel
- max local 540 → 12 nouveaux, 1 appel
- max local 400 → 152 nouveaux, 2 appels (au lieu de 6)
- bibliothèque vide de chapitres → pagination complète conservée

---

## 10. ✅ Onglet « Mises à jour » centré sur la bibliothèque (2026-08-15)

Demande : « quand je clique sur la page Mises à jour, j'obtiens les mises à
jour des chapitres des novels enregistrés dans la bibliothèque ».

Avant : l'onglet affichait le **flux global du site** (`/api/chapters/latest`),
identique pour tout le monde, sans rapport avec la bibliothèque.

Après : l'onglet affiche **les nouveaux chapitres détectés pour TES novels**
(données locales remplies par `UpdateWorker`), avec badge « NOUVEAU » sur les
non-lus, heure relative, et un déclenchement de vérification à l'ouverture.

| Fichier | Changement |
|---|---|
| `ChapterEntity` | Nouveau champ `addedAt` (0 = chargement initial, >0 = détecté par mise à jour) |
| `AppDatabase` | Version 5 + `MIGRATION_4_5` (`ALTER TABLE chapters ADD COLUMN addedAt … DEFAULT 0`), enregistrée dans `AppModule` |
| `ChapterDao` | `getLibraryUpdatesFlow(limit)` (réactif) + `getLibraryUpdatesOnce` + `getLibraryUpdatesCount` |
| `NovelRepository` | `cacheChapters(..., addedAt)` : le worker passe `System.currentTimeMillis()` → seuls les chapitres trouvés par une mise à jour apparaissent (l'ajout en bibliothèque passe 0, donc n'inonde pas la liste) ; `getLibraryUpdates()` expose le flux |
| `UpdatesViewModel` | Lit le **flux Room** (auto-mise à jour quand les workers insèrent) + `checkLibraryUpdates()` lancé à l'ouverture de la page |
| `UpdatesScreen` | Liste locale : titre du novel, « Ch. N — titre », badge **NOUVEAU** (non-lu), « il y a X min/h/j » ; tap → détail du novel ; état vide avec bouton « Vérifier maintenant » ; barre de progression pendant la vérification |
| `LoadingError.kt` | `EmptyView` accepte une `action` optionnelle (non cassant pour les autres écrans) |

**Fonctionnement**
1. Ouverture de l'onglet → `UpdateWorker.runNow()` lance un worker par novel de la bibliothèque.
2. Chaque worker détecte les nouveaux chapitres et les insère avec `addedAt > 0`.
3. Le flux Room émet → la liste se met à jour **automatiquement** au fil des résultats (réactif).
4. Le badge « NOUVEAU » disparaît quand le chapitre est lu.
5. Le flux global du site n'est plus affiché ici (méthode source conservée, inutilisée par l'UI).
