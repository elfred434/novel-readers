# 🔍 Analyse du nouveau clone — novel-readers (2026-08-15)

> Analyse complète du clone frais de `https://github.com/elfred434/novel-readers`,
> avec vérification **en direct** des endpoints novelfrance.fr.
> Découverte majeure : une **branche d'audit orpheline** contient des correctifs
> critiques absents de `main`.

---

## 1. Topologie du dépôt

```
main  db8b937  Fix: retirer local.properties du suivi git (notre fix, 15/08)
      bd3ba09  « mise à jour » — notre travail d'août squashed (poussé par l'auteur)
      6745533  base d'origine (11/07)

arena  ed6ac2f  « fix: correction des bugs critiques + alignement API réelle »
        (branche origin/arena/019f7023-novel-readers, 17/07) ← AUDIT ANTÉRIEUR
        (d'une autre session) — JAMAIS FUSIONNÉ DANS main
```

- **Tags** : `v1.0.0`, `v1.0.44`, `v1.0.46`, `v1.0.47`, `v1.0.51`
- **98 fichiers** suivis, ~8 815 lignes de Kotlin
- Remote `origin` correctement configuré

---

## 2. Ce que contient `main` (notre travail d'août, poussé par l'auteur)

Le commit `bd3ba09` intègre tout ce que nous avions fait (1288 insertions) :

- **Auto-update de l'app** : `AppUpdateChecker` (tri-état), `AppUpdateInstaller`
  (DownloadManager système + receiver auto-désenregistré), `ApkDownloadReceiver`
  (manifest, survie à la mort du process), permission `REQUEST_INSTALL_PACKAGES`,
  état UI `UpdateState`, CI keystore via `KEYSTORE_BASE64`.
- **Mises à jour des chapitres** : notifications (`NovelUpdateNotifier`),
  badge non-lu recalculé, workers parallèles par novel, bouton « Vérifier »,
  onglet Mises à jour **centré bibliothèque** (`addedAt` + migration Room v5).
- **API réelle** : `/api/chapters/latest` pour le flux, `getNewChaptersSince()`
  optimisé (1 appel au lieu de N), fallbacks HTML conservés.
- `ANALYSE_MISE_A_JOUR.md` (24 Ko), README keystore, etc.

✅ Vérifié : `.toLong()` (correction compil), `@ColumnInfo(defaultValue="0")`
(migration Room) sont bien présents dans la version poussée.

---

## 3. La branche `arena/019f7023-novel-readers` (17/07) — audit antérieur non fusionné

`ed6ac2f` est un **audit complet d'une session précédente** (juillet) qui corrigeait
des bugs critiques **de nature différente** de notre travail d'août :

| Correctif juillet (arena) | Présent sur main ? |
|---|---|
| Recherche via `/api/search?q=` (le param `search` de `/api/novels` est **ignoré** par le serveur) | ❌ NON |
| Filtre genre via param **`genres`** (pluriel — le singulier est ignoré) | ❌ NON |
| ProGuard : `-keep @kotlinx.serialization.Serializable class com.novelreader.**` (parsing JSON cassé en release R8) | ❌ NON |
| Contenu chapitre via API directe `/api/chapters/{slug}/{chapterSlug}` | ❌ NON (parser HTML RSC) |
| `cacheChapters()` en **upsert** préservant `isRead`/`scrollPosition` (au lieu de REPLACE) | ❌ NON (REPLACE) |
| Keystore obligatoire via env (pas de mots de passe par défaut) | ⚠️ Partiel (fallback `novelreader` conservé) |
| Persistance réglages lecteur (DataStore) + restauration scroll | ⚠️ Partiel |
| Annulation réelle des téléchargements (Jobs + mutex), WiFi-only | ⚠️ Partiel |
| Suppression bibliothèque = suppression fichiers, badge « Lu », thème sombre au démarrage, `POST_NOTIFICATIONS` | ⚠️ Variable |
| Toggle extensions persisté | ⚠️ Variable |
| Test unitaire `NovelFranceParserTest` | ✅ Présent aussi sur main (189 lignes) |

**Deux séries de correctifs complémentaires mais disjointes** : main a le système
de mises à jour, arena a les correctifs de stabilité/fiabilité API.

---

## 4. 🔴 Problèmes vérifiés EN LIVE sur `main` (correctifs juillet manquants)

### 4.1 Recherche cassée (fonctionnelle)
`BrowseViewModel` → `repository.searchNovels(query)` → `api.getNovels(search=...)`
→ **`/api/novels?search=shadow` renvoie 555 novels non filtrés** (le serveur
ignore le paramètre). Testé en direct :

| Requête | Résultat |
|---|---|
| `/api/novels?search=shadow&limit=3` | total **555**, titres sans rapport |
| `/api/search?q=shadow&limit=3` | total **10**, titres pertinents ✅ |

