# 5. Password-encrypted backup file

- Status: accepted
- Date: 2026-10-06

## Context
Keystore keys cannot be backed up, so Android Auto Backup cannot restore encrypted data on a new
device. Users still need a way to move their cards.

## Decision
Android backup and device transfer are disabled (`allowBackup=false`, data extraction rules). The
user can export a single `.cardholder` file through the Storage Access Framework:

```
magic "CHBK" | version:u8 | iterations:u32 | salt[16] | iv[12] | AES-256-GCM(zip(cards.json, images/*))
```

The key is derived from a user password with **PBKDF2-HMAC-SHA256 (600 000 iterations)**; the header
is authenticated as associated data. Exporting requires authentication because the file contains
CVVs and locked cards. Importing re-encrypts everything with this device's keys and can either skip
or replace cards that already exist.

## Consequences
The backup is only as strong as the user's password; the export screen requires a minimum length
and confirmation.
