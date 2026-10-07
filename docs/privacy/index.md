---
title: Cardholder privacy policy
permalink: /privacy/
---

# Cardholder privacy policy

_Last updated: 7 October 2026_

Cardholder is an open-source app for storing bank, ID and loyalty cards on an Android phone. It is developed by Daniel Košč ("I", "me"). This policy explains what happens to your data when you use the app.

## Summary

- Your cards stay on your device. I don't run any servers and never receive your cards, photos or backups.
- There is no account, no advertising and no analytics or tracking by the app itself.
- The app connects to the internet only to download an official logo from Wikimedia Commons, and only when you ask for it.
- Google's on-device ML Kit libraries, used for scanning, send anonymous diagnostic information to Google (see below).

## Data stored on your device

Everything you enter into Cardholder is stored only on your device:

- card details (for example card number, expiry date, card holder, CVV, issuing country, document number, loyalty card code, shop or bank);
- optional photos of the front and back of your cards;
- logos you download or upload;
- app settings (theme, sort order).

The database and all photos are encrypted with keys held in your phone's secure hardware (Android Keystore). The CVV and the details of locked cards can only be decrypted after you confirm your identity with fingerprint, face or screen lock. The app opts out of Android cloud backup, so this data is not copied to Google Drive.

When you uninstall the app or clear its data, all of it is permanently deleted.

## Permissions

- **Camera** – to scan barcodes and QR codes on your cards. Images are analysed on the device and are neither stored nor sent anywhere.
- **NFC** – to read the card number and expiry date from a contactless bank card you hold to the phone. The data is read on the device and only used to fill in the form. The CVV and PIN cannot be read and are never requested.
- **Internet** – only for downloading official logos (see below).

Photos of card sides are taken with the Google Play services document scanner, which runs on the device and returns the image directly to the app. To save typing, the app reads the text and barcode on those photos with Google ML Kit on the device (for example the card number, expiry date or the machine-readable lines of an ID card) and suggests them in the form; the recognised text is kept in memory only and is not sent anywhere.

## Logo downloads

When you choose to use the official logo of a shop or bank, the app downloads the image file from Wikimedia Commons (`upload.wikimedia.org`). Like any website, Wikimedia receives your IP address and the requested file name; no card data is sent. Wikimedia's handling of this request is governed by the [Wikimedia Foundation privacy policy](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy). If you never choose an official logo, the app never connects to the internet.

## Google ML Kit diagnostics

Barcode scanning and document scanning use Google ML Kit, which runs entirely on the device. ML Kit sends Google technical information such as device model, OS version, app package and version, performance metrics, API configuration and a per-installation identifier that does not identify you or your device. It doesn't include images, card numbers or barcode contents. Google uses this information for diagnostics and usage analytics. See [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) and the [Google privacy policy](https://policies.google.com/privacy).

## Backups

You can export an encrypted backup file protected by a password you choose. The file is saved wherever you pick (for example, your phone storage or a cloud drive you use). Its encryption uses AES-256-GCM with a key derived from your password. I have no access to your backup files or passwords and cannot recover a forgotten password.

## Children

Cardholder is not directed at children under 13.

## Changes

Changes to this policy will be published on this page and in the app's [source repository](https://github.com/danyk20/cardholder).

## Contact

For questions about this policy, please open an issue at <https://github.com/danyk20/cardholder/issues>.
