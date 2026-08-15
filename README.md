# NovelReader

> Une application Android de lecture de novels inspirée de Mihon, avec une identité visuelle **Studio Noir**.

| Build | Version |
|-------|---------|
| [![Build](https://github.com/tonuser/NovelReader/actions/workflows/build.yml/badge.svg)](https://github.com/tonuser/NovelReader/actions) | **v1.0.0** |

## ✨ Fonctionnalités

- 📚 **Bibliothèque** — Suis tes novels préférés, organise-les par catégories
- 🔍 **Découverte** — Parcours le catalogue NovelFrance, recherche par titre
- 📖 **Lecteur** — Interface épurée, taille de police et interligne réglables
- 📥 **Téléchargement** — Chapitres sauvegardés en local (JSON), lecture hors-ligne intégrale
- ⚡ **Mode Haute Vitesse** — Jusqu'à 5 téléchargements simultanés sur WiFi
- 🎨 **Studio Noir** — Design rubis atténué `#CC3344`, 5 niveaux de gris, fond lecteur `#131313`
- 🌙 **Thèmes** — Sombre, AMOLED, Clair, Système
- 📂 **Stockage** — Dossier auto-créé, ou choix via SAF (Storage Access Framework)

## 🖼️ Aperçu

*À venir*

## 🚀 Installation

### Depuis les releases
1. Va dans [Releases](https://github.com/tonuser/NovelReader/releases)
2. Télécharge le dernier `NovelReader-vX.X.X.apk`
3. Ouvre le fichier sur ton Android (autorise les installations tierces si nécessaire)

### Depuis les sources
```bash
git clone https://github.com/tonuser/NovelReader.git
cd NovelReader
./gradlew assembleRelease
# APK généré dans app/build/outputs/apk/release/
```

> ⚠️ **Mise à jour intégrée** : pour que l'app puisse se mettre à jour depuis
> l'App Store, **toutes les releases doivent être signées avec la même clé**.
> Configure le secret GitHub `KEYSTORE_BASE64` (keystore encodé en base64) :
> ```bash
> keytool -genkey -v -keystore novelreader.keystore -alias novelreader \
>   -keyalg RSA -keysize 2048 -validity 10000 -storepass <mdp> -keypass <mdp> \
>   -dname "CN=NovelReader, OU=Dev, O=NovelReader, L=City, ST=State, C=FR"
> base64 -w 0 novelreader.keystore   # → coller dans le secret KEYSTORE_BASE64
> ```
> Secrets optionnels : `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
> (défaut : `novelreader`). Sans `KEYSTORE_BASE64`, les APK de test CI sont
> signés avec une clé jetable et ne peuvent pas être installés par-dessus une release.

## 📁 Structure du projet

```
NovelReader/
├── app/
│   └── src/main/java/com/novelreader/
│       ├── data/
│       │   ├── download/        # Gestionnaire de téléchargements + Service
│       │   ├── local/           # Room (DAO, entities, DB)
│       │   ├── model/           # Modèles de données (Novel, Chapter)
│       │   ├── network/         # État réseau (WiFi, mobile)
│       │   ├── remote/          # Source NovelFrance (API, parsing Jsoup)
│       │   ├── repository/      # Repository central
│       │   ├── storage/         # Stockage SAF + Interne
│       │   └── worker/          # WorkManager (mises à jour périodiques)
│       ├── di/                  # Injection de dépendances Hilt
│       ├── ui/
│       │   ├── components/      # Composants réutilisables
│       │   ├── navigation/      # Navigation Compose
│       │   ├── screens/         # Écrans (bibliothèque, détail, lecteur…)
│       │   └── theme/           # Thème Studio Noir
│       └── MainActivity.kt
├── .github/workflows/build.yml  # CI/CD GitHub Actions
└── build.gradle.kts
```

## 🛠️ Technologies

| Couche | Technologie |
|--------|-------------|
| Langage | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Base de données | Room (SQLite) |
| Parsing | Jsoup + kotlinx.serialization |
| Réseau | OkHttp |
| Téléchargements | Foreground Service + WorkManager |
| DI | Hilt |
| CI/CD | GitHub Actions |

## 🤝 Contribution

Les contributions sont les bienvenues ! Voir [CONTRIBUTING.md](CONTRIBUTING.md).

1. Fork le projet
2. Crée une branche (`git checkout -b feature/amazing`)
3. Commit (`git commit -m 'Add amazing feature'`)
4. Push (`git push origin feature/amazing`)
5. Ouvre une Pull Request

## 📄 Licence

Ce projet est sous licence **GNU General Public License v3.0** — voir [LICENSE](LICENSE) pour plus de détails.

## 🙏 Remerciements

- [NovelFrance](https://novelfrance.fr) pour le contenu
- [Mihon](https://mihon.app) pour l'inspiration architecturale
