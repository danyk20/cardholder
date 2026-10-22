# 12. Showing a card's places on a map

- Status: accepted
- Date: 2026-10-22

## Context
Card suggestions by location learn a card's places from location fixes, which can be a few hundred metres off.
A place can end up in the wrong spot, and then suggestions and arrival notifications fire in the wrong area or
never fire. The user needs to see where each place is, move a wrong one to the right spot, delete one, or add a
place they have never been to by tapping the map.

## Decision
- The places of a card are shown on a map, drawn with **OpenStreetMap** raster tiles, and in a list, on a Places
  screen opened from the card's "Suggest by location" section.
- The map is drawn with **MapCompose** (`ovh.plrapps:mapcompose`, Apache License 2.0). It is pure Kotlin and
  Compose, with no native code and no permissions. Only the `core:map` module uses it, and its public API uses
  domain types, so the library could be replaced without touching the features.
- Tiles are fetched by our own small HTTP code, not the library's:
  - HTTPS only, to `tile.openstreetmap.org`, with no redirects and no cookies;
  - a User-Agent of `Cardholder/<version> (+https://github.com/danyk20/cardholder)`, as the
    [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) requires;
  - at most two downloads at a time, a PNG of at most 512 KB, 10 second timeouts;
  - a disk cache in the app's cache folder, capped at about 50 MB and evicted least recently used, refreshed after
    seven days, and a stale tile shown when the server can't be reached.
- The map is attributed "© OpenStreetMap contributors", as the ODbL licence requires.
- Moving a place **replaces its learned fixes with the user's pin** and marks it **pinned**. A pinned place doesn't
  drift: later visits still count and still add their Wi‑Fi networks, but never move it. A pin gets an accuracy of
  25 m, so its geofence is the smallest allowed (150 m).
- Moving keeps the place's **Wi‑Fi access points**. They were seen at the real place, and only the GPS position was
  wrong.
- Adding a place creates a pinned place with one fix at the tapped spot.

## Alternatives
- **osmdroid**: the first choice, but its GitHub repository has been archived since 2024, with no fixes since
  November 2024. Rejected.
- **MapLibre**: maintained and fully open, but it ships native libraries, adding size and a native build concern for
  a few markers. Possible later, behind the same `core:map` API.
- **Google Maps**: needs an API key and Google Play services, which the GitHub build doesn't have and the app avoids.
  Rejected.

## Consequences
- The Places screen needs the network to draw the map. Without it, cached tiles or a blank map are shown, with a
  hint, and the list and every action keep working.
- The app's data never leaves the device except as tile requests, which reveal the IP address and the area viewed.
  Documented in the privacy policy.
- No new permissions: the merged manifest still has only INTERNET (and the existing permissions).
- The screen's map is a slot, so the screen can be tested with a stand-in map and no network.
