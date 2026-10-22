# 11. Where a card is valid, and filtering the list by the current country

- Status: accepted
- Date: 2026-10-22

## Context
People who carry cards from several countries (a Swiss and a German loyalty card, an ID card, a local
transit card) only care about the cards that work where they are now. Each card needs a validity: valid
everywhere (the default) or valid in a set of countries. The card list and the home-screen widget then show
the cards valid in the current country, and the user can switch that off.

The current country must be found without noticeable delay or battery cost, without asking for new
permissions, and without sending anything off the phone.

## Decision

### Validity
- A card has `CardValidity`: `Everywhere`, or `Countries` with a non-empty set of ISO codes. An empty
  selection always means `Everywhere`.
- Stored in one column, `valid_countries`, as sorted codes separated by commas (`AT,CH,DE`). Database
  version 7 adds it; existing cards become `Everywhere`. A join table was rejected: validity is always
  loaded with its card, filtering runs over a small list in Kotlin, and no query searches by country.
- Invalid codes are dropped when read (from the database, or from a backup file), and a file never fails
  to import because of them. Only canonical upper-case codes count, so a lower-case code is dropped too.
  Codes that are well formed but not ISO (such as `XK`) are kept and named by their code.
- Validity is not sensitive, like the title, so it is shown without unlocking the card. It is part of
  backups and shares, since it describes the card, not the user. Shares keep it even though favourites are
  left out.
- An unknown country never hides a card.
- Region shortcuts (EU, Schengen, Eurozone, EEA) only fill in member lists. Their membership is hard-coded
  as of 2026-10-22 in `core/model` and checked by a test.

### Detecting the current country
Tried in order, the first that gives a country is used:
1. **The mobile network's country** (`TelephonyManager.getNetworkCountryIso`, with the default SIM and then
   the other SIM slots). It needs no permission, costs essentially no battery and answers in under a
   millisecond, without internet or GPS.
2. **The approximate location**, but only when location permission is *already* granted, and only with
   the last known network location no older than six hours (one network-only reading if there is none).
   The point is mapped to a country with bundled, offline borders. This feature never asks for a
   permission, and never uses GPS.
3. **The country detected last time**, however old, marked as "last known".
4. **Unknown**, which hides nothing. This is only possible before anything was ever detected.

The location is looked up at most once per 30 minutes (kept in memory, so a restart may look again). The
network is read every time, since it is cheap. Detection runs only when the card list is shown and when the
widget's refresh button is pressed. There is no background work, polling, or broadcast receiver.

Why the network comes first: GPS needs a permission, takes seconds to fix and drains the battery, which is
overkill for something that changes only when you cross a border. Wi-Fi on its own cannot give a country:
the network name says nothing about the country, and Wi-Fi positioning is only a network location.

Known weak spots, covered by the fallbacks and the manual choice: airplane mode, phones without a SIM,
tablets, and border towns where the phone roams onto the neighbour's network.

### Offline borders instead of a geocoder
- The outlines come from Natural Earth admin-0 countries, 1:50m (public domain), simplified to about 100 m
  and stored in `core/location/src/main/assets/country_borders.bin` (about 376 KB). The 1:110m data is too
  coarse for places such as Basel or Geneva; 1:10m is too large.
- The build script `tools/country-borders/build_borders.py` uses `ISO_A2_EH`, not `ISO_A2`, because `ISO_A2`
  is `-99` for France, Norway and Kosovo. It is stdlib-only and not part of the Gradle build.
- Lookup: even-odd point-in-polygon over the country's bounding boxes, so holes work. A point just off a
  simplified coastline (a harbour, a beach) takes the country of the nearest edge within 25 km. Farther out
  it is no country.
- Android's Geocoder was rejected: it needs network access and sends the coordinates to a service.
- Disputed areas follow Natural Earth's default view. The manual choice covers what it gets wrong.

### Manual choice and when it expires
- The user can pick a country (or go back to automatic) from the card list. A choice is stored with the
  country that was detected when it was made (its basis).
- A choice is dropped only when a *fresh* detection gives a different country from its basis. Cached
  results and unknown results never drop it, so a manual choice survives a flaky network.
- The detected country and the choice are kept in their own DataStore, `country_state`, which is device
  state, not a setting. It is not part of backups (the same rule as learned places).
- The filter switch itself is a setting (`countryFilterEnabled`, default on), kept in `user_preferences`.

### Where it applies
- **Card list**: the cards valid in the current country, plus a footer "N cards for other countries are
  hidden · Show all". Search always looks through every card. The nearby section is not filtered. The row
  with the country chip only appears once a card is restricted to some countries.
- **Widget**: the loyalty cards valid in the current country. The app redraws the widget when the country
  is detected again or the switch changes, and its refresh button detects the country before sorting by
  location.
- **App shortcuts**: unchanged. They don't depend on where the user is.

## Consequences
- A card valid in exactly one country shows its flag and name. A card valid in several shows a globe; its
  full list is shown on the details screen. The large issuing-country flag of an ID card is unchanged: the
  issuing country and the countries where the card is accepted are separate facts.
- **Widget limitation**: the widget only updates when the app detects a country again. If the user travels
  without opening the app or pressing the widget's refresh button, the widget keeps the previous country.
- Border towns: a phone roaming onto the neighbour's network reports the neighbour's country. The user can
  correct it in the card list.
- Dual SIM with different countries: the default SIM is tried first, then the other slots.
- An older app reading a newer backup ignores the new key (`ignoreUnknownKeys`), so the cards import as valid
  everywhere.
- The borders file must be regenerated when the Natural Earth release changes; `tools/country-borders/README.md`
  records the source and the format.
- The APK grows by about 376 KB for the asset.
