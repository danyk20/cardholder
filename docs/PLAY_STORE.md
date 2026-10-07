# Publishing on Google Play

Step-by-step checklist for publishing Cardholder (`io.github.danyk20.cardholder`) with a **personal** developer account.

## 1. Developer account (one-time, done by the owner)
1. Create the account at <https://play.google.com/console/signup> → *Yourself* (personal), pay the one-time fee and verify your identity and an Android device.
2. New personal accounts must run a **closed test with at least 12 testers who stay opted in for 14 consecutive days** before production access can be requested.

## 2. Upload key and signed bundle
Follow [RELEASING.md](RELEASING.md) to create the upload keystore and the GitHub secrets, then push the tag `v1.0.0`. The *Release* workflow attaches `app-release.aab` to the GitHub release.
Opt in to **Play App Signing** (default): Google keeps the app signing key and the keystore only acts as the upload key.

## 3. Create the app
*All apps → Create app*: name **Cardholder**, default language **English (United States) – en-US**, *App*, *Free*, accept the declarations.

## 4. Store listing
Texts for 30 languages are in [`fastlane/metadata/android/`](../fastlane/metadata/android). Each locale folder holds `title.txt` (≤ 30), `short_description.txt` (≤ 80), `full_description.txt` (≤ 4000) and `changelogs/<versionCode>.txt`. Add each language under *Store listing → Manage translations → Add your own translations*.

Graphics are in [`fastlane/metadata/android/en-US/images/`](../fastlane/metadata/android/en-US/images): app icon 512 × 512, feature graphic 1024 × 500 and phone screenshots.

- App category: **Tools**. Tags: wallet, cards, loyalty.
- Contact email: required. It is shown publicly, so use an address you're happy to publish.
- Privacy policy URL: `https://danyk20.github.io/cardholder/privacy/` (source: [`docs/privacy/index.md`](privacy/index.md), published with GitHub Pages from `main` → `/docs`).

## 5. App content (Policy → App content)
| Section | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access (no login) |
| Content rating | IARC questionnaire, category *Utility, Productivity, Communication or Other*: answer **No** to everything → rated for everyone (3+/E) |
| Target audience | **18 and over** (keeps the app out of the Families programme; the app is not designed for children) |
| News app | No |
| COVID-19 contact tracing | No |
| Government app | No (the app stores ID cards but is not made by or for a government) |
| Financial features | **My app doesn't provide any financial features**. It stores card details locally; it does not make payments, lend money or provide any other financial service |
| Health | No health features |
| Data safety | See below |

### Data safety
The app itself collects nothing, but the Google ML Kit libraries (barcode scanner, document scanner, text recognition) send diagnostics to Google ([ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)), so this must be declared.

- *Does your app collect or share any of the required user data types?* **Yes**
- *Is all of the user data collected by your app encrypted in transit?* **Yes** (ML Kit uses HTTPS)
- *Do you provide a way for users to request that their data is deleted?* **No** (no account; the data is anonymous and handled by Google)
- Data types:
  | Category → type | Collected | Shared | Ephemeral | Required | Purpose |
  |---|---|---|---|---|---|
  | App info and performance → **Diagnostics** | Yes | No | No | Required | Analytics |
  | Device or other IDs → **Device or other IDs** | Yes | No | No | Required | Analytics |
- Not collected: personal info, financial info (card details never leave the device), photos (images are processed on-device), location, contacts, app activity, web browsing.

Logo downloads from Wikimedia Commons don't send user data. They are user-initiated requests for public files, so they aren't declared.

## 6. Testing tracks
1. **Internal testing**: upload the AAB, add yourself, install it from Play and smoke-test it, especially the release-only paths (document scanner, barcode scanner, NFC).
2. **Closed testing**: create an email list of **at least 12 testers** and send them the opt-in link. They must stay opted in for **14 days**. Collect feedback and ship fixes as new versionCodes.
3. Apply for **production access** (*Dashboard*) and answer the questions about the closed test.
4. **Production**: roll out at a staged percentage (for example 20 %), then 100 %.

## 7. Each update
Bump `versionCode`/`versionName`, add `fastlane/metadata/android/*/changelogs/<versionCode>.txt`, update `CHANGELOG.md`, tag, upload the new AAB.
