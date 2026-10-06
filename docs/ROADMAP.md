# Roadmap

## Goal
An Android app that stores **bank cards**, **ID cards** and **loyalty cards**. Every card has two sides
that can *optionally* be captured with the camera.
- **Bank card**: card number, CVV, expiry, card holder.
- **ID card**: issuing country.
- **Loyalty card**: shop and barcode/QR code; tapping the card shows the code full-screen for scanning.

Product decisions:
- CVV is stored encrypted and **hidden by default**; it is revealed only after biometric/PIN/password.
- Cards are visible without protection by default. Any card can be **locked**; after unlocking, all its
  data (including the CVV) is visible.
- Shops come from a bundled catalogue, with "Other" for a custom name.
- v1 includes camera barcode scanning and an encrypted backup export/import.

## Milestones (v1.0.0)
| # | Milestone | Status |
|---|---|---|
| M0 | Scaffolding: Gradle, convention plugins, modules, CI, static analysis, docs | ✅ |
| M1 | Domain: models, validators, use cases | ✅ |
| M2 | Data & security: Room + SQLCipher, Keystore envelope encryption, image store, repositories | ✅ |
| M3 | Card list & editor | ✅ |
| M4 | Card detail & full-screen barcode | ✅ |
| M5 | Scanning: document scanner, live barcode scanner, barcode from photos | ✅ |
| M6 | Protection: secure dialogs, ADRs | ✅ |
| M7 | Encrypted backup & settings | ✅ |
| M8 | Polish & release: R8 verification, docs, screenshots, release process | ✅ |

## Changes compared to the original plan
| Planned | Implemented | Why |
|---|---|---|
| Google Tink for file encryption | JCE AES-GCM **envelope encryption** with Keystore-wrapped data keys | Same guarantees with no extra dependency; one format for values and files ([ADR 2](adr/0002-encryption-at-rest.md)). |
| Auth-bound AES key + in-memory `UnlockSession` | **RSA-OAEP key pair**: encrypt with the public key, decrypt only within 30 s of authentication; decrypted data dropped on background | Saving a CVV or locking a card needs no prompt, and protection is cryptographic ([ADR 3](adr/0003-auth-bound-keys.md)). |
| Coil with custom fetcher | As planned, with **disk cache disabled** and memory cache cleared on background | Never write decrypted photos to disk. |
| Live scanner in a dialog | Full-screen composable in the editor window | Keeps the window's `FLAG_SECURE` and avoids SurfaceView layering issues. |
| Drag-to-reorder cards | Sorted alphabetically with search and type filters | Deferred to the backlog. |

## Backlog (post-v1)
- Drag-to-reorder and favourites / most-used loyalty cards first.
- OCR prefill of card number and expiry from the front photo.
- Home-screen widget / app shortcuts for favourite loyalty cards.
- Optional app-wide lock on open.
- Screenshot tests (Roborazzi) for key screens in light/dark.
- Room migration tests once the schema reaches version 2.
- ABI splits for GitHub APKs; more languages; Wear OS tile.
