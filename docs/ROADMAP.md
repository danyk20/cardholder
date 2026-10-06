# Cardholder — Android app plan

## Context
Build a new Android app (**Cardholder**) to store three kinds of cards on the phone: **bank cards**, **ID cards** and **loyalty cards**. Every card has a front and back side that can *optionally* be captured with the camera. Type-specific data:
- **Bank card** – card number, CVV, expiry, card holder (manual entry).
- **ID card** – country (picked from a list).
- **Loyalty card** – shop (from a bundled list or custom) + barcode/QR code; tapping the card shows the code full-screen so a shop can scan it.

Decisions confirmed with you:
- **CVV** is stored encrypted, **hidden by default**, revealed only after biometric/PIN/password auth.
- **Per-card lock (opt-in)**: by default cards are visible without protection. A user can lock any card; after unlocking, *all* its data (incl. CVV) is visible.
- **Shops**: bundled, searchable list + "Other" (custom name).
- **v1 scope also includes**: barcode scanning from camera, encrypted backup export/import.
- **GitHub repo**: `danyk20/cardholder`, **public** (Apache-2.0 license).

Environment today: `/Users/danielkosc/Documents/Projects/cards` is empty; `gh` is logged in as `danyk20`; the repo name is free; JDK 17 is installed; **no Android SDK / Android Studio is installed** (see Prerequisites).

---

## 1. Tech stack (latest stable versions pinned at setup time)
| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x, Coroutines + Flow | Android standard |
| UI | Jetpack Compose, Material 3 (dynamic color, dark mode) | Modern declarative UI |
| Architecture | Clean Architecture + MVVM with unidirectional data flow (`UiState` / `UiEvent` / one-off effects) | Testable, scalable |
| DI | Hilt | Official, compile-time checked |
| Navigation | Navigation Compose, type-safe `@Serializable` routes | Official |
| Persistence | Room + **SQLCipher** (whole DB encrypted) | Encrypted at rest |
| Preferences | DataStore | Replaces SharedPreferences |
| Crypto | Android Keystore (AES-256-GCM) + Google **Tink** for file streaming encryption | Hardware-backed keys; `security-crypto` is deprecated |
| Auth | `androidx.biometric` `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (fingerprint/face **or** device PIN/password/pattern) | No custom PIN storage needed |
| Card side capture | **ML Kit Document Scanner** (edge detection, crop, no camera permission) + Photo Picker fallback | Best UX, minimal permissions |
| Barcode scanning | ML Kit Barcode Scanning (bundled, offline) + CameraX live preview; also decode from captured back image | Works without Play Services for decoding |
| Barcode rendering | ZXing `core` (QR, EAN-13/8, UPC-A/E, Code 128/39/93, ITF, Codabar, PDF417, Aztec, Data Matrix) | Pure JVM, battle-tested |
| Images | Coil (custom fetcher that decrypts files) | Compose-native |
| Serialization | kotlinx.serialization | Routes, backup, payload blobs |
| Build | Gradle KTS, version catalog `libs.versions.toml`, **convention plugins** in `build-logic` | DRY, consistent modules |
| Quality | Spotless + ktlint, detekt, Android Lint (warnings as errors), Kover coverage | Enforced in CI |
| Testing | JUnit, Turbine, kotlinx-coroutines-test, MockK (sparingly; prefer fakes), Robolectric, Compose UI tests, Roborazzi screenshot tests, Room migration tests | |
| SDK levels | `minSdk 30`, `target/compileSdk` = latest | API 30+ makes `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` + `setUserAuthenticationParameters` work uniformly (~95% of active devices) |

Package / applicationId: `io.github.danyk20.cardholder`.

## 2. Module structure (Now-in-Android style)
```
cardholder/
├─ app/                      # Application, MainActivity, NavHost, app-level DI, re-lock on background
├─ build-logic/convention/   # android-application, android-library, compose, hilt, feature, jvm-library plugins
├─ core/
│  ├─ model/                 # Pure Kotlin: Card sealed hierarchy, value classes, BarcodeFormat, Shop, Country
│  ├─ domain/                # Repository interfaces, use cases, validators (Luhn, expiry, EAN checksum…)
│  ├─ data/                  # Repository impls, mappers, shops.json loader, backup service
│  ├─ database/              # Room + SQLCipher, entities, DAOs, migrations, exported schemas
│  ├─ security/              # KeyManager, CipherService, Authenticator (BiometricPrompt), UnlockSession
│  ├─ storage/               # Encrypted image file store
│  ├─ scanning/              # DocumentScanner + BarcodeScanner/Decoder abstractions & ML Kit impls
│  ├─ barcode/               # ZXing barcode → ImageBitmap renderer
│  ├─ designsystem/          # Theme, typography, CardSurface (ID-1 ratio 1.586), icons
│  ├─ ui/                    # Shared composables (CardTile, SecretField, FlagSecure effect…)
│  └─ testing/               # Fakes, test rules, test dispatchers
└─ feature/
   ├─ cardlist/              # Home: list/grid, search, type filter chips
   ├─ cardeditor/            # Add/Edit flow (type → optional scan → form)
   ├─ carddetail/            # Detail, flip front/back, reveal CVV, lock/unlock, delete
   ├─ barcodefullscreen/     # Full-screen code for loyalty cards
   └─ settings/              # Backup export/import, theme, about/licenses
