# Security Policy

Cardholder stores sensitive data (payment card numbers, CVVs, ID documents). Security reports are taken seriously.

## Reporting a vulnerability
Please report vulnerabilities **privately** through
[GitHub Security Advisories](https://github.com/danyk20/cardholder/security/advisories/new).
Do not open a public issue. You can expect an initial response within 7 days.

## Threat model (summary)
| Threat | Mitigation |
|---|---|
| Device lost/stolen while locked | All data encrypted at rest (SQLCipher DB, encrypted image files) with keys in Android Keystore (TEE/StrongBox). |
| Someone picks up an **unlocked** phone | CVV always needs biometric / device credential; any card can be individually locked so *all* its data needs authentication. The prompt is always shown in the app before protected data is decrypted: the Keystore key stays usable for 30 s after *any* authentication, including unlocking the phone, so the app never relies on that window alone. Decrypted data is discarded, and a new prompt is required, when the app goes to the background. |
| Screenshots, screen recording, recents thumbnail | `FLAG_SECURE` on all screens and dialogs that show sensitive data. The user can allow screenshots in Settings; that permission is only kept in memory and is revoked as soon as the app leaves the screen. While it is on, the recents thumbnail is disabled (Android 13+); on older versions it is revoked when the app is paused. |
| Decrypted photos lingering in caches | Image loader disk cache disabled; memory cache cleared when the app goes to the background; temporary scanner output deleted after encryption. |
| Malicious backup files | Authenticated encryption (AES-GCM over header and content), zip path-traversal and size guards. |
| Cloud/ADB backups leaking data | Android backup and device transfer are disabled; the only export path is a password-encrypted backup file (AES-256-GCM, PBKDF2-HMAC-SHA256). |
| Card data read over NFC | Read-only EMV commands, reader mode only while the read dialog is open, data kept in memory and put straight into the (FLAG_SECURE) form. |
| Clipboard sniffing | Copied values are marked sensitive and cleared automatically. |
| Network exfiltration | The only network code downloads a shop logo on explicit request: HTTPS only (cleartext disabled), restricted to the Wikimedia media hosts, no redirects, image content type and 2 MB size limit. No card data is ever sent. |

See [docs/adr](docs/adr) for the detailed design decisions.

## Out of scope
Rooted/compromised devices, and attacks that require the device credential or an enrolled biometric.
