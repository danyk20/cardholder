# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]
### Added
- Shop logos: when a catalogue shop is selected, choose its official logo (freely licensed, downloaded from
  Wikimedia Commons on request), upload your own, or keep the card plain. Logos are shown on the card.
- Database version 2 (logo column) with an automatic, tested migration.

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
