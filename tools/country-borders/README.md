# Country borders asset

## Purpose

`country_borders.bin` is an offline point-to-country lookup. The app uses it as a
fallback when the phone's network country is unknown. It is generated from public
data at build time by `build_borders.py`; nothing is fetched at runtime.

## Source

- Dataset: Natural Earth, admin-0 countries, 1:50m (`ne_50m_admin_0_countries.geojson`)
- URL: https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson
- Version: Natural Earth v5.1.1. The GeoJSON has no version field; the version comes from the
  latest commit touching the file in the `nvkelso/natural-earth-vector` repo
  (commit `9380cca`, "v5.1.1 DBF var char length fix", 2022-05-13).
- License: public domain.
- Attribution: "Natural Earth, naturalearthdata.com, public domain"

## Country code property

The code is read from `ISO_A2_EH`, not `ISO_A2`. `ISO_A2` is `-99` for France, Norway
and Kosovo, which would drop them. Features whose `ISO_A2_EH` is not exactly two upper-case
ASCII letters are skipped. In this release that skips Somaliland, Northern Cyprus and
Siachen Glacier (3 features). Several features may share one code; all their polygons are
merged under that code.

## Processing

- Coordinates are quantized to integers in units of 1e-4 degree (about 11 m).
- Simplification: Douglas-Peucker per ring, tolerance 0.001 degree (about 100 m), named
  `SIMPLIFY_TOLERANCE_DEG` in the script. Each ring is simplified as a closed loop, with its
  first point as both ends. Then the ring is quantized.
- Consecutive duplicate points are removed. A ring with fewer than 3 distinct points is dropped.
  A polygon whose outer ring is dropped is dropped with its holes. A country with no polygons
  left is omitted.
- Output order: countries sorted by code, then polygons and rings in input order.

## Regenerate

From the repository root:

```
python3 -I tools/country-borders/build_borders.py <input.geojson> <output.bin>
```

Standard library only (Python 3). The optional `--tolerance <degrees>` flag overrides the
simplification tolerance for size tuning. The output path's parent directories are created
if missing.

Current output: `core/location/src/main/assets/country_borders.bin`

## Binary format

All integers are unsigned LEB128 varints unless marked zigzag. Zigzag: a signed value `v`
is stored as `(v << 1) ^ (v >> 63)` (64-bit), then as an unsigned varint. Decode with
`(u >>> 1) ^ -(u & 1)`. All coordinates are in 1e-4 degree units.

| Field | Type | Notes |
|---|---|---|
| magic | 4 bytes | ASCII `CHCB` |
| version | 1 byte | `0x01` |
| countryCount | varint | |
| *for each country, ascending code* | | |
| code | 2 bytes | ASCII upper-case letters, e.g. `CH` |
| minLat | zigzag varint | bbox over all rings of the country |
| minLon | zigzag varint | |
| maxLat | zigzag varint | |
| maxLon | zigzag varint | |
| polygonCount | varint | |
| *for each polygon* | | |
| ringCount | varint | ring 0 is the outer ring, rings 1..n-1 are holes |
| *for each ring* | | |
| pointCount | varint | closing duplicate point not stored; at least 3 |
| *for each point i* | | |
| dLat | zigzag varint | i = 0: lat itself (minus 0); i > 0: minus previous lat in the same ring |
| dLon | zigzag varint | same rule for lon |

The file has no trailing data.

## Size

375,980 bytes (about 367 KiB) at tolerance 0.001 degree, which is under the 1 MB limit.
237 countries, 1,617 polygons, 1,629 rings, 93,968 points.
