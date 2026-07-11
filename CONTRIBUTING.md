# Contribuer à NovelReader

Merci de t'intéresser à ce projet ! 🎉

## 🤝 Comment contribuer

### Signaler un bug
1. Vérifie que le bug n'a pas déjà été signalé dans [Issues](https://github.com/tonuser/NovelReader/issues)
2. Ouvre une nouvelle issue avec :
   - Version de l'app (Paramètres → bas de page)
   - Appareil Android + version
   - Étapes pour reproduire
   - Comportement attendu vs réel
   - Capture d'écran si possible

### Suggérer une amélioration
- Ouvre une issue avec le label `enhancement`
- Explique clairement le besoin et le cas d'usage

### Soumettre du code

1. **Fork** le projet
2. Crée une branche : `git checkout -b feature/ma-feature`
3. Suis les conventions de code :
   - Kotlin — suit le style officiel Kotlin
   - Nommage : `camelCase` pour les variables/fonctions, `PascalCase` pour les classes
   - Pas de `===` dans les resources (on est en Kotlin)
   - Les ViewModels utilisent Hilt (`@HiltViewModel`)
   - Le thème utilise les couleurs Studio Noir définies dans `Color.kt`
4. **Teste** que le build passe : `./gradlew assembleDebug`
5. Commit : `git commit -m "feat: description courte"`
6. Push : `git push origin feature/ma-feature`
7. Ouvre une **Pull Request** vers `main`

## 🧪 Build local

```bash
# Build debug
./gradlew assembleDebug

# Build release (nécessite un keystore)
./gradlew assembleRelease

# Lancer les tests
./gradlew test
```

## 📐 Architecture

```
ViewModel → Repository → Source (API/parsing)
                       → DB (Room/SQLite)
                       → Storage (fichiers JSON)
```

- Les ViewModels utilisent `StateFlow` pour l'UI
- Les Repository sont la seule source de vérité
- Les Sources implémentent `NovelSource` (extensible)

## 🎨 Studio Noir

Les couleurs sont définies dans `ui/theme/Color.kt`. Respecte la hiérarchie :
- **Rubis atténué** (`#CC3344`) — UNIQUEMENT pour les CTAs principaux (1 par écran)
- **5 niveaux de gris** — pour toute la hiérarchie visuelle
- Ne JAMAIS ajouter de nouvelle couleur d'accent
