# 3. Authentication-bound protection with an RSA key pair

- Status: accepted
- Date: 2026-10-06

## Context
CVVs must always require biometric or device-credential authentication to view, and users can lock
any card so that *all* of its data needs authentication. A UI-only gate is not enough: the data
itself must be undecryptable without authentication. At the same time, saving a CVV or locking a
card should not force an extra prompt.

## Decision
`PROTECTED` data keys are wrapped with an **RSA-3072 OAEP key pair** in the Android Keystore:

- The private key requires user authentication (`AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL`)
  and stays usable for **30 seconds** after a successful prompt (`setUserAuthenticationParameters`).
- Wrapping uses a software copy of the **public key**, so encrypting never needs authentication.
- `setInvalidatedByBiometricEnrollment(false)`: enrolling another fingerprint must not destroy data.
- If the key is permanently invalidated (screen lock removed), the app reports `KeyInvalidated`
  instead of failing silently and creates a new key for future data.
- Authentication uses `BiometricPrompt` with strong biometrics **or** the device PIN/pattern/password,
  so no app-specific PIN has to be stored.

What is `PROTECTED`: every CVV; for locked cards also the details and both photos.
Locking needs no authentication; unlocking (re-encrypting as `STANDARD`) does.

## Consequences
- CVVs and locked cards require a secure lock screen; without one the UI disables these features.
- Decrypted data held by screens is dropped when the app goes to the background
  (`SessionLockEvents`), and the Coil memory cache of photos is cleared.
- The time-bound key avoids repeated prompts when a detail screen decrypts details, CVV and photos.
