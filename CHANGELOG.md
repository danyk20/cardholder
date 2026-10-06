# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-06
### Added
- Bank, ID and loyalty cards with type-specific details and validation.
- Optional front/back photos via ML Kit document scanner or gallery; encrypted storage.
- Live barcode/QR scanning and barcode detection in card photos.
- Full-screen, max-brightness barcode display for 13 symbologies; loyalty cards show their code in the list.
- CVV hidden behind biometric/device-credential authentication; per-card lock.
- Encrypted database (SQLCipher) and Keystore envelope encryption; auth-bound RSA keys for protected data.
- Secure screens and dialogs, sensitive auto-clearing clipboard, re-lock when backgrounded.
  *Allow screenshots* in Settings lifts the screenshot protection until you leave the app.
- Password-encrypted backup export/import (PBKDF2-HMAC-SHA256, AES-256-GCM).
- Read bank cards over NFC: number, expiry and (when the card provides it) holder name are filled in
  automatically. The CVV is never stored on the chip and still has to be typed.
- Shop and bank catalogues (55 shops, 145 banks) with official, freely licensed logos downloaded from
  Wikimedia Commons on request, or your own logo; card network logos shown automatically. The `INTERNET`
  permission is used only for these downloads (HTTPS, Wikimedia hosts only).
- Sort cards by name, date added or a custom order, with drag-to-reorder and accessibility actions.
- Translations into 30 languages: Albanian, Arabic, Bengali, Chinese (Simplified and Traditional), Czech,
  Danish, Estonian, Finnish, French, German, Greek, Hindi, Hungarian, Italian, Japanese, Korean, Norwegian,
  Persian, Polish, Portuguese (Brazil and Portugal), Romanian, Russian, Serbian (Cyrillic and Latin),
  Slovak, Spanish, Swahili, Swedish, Turkish and Ukrainian. The app language can be chosen in the system
  settings (Android 13+).
- Settings: theme, dynamic colour, backup, privacy policy, logo credits, about.
- Google Play store listing for every language (`fastlane/metadata/android`).
