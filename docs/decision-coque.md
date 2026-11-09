# Decision sur la coque (Electron ou Tauri)

Statut : en attente de decision (mesures partielles).

## Mesures (2026-11-09)

| Mesure | Resultat | Seuil | Verdict |
|---|---|---|---|
| DMG arm64 | 114 Mo | 60 Mo | depasse |
| DMG x64 | 118 Mo | 60 Mo | depasse |
| App installee arm64 / x64 | 256 Mo / 260 Mo | - | - |
| Latence mediane (Wi-Fi, TECNO CG7) | 106 ms (max 248 ms, 25 echantillons) | 300 ms | ok |
| Memoire pendant un partage | non mesuree | 250 Mo | a faire |

Les DMG sont deja alleges (asar, langues fr et en, fichiers limites a src et electron). Le poids vient du runtime Chromium d'Electron, il ne descendra pas sous 60 Mo avec Electron.

## Options

- Garder Electron : fonctionne et est teste sur appareil, mais DMG d'environ 115 Mo (seuil de 60 Mo non tenu).
- Essai Tauri : DMG attendu de quelques Mo, mais demande d'installer Rust, de refaire la coque (taches 3, 8 et 9 a reviser) et de verifier la video WebRTC dans WKWebView.
