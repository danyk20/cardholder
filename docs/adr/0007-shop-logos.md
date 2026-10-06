# 7. Official shop and bank logos on request

- Status: accepted
- Date: 2026-10-06

## Context
Cards of known shops should show the shop's logo instead of only a colour. Bundling brand logos in a public
Apache-licensed repository raises copyright and trademark questions, and the app had no network access.

## Decision
- The shop catalogue stores, per shop, a **link to a freely licensed logo on Wikimedia Commons** (public
  domain or CC BY-SA, with the required attribution), its licence and its source page. 51 of 55 shops have
  one; the repository contains no logo files. Sources are listed in [LOGOS.md](../LOGOS.md).
- When the user picks a catalogue shop with a logo, the app asks: **use official logo**, **upload my own**,
  or **keep blank**. Custom shops can upload a logo or stay plain.
- Downloading requires the `INTERNET` permission. It is used by exactly one component, `HttpsLogoDownloader`:
  HTTPS only (cleartext disabled by the network security config), only the Wikimedia media hosts, no
  redirects, image content type and a 2 MB limit. Nothing is sent except the request for the logo.
- Logos are stored like photos (encrypted, `STANDARD` protection) as PNG to keep transparency, and are
  included in backups. Database version 2 adds the logo column through a Room auto-migration.

## Consequences
The app is no longer "no network at all", and the privacy statements say so precisely. Logos stay
trademarks of their owners and are only used to identify the shop a card belongs to.

## Addendum: banks and card networks
- A bank catalogue (145 banks, 128 with a freely licensed logo) uses the same mechanism: link, licence and
  source page in `banks.json`, download only on *Use official logo*.
- The eight card network logos (Visa, Mastercard, American Express, Maestro, Discover, JCB, UnionPay,
  Diners Club) are **public domain** and small, so they are bundled as drawables and shown automatically,
  offline, based on the detected network. Credits for all logos are in [LOGOS.md](../LOGOS.md).
