# Miroir Téléphone

Affiche l'écran d'un téléphone Android sur un Mac, sans fil, en scannant un QR code.

- `desktop/` : application Mac (Electron).
- `android/` : application Android (Kotlin).
- `docs/` : aides d'installation et décision sur la coque.

## Utiliser

1. Installer l'app Mac : voir `docs/aide-mac.md`.
2. Installer l'app Android : voir `docs/aide-android.md`.
3. Lancer l'app Mac, scanner le QR code avec le téléphone, confirmer la capture.

## Construire

- Mac : `npm --prefix desktop install` puis `npm --prefix desktop run dist` (deux DMG, arm64 et x64, dans `desktop/dist`).
- Android : `cd android && ./gradlew assembleRelease` (JDK 17, par exemple celui d'Android Studio). L'APK est signe avec la clé de debogage pour une diffusion à la main.
- Tests : `npm --prefix desktop test` et `cd android && ./gradlew testDebugUnitTest`.

## Mesures des livrables

| Livrable | Taille | Seuil |
|---|---|---|
| DMG arm64 | 114 Mo | 120 Mo |
| DMG x64 | 118 Mo | 120 Mo |
| APK (arm64-v8a et armeabi-v7a) | 52 Mo | - |

Latence médiane mesurée : 106 ms (cible 300 ms). Le seuil de poids est de 120 Mo depuis la décision sur la coque (`docs/decision-coque.md`).