→ **La recherche de l'app ne filtre pas du tout** actuellement.

### 4.2 Filtre genre cassé (fonctionnelle)
`BrowseViewModel` passe `genre = state.genreSlug` (singulier) :

| Requête | Résultat |
|---|---|
| `/api/novels?genre=action` | total **555** (ignoré) |
| `/api/novels?genres=action` | total **440** (filtré) ✅ |

→ Le filtre « Action » affiche tous les novels.

### 4.3 ProGuard release — risque de parsing JSON cassé (critique release)
`app/proguard-rules.pro` sur main ne conserve que `data.model.**` et
`data.local.entity.**`. Les DTO `@Serializable` de `data.remote.novelfrance.**`
(API), `data.repository.**` (cache JSON `StorageContent`), `data.update.**`
(`GitHubRelease`) et `data.download.**` (téléchargements JSON) **ne sont pas
protégés** → avec `isMinifyEnabled = true` (release), R8 peut renommer les
champs → **parsing JSON cassé en release**.
L'audit juillet avait ajouté la règle générique :
```
-keep @kotlinx.serialization.Serializable class com.novelreader.** { *; }
```

### 4.4 Contenu des chapitres — parser HTML fragile
`main` lit le contenu des chapitres en parsant le flux Next.js RSC (Jsoup) avec
fallback DOM. L'API directe fonctionne parfaitement :
`GET /api/chapters/omniscient-readers-viewpoint/chapter-552`
→ `{ title, paragraphs: 226, authorNote, nextChapter, novel }` ✅
(Le format 404 testé au début était `.../552` sans `chapter-` ; il faut le slug complet.)

→ Pas un bug, mais une **fragilité** (le parser RSC se désynchronise si le site
change) que l'API directe élimine.

### 4.5 `cacheChapters()` — sémantique REPLACE (risque)
`insertChapters` utilise `OnConflictStrategy.REPLACE` : ré-insérer un chapitre
existant **écrase `isRead`/`readAt`/`scrollPosition`**. Atténué sur main car le
worker ne cache que les nouveaux chapitres (`getNewChaptersSince`), mais tout
appel avec la liste complète (ex. `toggleLibrary`) réinitialiserait l'historique
de lecture. L'audit juillet avait basculé en upsert préservant ces champs.

---

## 5. Détails complémentaires

| Sujet | Constat |
|---|---|
| `local.properties` | ✅ Retiré du suivi git par notre commit `db8b937` (chemin SDK Windows invalide en CI) |
| `gradlew` | ✅ Mode exécutable restauré (100755) |
| Tests | 1 test unitaire (`NovelFranceParserTest`, 189 lignes) présent sur main |
| CI keystore | Fallback par défaut `novelreader` conservé (permissif) vs arena (obligatoire) — acceptable mais moins strict |
| Workflow CI | `assembleRelease` (R8 minify) + `assembleDebug` + création Release — cohérent |
| Versionnage | `versionCode = run_number`, `versionName = 1.0.X` — ok |
| Build | Validé en audit précédent : Gradle 9.3 + AGP 8.7.3 + SDK 35 → `compileDebugKotlin` BUILD SUCCESSFUL |

---

## 6. Recommandations (par ordre de priorité)

1. **🔴 4.3 ProGuard** — ajouter les keeps `@Serializable` avant toute release :
   c'est le seul correctif qui peut casser silencieusement la release.
2. **🔴 4.1 Recherche** — basculer sur `/api/search?q=` (+ debounce, déjà prévu
   côté juillet).
3. **🟠 4.2 Filtre genre** — passer `genre` → `genres` (pluriel).
4. **🟠 4.4 Contenu chapitre** — utiliser l'API directe
   `/api/chapters/{slug}/{chapterSlug}` avec fallback HTML conservé.
5. **🟡 4.5 cacheChapters** — upsert préservant `isRead`/`scrollPosition`.
6. **Stratégie** : fusionner la branche `arena/019f7023-novel-readers` dans
   `main` (ou cherry-pick `ed6ac2f`), puis rejouer nos correctifs d'août s'il y a
   conflit — les deux séries sont complémentaires.

---

## 7. Conclusion

Le clone frais est **propre et fonctionnel pour le système de mises à jour**
(tout notre travail d'août est présent), mais il **manque 5 correctifs critiques
de l'audit de juillet** restés sur une branche orpheline. Trois d'entre eux sont
**vérifiés en direct contre le site** : recherche non filtrée, filtre genre
inopérant, et un risque ProGuard sur les APK de release. Le point le plus urgent
avant la prochaine release est le **ProGuard** (4.3) ; les deux bugs
fonctionnels (recherche, genre) sont faciles à corriger (2 lignes chacune).
