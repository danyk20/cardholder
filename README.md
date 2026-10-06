# Cardholder

[![CI](https://github.com/danyk20/cardholder/actions/workflows/ci.yml/badge.svg)](https://github.com/danyk20/cardholder/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

An offline, privacy-first Android wallet for your **bank cards**, **ID cards** and **loyalty cards**.

> 🚧 Work in progress – see the [roadmap](docs/ROADMAP.md).

## Features
- Three card types with type-specific details:
  - **Bank card** – number, expiry, card holder, CVV (always hidden behind biometric / device credential).
  - **ID card** – issuing country.
  - **Loyalty card** – shop (bundled list or custom) and its barcode/QR code, shown full-screen at max brightness for scanning at the till.
- Optionally capture the **front and back** of every card with the camera (automatic edge detection).
- Scan loyalty barcodes/QR codes with the camera.
- **Per-card lock**: protect any card with fingerprint/face/device PIN.
- Password-encrypted **backup export/import**.
- No internet permission, encrypted database, no cloud backup – your data never leaves the device unless you export it.

## Tech stack
Kotlin · Jetpack Compose · Material 3 · Hilt · Room + SQLCipher · Android Keystore · CameraX · ML Kit · ZXing · Coroutines/Flow · Gradle convention plugins · GitHub Actions

## Building
Requirements: JDK 17+, Android SDK with platform 37.
```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew spotlessCheck detekt lintDebug   # static analysis
```

## Project structure
```
app/                 Application, navigation, DI wiring
build-logic/         Gradle convention plugins
core/model           Pure Kotlin domain models
core/domain          Repository contracts, use cases, validators
core/data            Repository implementations, backup
core/database        Room + SQLCipher
core/security        Keystore keys, envelope encryption, authentication
core/storage         Encrypted card image storage
core/scanning        Document & barcode scanning (ML Kit, CameraX)
core/barcode         Barcode/QR rendering (ZXing)
core/designsystem    Theme and design components
core/ui              Shared UI building blocks
feature/*            Card list, editor, detail, full-screen barcode, settings
```

## Contributing & security
See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## License
[Apache License 2.0](LICENSE)
