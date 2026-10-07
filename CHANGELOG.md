# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.0] - 2026-10-07
### Added
- Fill in card details from the scanned photos (on the device): card number, expiry, holder, CVV and bank
  of bank cards; document number, expiry, country and name from the machine-readable zone of IDs and
  passports; barcode and shop of loyalty cards. Only empty fields are filled, and the user is asked to check.
- Live card number and expiry check with a check mark (Luhn and the network's lengths).
- Favourites (star in the card details) and a "Most used" order; favourites come first.
- App shortcuts and a home-screen widget for loyalty cards, one tap from the code.
- Expiry reminders a month before a bank card or ID expires, and 7 months before an ID expires, since many
  countries require 6 months of validity for travel (Settings > Notifications).
- Notes on every card, encrypted with its details.
- Warning before saving a card from the same shop or bank, or with the same name, as an existing one.
- Lens and camera choice in the barcode scanner (zoom steps and camera switch).
- Nine light card colours; generated bank card faces show a chip and the contactless symbol, ID cards a
  flag.
- Cards morph from the list into their details.

### Changed
- Card text colour is chosen by contrast (WCAG); some colours were adjusted to stay readable.
- Status bar icons follow the app's theme; dark mode no longer flashes light between screens.
- Database version 5 (favourites, usage, expiry dates) with an automatic migration.

## [1.0.0] - 2026-10-06
### Added
- Bank, ID and loyalty cards with type-specific details and validation.
- Optional front/back photos via ML Kit document scanner or gallery; encrypted storage.
- Live barcode/QR scanning and barcode detection in card photos.
- Full-screen, max-brightness barcode display for 13 symbologies; loyalty cards show their code in the list.
- CVV hidden behind biometric/device-credential authentication; per-card lock. Protected data is only shown
  after a prompt in the app itself, never just because the phone was unlocked recently.
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
- Settings: theme, dynamic colour, backup, *Delete all data* (with an explicit "I understand" confirmation),
  privacy policy, logo credits, about.
- Leaving the card editor with unsaved changes asks before discarding them.
- Robust error handling: damaged data, storage and camera errors show a message instead of crashing; if the
  device lost the app's keys, a recovery screen offers to start over.
- Google Play store listing for every language (`fastlane/metadata/android`).
