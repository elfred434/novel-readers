# NovelReader

Application Android de lecture de novels inspirée de Mihon, avec téléchargement des chapitres et lecture hors-ligne. L'interface utilise l'identité visuelle « Studio Noir ».

<!-- TODO: screenshot — ajouter des captures de la bibliothèque et du lecteur -->

## Fonctionnalités

- Bibliothèque de novels avec catégories.
- Découverte et recherche dans le catalogue NovelFrance.
- Lecteur avec réglage de la taille de police et de l'interligne.
- Téléchargement local des chapitres pour la lecture hors-ligne.
- Téléchargements simultanés sur Wi-Fi.
- Thèmes sombre, AMOLED, clair et système.
- Stockage interne ou sélection d'un dossier via le Storage Access Framework.

## Stack

| Couche | Technologie |
|---|---|
| Langage | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Données locales | Room, SQLite |
| Parsing / sérialisation | Jsoup, kotlinx.serialization |
| Réseau | OkHttp |
| Téléchargements | Foreground Service, WorkManager |
| Injection de dépendances | Hilt |
| CI/CD | GitHub Actions |

## Prérequis

- Android Studio
- Android SDK adapté au projet

## Installation depuis les sources

```bash
git clone https://github.com/elfred434/novel-readers.git
cd novel-readers
./gradlew assembleRelease
```

L'APK est généré dans `app/build/outputs/apk/release/`. Pour installer une version publiée, consulter les [releases](https://github.com/elfred434/novel-readers/releases).

## Captures

<!-- TODO: ajouter des captures réelles de l'application -->

## Contribution

Les contributions sont les bienvenues. Voir [CONTRIBUTING.md](CONTRIBUTING.md), puis ouvrir une pull request après validation locale.

## Licence

Ce projet est distribué sous licence **GNU GPL v3.0**. Voir [LICENSE](LICENSE).

## Crédits

- [NovelFrance](https://novelfrance.fr) pour le contenu.
- [Mihon](https://mihon.app) pour l'inspiration architecturale.