```
Dependency rule: `feature → domain → model`; `data` implements `domain`; features never depend on each other (navigation wired in `app`).

## 3. Domain model (`core/model`)
```kotlin
sealed interface Card {
    val id: CardId; val title: String; val sides: CardSides   // front/back ImageRef?, both optional
    val isLocked: Boolean; val color: CardColor; val createdAt: Instant; val updatedAt: Instant
}
data class BankCard(..., val number: CardNumber, val cvv: Cvv?, val expiry: YearMonth, val holder: String) : Card
    // network (Visa/MC/Amex/…) derived from IIN prefix, not stored
data class IdCard(..., val country: CountryCode /* ISO 3166-1 alpha-2 */, val documentNumber: String?, val expiry: LocalDate?) : Card
data class LoyaltyCard(..., val shop: ShopRef /* Known(id) | Custom(name) */, val code: String, val format: BarcodeFormat) : Card
```
- **Validators** (pure, 100% unit-tested): Luhn, card-number length per network, expiry not malformed (warn if past), CVV 3 digits (4 for Amex), EAN-13/8 & UPC checksums, format-specific allowed charsets.
- **Countries**: `Locale.getISOCountries()` + localized display name + flag emoji (no bundled data).
- **Shops**: `core/data/src/main/assets/shops.json` (`id`, `name`, `brandColor`, `countries`, `defaultFormat`). No trademarked logos in the public repo — render initials on brand color.

## 4. Security design (`core/security`)
Two protection layers:
1. **At rest**: Room DB encrypted with SQLCipher; passphrase randomly generated, wrapped by a Keystore AES key (no user-auth needed). Card images encrypted with Tink streaming AEAD (keyset wrapped by Keystore).
2. **Auth-bound secrets**: a second Keystore key `PROTECTED` created with `setUserAuthenticationParameters(timeout ≈ 30 s, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)`. Data encrypted with it can only be decrypted after `BiometricPrompt` succeeds — real cryptographic protection, not just a UI gate.
   - **CVV** is always encrypted with `PROTECTED`.
   - **Locked card**: its whole details payload (number, expiry, holder / code / document number) and its images are encrypted with `PROTECTED`; the list shows only title, type, shop/country and a lock badge.
   - Lock / unlock toggles re-encrypt payload + images (requires auth).
- **Storage shape**: `cards` table keeps non-sensitive, queryable columns (id, type, title, color, country_code, shop_id/shop_name, barcode_format, is_locked, sort_order, timestamps, image refs) + `details_blob` (serialized type-specific details; encrypted with PROTECTED if locked) + `cvv_blob` (always PROTECTED).
- `UnlockSession`: in-memory set of unlocked card ids; cleared when the app goes to background (`ProcessLifecycleOwner`) and after a timeout.
- Hardening: `FLAG_SECURE` on detail/barcode/editor screens, clipboard copies flagged `EXTRA_IS_SENSITIVE` and auto-cleared, `allowBackup=false` + data-extraction rules excluding DB/keys (Keystore keys can't be cloud-restored anyway — the encrypted export is the backup path), R8 enabled, no INTERNET permission.
- Edge cases handled: no screen lock set → lock/CVV features prompt user to set one (`ACTION_BIOMETRIC_ENROLL`); `KeyPermanentlyInvalidatedException` (screen lock removed) → explain that protected data is unrecoverable and suggest restoring a backup.

## 5. Features & UX flows
1. **Card list (home)** – cards rendered as real card shapes (front photo thumbnail or generated colored card), search by title/shop, filter chips (All / Bank / ID / Loyalty), drag-to-reorder, empty state, FAB "Add card".
   - Tap **loyalty** card → straight to full-screen code (per spec). Long-press / ⋮ → detail.
   - Tap **bank/ID** card → detail. Tap a **locked** card → BiometricPrompt first.
2. **Add/Edit card** (one ViewModel, multi-step):
   1. Choose type (Bank / ID / Loyalty).
   2. *Optional* "Scan front" / "Scan back" via Document Scanner (or pick from gallery) — clear **Skip** button.
   3. Type-specific form with inline validation:
      - Bank: number (auto-grouped, network icon), expiry MM/YY, CVV (masked), holder.
      - ID: country picker (searchable, flags), optional document number & expiry.
      - Loyalty: shop picker (bundled list + "Other"), code value + format; **"Scan barcode"** (CameraX + ML Kit) auto-fills both; also auto-detect a barcode from the captured back image and offer to prefill.
   4. Option "Lock this card", title & color, Save.
3. **Card detail** – flip animation front/back, full-screen image zoom, fields with copy buttons, CVV shown as `•••` with "Reveal" (auth), lock/unlock toggle, edit, delete with confirm + undo snackbar.
4. **Full-screen barcode** – white background, max screen brightness, keep-screen-on, code rendered at full width (linear codes rotated landscape for readability), human-readable value underneath, brightness restored on exit.
5. **Settings** – Export backup / Import backup, theme (system/light/dark), open-source licenses, about.
6. **Backup** – Storage Access Framework (`CreateDocument`/`OpenDocument`, no storage permission). File `*.cardholder` = versioned header (magic, version, salt, KDF params, IV) + AES-256-GCM encrypted zip (`cards.json` + images). Key from user password via PBKDF2-HMAC-SHA256 (≥600k iterations). Export requires auth (it contains CVVs/locked data). Import: validate, show summary, choose *skip duplicates* or *replace*.

## 6. Code quality & conventions
- Clean code: small single-purpose classes, immutable `UiState` data classes, stateless composables + state hoisting, `Result`/sealed error types instead of exceptions crossing layers, injected `CoroutineDispatcher`s, no Android types in `model`/`domain`.
- All strings in resources (English first; ready for localization), content descriptions for accessibility, min 48dp touch targets, TalkBack-friendly secret fields.
- Room schemas exported to `core/database/schemas/` and migration-tested from day one.
- KDoc on public APIs of core modules; ADRs in `docs/adr/` for key decisions (SQLCipher, auth-bound keys, minSdk 30, ML Kit).

## 7. Testing strategy
- **Unit (JVM)**: validators, use cases, mappers, ViewModels (fakes + Turbine), backup format round-trip, barcode renderer output for each format.
- **Robolectric**: DAOs, Compose screens, screenshot tests (Roborazzi) in light/dark.
- **Instrumented**: Keystore/crypto round-trips, SQLCipher open/migrate, end-to-end add → view → barcode flow.
- Coverage goal via Kover: ≥80% on `core:domain`/`core:data`/ViewModels.

## 8. GitHub repo & CI/CD
- `gh repo create danyk20/cardholder --public --license apache-2.0`; local repo lives in `/Users/danielkosc/Documents/Projects/cards`.
- Files: `README.md` (features, screenshots, architecture diagram, build instructions), `LICENSE`, `SECURITY.md` (threat model + how to report), `CONTRIBUTING.md` (Conventional Commits, branch naming), `.editorconfig`, Android `.gitignore`, PR & issue templates, `docs/ROADMAP.md` (this plan), `docs/adr/`.
- **GitHub Actions**:
  - `ci.yml` on PR/push: Spotless, detekt, Lint, unit + Robolectric tests, Kover report, `assembleDebug`, upload artifacts.
  - `instrumented.yml` (nightly / on label): emulator tests via `android-emulator-runner`.
  - `release.yml` on tag `v*`: signed release APK/AAB (keystore from repo secrets) → GitHub Release with changelog.
- Dependabot (Gradle + Actions), branch protection on `main` (PR + green CI required), GitHub milestones/issues mirroring §9.

## 9. Delivery milestones (one PR per milestone, each green in CI)
| # | Milestone | Done when |
|---|---|---|
| M0 | Repo & scaffolding: Gradle, version catalog, convention plugins, all modules, Hilt, theme, empty NavHost, CI, linters, docs | Empty app builds & runs, CI green |
| M1 | Domain: models, validators, use cases + tests | 100% validator coverage |
| M2 | Data: Room+SQLCipher, Keystore/Tink crypto, image store, repositories, shops.json | DAO/migration/crypto tests pass |
| M3 | Card list + Add/Edit (manual entry, all 3 types) | Can create/edit/delete every card type |
| M4 | Card detail + full-screen barcode (all formats) | Loyalty code scannable from screen |
| M5 | Camera: Document Scanner for sides, live barcode scan, decode from image | Optional scan flow works, skip works |
| M6 | Security: CVV reveal, per-card lock/unlock, UnlockSession, FLAG_SECURE, clipboard | Auth required where specified |
| M7 | Encrypted backup export/import | Round-trip restores everything |
| M8 | Polish & release: a11y, empty/error states, screenshots tests, R8 rules, README screenshots, `v1.0.0` release | Signed release on GitHub |

Backlog (post-v1): OCR prefill of card number from front image, home-screen widget/shortcuts for favourite loyalty cards, Wear OS, more languages, cloud sync.

## Prerequisites before coding
- Install Android SDK: Android Studio (recommended for you to run/debug) or `brew install --cask android-commandlinetools` + `sdkmanager "platform-tools" "platforms;android-<latest>" "build-tools;<latest>"` and an emulator image. I'll do the CLI route if you approve the brew install; Gradle toolchains will provision JDK 17/21 as needed.

## Verification
- Every milestone: `./gradlew spotlessCheck detekt lint testDebugUnitTest koverVerify assembleDebug` locally and in GitHub Actions.
- Instrumented: `./gradlew connectedDebugAndroidTest` on an emulator (API 30 and latest).
- Manual E2E on emulator/device: add each card type with & without scanning; scan a real loyalty barcode and verify a shop/another phone can read the full-screen code; verify CVV needs auth; lock a card, background the app, confirm it re-locks; export backup, wipe app data, import, confirm all cards/images restored.

## What happens right after approval
1. Install Android command-line tools (needs your OK for `brew`).
2. `git init` in the project folder, create public `danyk20/cardholder`, commit this plan as `docs/ROADMAP.md`, create milestones M0–M8.
3. Implement M0, push, open PR, and continue milestone by milestone.
