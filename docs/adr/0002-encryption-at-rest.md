# 2. Encryption at rest: SQLCipher + envelope encryption

- Status: accepted
- Date: 2026-10-06

## Context
Card numbers, CVVs, ID numbers, loyalty codes and card photos must never be stored in plain text.
`androidx.security:security-crypto` is deprecated. Card photos are files, not database rows.

## Decision
- The Room database is encrypted as a whole with **SQLCipher**. Its random 256-bit passphrase is
  stored in `noBackupFilesDir`, sealed with a Keystore key.
- On top of that, every sensitive value and every photo is **envelope-encrypted** (`EnvelopeCipher`):
  a fresh AES-256-GCM data key per message, wrapped by a long-lived Android Keystore key. The header
  (format version, protection level) is authenticated as associated data.
- Two protection levels exist: `STANDARD` (Keystore AES-GCM key, usable without user interaction)
  and `PROTECTED` (see [ADR 3](0003-auth-bound-keys.md)).
- Photos are normalized (EXIF rotation, ≤ 2048 px, JPEG) before encryption; temporary unencrypted
  scanner output in the cache directory is deleted after storing.
- No custom crypto primitives; only JCE AES-GCM, RSA-OAEP and the platform Keystore.

## Consequences
- Data is unreadable without the device's Keystore, so an extracted database or image file is useless.
- Keystore keys never leave the device: Android cloud backup is disabled and the encrypted export
  ([ADR 5](0005-encrypted-backup.md)) is the only migration path.
- Double encryption of database values costs one Keystore operation per read; negligible for the
  data sizes involved.
