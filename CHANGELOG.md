# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]
### Added
- Shop logos: when a catalogue shop is selected, choose its official logo (freely licensed, downloaded from
  Wikimedia Commons on request), upload your own, or keep the card plain. Logos are shown on the card.
- Database version 2 (logo column) with an automatic, tested migration.
- Bank cards: choose the issuing bank from a catalogue of 145 banks (EU, Switzerland, Slovakia/Czechia,
  Germany/Austria, digital banks, US) or type any name; official bank logos on request, and the card network
  logo (Visa, Mastercard, …) shown automatically. Database version 4 stores the bank.
- Sort cards by name, date added or a custom order; *Reorder cards* lets you drag cards into place
  (with "Move up/down" accessibility actions). Database version 3 stores the order.
- Read bank cards over NFC: number, expiry and (when the card provides it) holder name are filled in
  automatically. The CVV is never stored on the chip and still has to be typed.
- Translations into 30 languages: Albanian, Arabic, Bengali, Chinese (Simplified and Traditional), Czech,
  Danish, Estonian, Finnish, French, German, Greek, Hindi, Hungarian, Italian, Japanese, Korean, Norwegian,
  Persian, Polish, Portuguese (Brazil and Portugal), Romanian, Russian, Serbian (Cyrillic and Latin),
  Slovak, Spanish, Swahili, Swedish, Turkish and Ukrainian. The app language can be chosen in the system
  settings (Android 13+).
- Privacy policy, linked from Settings.
- Google Play store listing for every language (`fastlane/metadata/android`).

### Changed
- The app requests the `INTERNET` permission, used only for the logo download (HTTPS, Wikimedia hosts only).

## [1.0.0] - 2026-10-06
### Added
- Bank, ID and loyalty cards with type-specific details and validation.
- Optional front/back photos via ML Kit document scanner or gallery; encrypted storage.
- Live barcode/QR scanning and barcode detection in card photos.
- Full-screen, max-brightness barcode display for 13 symbologies.
- CVV hidden behind biometric/device-credential authentication; per-card lock.
- Encrypted database (SQLCipher) and Keystore envelope encryption; auth-bound RSA keys for protected data.
- Secure screens and dialogs, sensitive auto-clearing clipboard, re-lock when backgrounded.
- Password-encrypted backup export/import (PBKDF2-HMAC-SHA256, AES-256-GCM).
- Settings: theme, dynamic colour, backup, about.
