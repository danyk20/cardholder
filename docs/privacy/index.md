---
title: Cardholder privacy policy
permalink: /privacy/
---

# Cardholder privacy policy

_Last updated: 18 October 2026 (update checks)_

Cardholder is an open-source app for storing bank, ID and loyalty cards on an Android phone. It is developed by Daniel Košč ("I", "me"). This policy explains what happens to your data when you use the app.

## Summary

- Your cards stay on your device. I don't run any servers and never receive your cards, photos or backups.
- There is no account, no advertising and no analytics or tracking by the app itself.
- The app connects to the internet only to download an official logo from Wikimedia Commons and to check for updates, and only when you ask for it (or, for updates, once a week if you turn that on).
- If you turn on card suggestions, the app remembers on your device where you used your cards. This location data is never sent to me or anyone else.
- Google's on-device ML Kit libraries, used for scanning, send anonymous diagnostic information to Google (see below).

## Data stored on your device

Everything you enter into Cardholder is stored only on your device:

- card details (for example card number, expiry date, card holder, CVV, issuing country, document number, loyalty card code, shop or bank);
- optional photos of the front and back of your cards;
- logos you download or upload;
- app settings (theme, sort order, card suggestions, the country filter);
- the countries each card is valid in, if you chose any;
- the country the phone is in, as detected for the card list (see below), and any country you picked by hand;
- if you turn on card suggestions: the places where you used your cards (see below).

The database and all photos are encrypted with keys held in your phone's secure hardware (Android Keystore). The CVV and the details of locked cards can only be decrypted after you confirm your identity with fingerprint, face or screen lock. The app opts out of Android cloud backup, so this data is not copied to Google Drive.

When you uninstall the app or clear its data, all of it is permanently deleted.

## Permissions

- **Camera** – to scan barcodes and QR codes on your cards. Images are analysed on the device and are neither stored nor sent anywhere.
- **NFC** – to read the card number and expiry date from a contactless bank card you hold to the phone. The data is read on the device and only used to fill in the form. The CVV and PIN cannot be read and are never requested.
- **Internet** – only for downloading official logos and checking for updates (see below).
- **Install apps** (only the version from GitHub) – to install an update you downloaded from within the app. Android asks you to allow it the first time.
- **Location (precise) and Wi‑Fi state** – only if you turn on card suggestions (see below).
- **Location (approximate)** – only if you have already allowed it for Cardholder. It is used to find the country you are in, if the phone's mobile network can't tell (see below). The app never asks for it for this purpose.
- **Location in the background** – only if you turn on arrival notifications (see below). Android asks you to allow it in the system settings.
- **Notifications** – for expiry reminders and, if you turn them on, arrival notifications.

Photos of card sides are taken with the Google Play services document scanner, which runs on the device and returns the image directly to the app. To save typing, the app reads the text and barcode on those photos with Google ML Kit on the device (for example the card number, expiry date or the machine-readable lines of an ID card) and suggests them in the form; the recognised text is kept in memory only and is not sent anywhere.

## Card suggestions by location

Card suggestions are off unless you turn them on in Settings. When they are on:

- **When you use a card** (you show a loyalty card's code, or tap "Remember this place" on another card), the app reads your location once and the Wi‑Fi networks around you (their hardware addresses, names and signal strength). It stores them as a place of that card: at most the 10 latest locations per place.
- **While the card list is open** (at most once a minute), the app reads the Wi‑Fi networks around you and, if that doesn't settle it, your approximate location, to show the cards you used nearby at the top.
- **With arrival notifications**, the app gives Google Play services circles around your most used places. Play services watches them on your phone and tells the app when you stay at one, so it can show the right card. Google's handling of location in Play services is covered by the [Google privacy policy](https://policies.google.com/privacy).

Learned places are stored only in the app's encrypted database. They are not included in backups or in cards you share, and they are never sent to me or anyone else. You can turn suggestions off for any card, forget a card's places in its details, or forget all places in Settings. Deleting a card deletes its places. You can withdraw the location permissions in Android's settings at any time.

## The country you are in

The card list can show only the cards valid in the country you are in. To find that country, the app reads:

- the country of your mobile network, which the phone already knows; this needs no permission;
- if you have already allowed approximate location for Cardholder and the network can't tell the country, your last known approximate location. The app compares it with country outlines stored in the app itself, so the lookup works offline. Only the country code is kept.

The country is stored on your device only and is never part of a backup or a shared file. Nothing is sent anywhere. You can switch the filter off, or choose the country yourself, in the card list.

## Logo downloads

When you choose to use the official logo of a shop or bank, the app downloads the image file from Wikimedia Commons (`upload.wikimedia.org`). Like any website, Wikimedia receives your IP address and the requested file name; no card data is sent. Wikimedia's handling of this request is governed by the [Wikimedia Foundation privacy policy](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy). If you never choose an official logo, the app never connects to the internet for logos.

## Update checks

In Settings you can check for a new version of the app, and you can turn on an automatic check (off by default; at most once a week, when you open the app). Where the app checks depends on where you installed it from:

- **Google Play:** the app asks Google Play whether an update is available; Google Play downloads and installs it. This is covered by the [Google privacy policy](https://policies.google.com/privacy).
- **GitHub:** the app asks GitHub for the latest release of Cardholder (`api.github.com`) and, if you choose to update, downloads the new app file from GitHub. GitHub receives your IP address and the requested address, like any website; no card data and no information about you is sent. GitHub's handling of this request is governed by the [GitHub privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement). The downloaded file is checked against the fingerprint GitHub publishes for it, and Android only installs it if it is signed with the same key as the app you have.

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
