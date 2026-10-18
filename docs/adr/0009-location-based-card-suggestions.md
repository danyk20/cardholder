# 9. Suggesting cards by where they were used

- Status: accepted
- Date: 2026-10-18

## Context
With many loyalty cards, finding the right one at the till takes time. The app could suggest the card
used before at the same shop. That needs location and Wi‑Fi data, which this offline, privacy-first app
never used, and it must not cost noticeable battery.

## Decision
- **Opt-in, three modes** in Settings: *Off* (default), *When I open the app*, *Also notify when I
  arrive*. Each card has its own *Suggest by location* switch, on by default for loyalty cards and off
  for bank and ID cards.
- **Learning.** Showing a loyalty card's code at the till is a use; bank and ID cards learn only through an
  explicit *Remember this place* button (opening details at home must not teach the app the home). One
  use stores the Wi‑Fi networks around (BSSID, name, strength) and one location fix.
- **Places.** A use joins a place of the same card that shares an access point or is within 150 m,
  otherwise it becomes a new place. A card can have any number of places; each place keeps its **10
  latest fixes** (oldest dropped first), so its accuracy-weighted centre improves with use and follows a
  shop that moved, and its 20 most seen access points. Uses within 15 minutes are one visit. Access points
  seen more than 1 km apart travel (buses, trains, hotspots) and are ignored from then on.
- **Suggesting in the app.** While the card list is on screen, at most once a minute: Wi‑Fi first (the
  system's recent scan results are free; otherwise one scan). Access points of a place, a network name
  seen at several branches of a chain, or a network named after the card's shop (whole words: "dm" in
  "dm-WLAN", not in "admin") suggest a card. Only if Wi‑Fi isn't conclusive, one balanced-power fix
  (Wi‑Fi and mobile network, not GPS) finds places within 1 km. At most three cards, above the others.
- **Arrival notifications** use Google Play services geofences (DWELL after 90 s, 3 min responsiveness,
  100 most used places, radius 150–300 m), so the app only runs when the user stays at a place. One
  notification at a time with cooldowns; "Not this place" forgets the place. They need location all the
  time, which Android grants only in the system settings after an in-app disclosure.
- **Sensors** are read with the framework `LocationManager` and `WifiManager`, so suggestions in the app
  work without Google Play services; only arrival notifications need it (as in ADR 6).
- **No Bluetooth or mobile cell data**: Bluetooth addresses are randomised and few shops run beacons, an
  extra permission and active scans would cost battery for little gain; cell towers only narrow it down
  to kilometres and are already used by network positioning.
- **Privacy.** Places are stored only in the encrypted database (ADR 2), never in backups or shared files,
  and can be forgotten per card or all at once; deleting a card deletes its places.

## Consequences
The app requests location and Wi‑Fi permissions, and (for arrival notifications) background location,
which Google Play reviews separately; if it's rejected, the permission is removed from the manifest and the
option disappears. Precise location is required: Wi‑Fi scan results aren't available with approximate
location. The emulator offers a single Wi‑Fi network, so Wi‑Fi matching is mostly verified by unit tests
and on real devices.
