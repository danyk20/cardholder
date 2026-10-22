#!/usr/bin/env python3
"""Build the offline country-borders asset used for point-to-country lookup.

Input:  Natural Earth admin-0 countries GeoJSON (1:50m).
Output: compact binary file "country_borders.bin" (format: see README.md).

Standard library only. Run as:
    python3 -I tools/country-borders/build_borders.py <input.geojson> <output.bin>
"""

import argparse
import json
import math
import os
import sys

# Property holding the ISO 3166-1 alpha-2 code. ISO_A2 is -99 for France,
# Norway and Kosovo in this dataset, so ISO_A2_EH is used instead.
CODE_PROPERTY = "ISO_A2_EH"

# Douglas-Peucker tolerance in degrees (about 100 m). Raised by the size
# check if the output would exceed the size budget.
SIMPLIFY_TOLERANCE_DEG = 0.001

# Coordinates are stored as integers in units of 1e-4 degree (about 11 m).
QUANT = 10000

MAGIC = b"CHCB"
VERSION = 1


def is_valid_code(value):
    """True only for exactly two upper-case ASCII letters A-Z."""
    return (
        isinstance(value, str)
        and len(value) == 2
        and all("A" <= c <= "Z" for c in value)
    )


def perp_segment_distance(px, py, ax, ay, bx, by):
    """Distance from point P to segment AB (degrees, planar)."""
    dx = bx - ax
    dy = by - ay
    len2 = dx * dx + dy * dy
    if len2 == 0.0:
        return math.hypot(px - ax, py - ay)
    t = ((px - ax) * dx + (py - ay) * dy) / len2
    if t < 0.0:
        t = 0.0
    elif t > 1.0:
        t = 1.0
    cx = ax + t * dx
    cy = ay + t * dy
    return math.hypot(px - cx, py - cy)


def douglas_peucker(points, tolerance):
    """Simplify an open polyline, always keeping both endpoints.

    Iterative (no recursion limit issues). Returns the kept points in
    their original order.
    """
    n = len(points)
    if n <= 2:
        return list(points)
    keep = [False] * n
    keep[0] = True
    keep[n - 1] = True
    stack = [(0, n - 1)]
    while stack:
        first, last = stack.pop()
        ax, ay = points[first]
        bx, by = points[last]
        max_dist = -1.0
        index = -1
        for i in range(first + 1, last):
            px, py = points[i]
            d = perp_segment_distance(px, py, ax, ay, bx, by)
            if d > max_dist:
                max_dist = d
                index = i
        if index != -1 and max_dist > tolerance:
            keep[index] = True
            stack.append((first, index))
            stack.append((index, last))
    return [points[i] for i in range(n) if keep[i]]


def process_ring(ring):
    """Turn one GeoJSON ring into a list of quantized (lat_u, lon_u) points.

    Returns None if the ring is dropped (fewer than 3 distinct points left).
    The ring is stored without its closing duplicate point.
    """
    # Work in (lon, lat) order as given by GeoJSON.
    pts = [(float(p[0]), float(p[1])) for p in ring]
    if len(pts) >= 2 and pts[0] == pts[-1]:
        pts = pts[:-1]
    if len(pts) < 3:
        return None

    # Simplify the closed ring: treat it as an open polyline whose two ends
    # are the same point, then drop the duplicate end.
    closed = pts + [pts[0]]
    simplified = douglas_peucker(closed, SIMPLIFY_TOLERANCE_DEG)
    if len(simplified) >= 2 and simplified[0] == simplified[-1]:
        simplified = simplified[:-1]

    # Quantize to 1e-4 degree integers (round half up).
    quantized = []
    for lon, lat in simplified:
        lat_u = int(math.floor(lat * QUANT + 0.5))
        lon_u = int(math.floor(lon * QUANT + 0.5))
        quantized.append((lat_u, lon_u))

    # Remove consecutive duplicates, including the wrap-around pair.
    deduped = []
    for p in quantized:
        if not deduped or deduped[-1] != p:
            deduped.append(p)
    while len(deduped) >= 2 and deduped[-1] == deduped[0]:
        deduped.pop()

    if len(set(deduped)) < 3:
        return None
    return deduped


def process_polygon(rings):
    """Process one polygon (list of rings, outer first).

    Returns a list of quantized rings, or None if the polygon is dropped.
    """
    if not rings:
        return None
    outer = process_ring(rings[0])
    if outer is None:
        return None
    out = [outer]
    for hole in rings[1:]:
        h = process_ring(hole)
        if h is not None:
            out.append(h)
    return out


def collect_polygons(geometry):
    """Return a list of polygons, each a list of rings (raw GeoJSON floats)."""
    if not geometry:
        return []
    gtype = geometry.get("type")
    coords = geometry.get("coordinates")
    if gtype == "Polygon":
        return [coords]
    if gtype == "MultiPolygon":
        return list(coords)
    return []


def build_model(geojson_path, tolerance=None):
    """Read the GeoJSON and return {code: [polygon, ...]} with quantized rings.

    Each polygon is a list of rings; each ring is a list of (lat_u, lon_u).
    Countries are returned in ascending code order.
    """
    global SIMPLIFY_TOLERANCE_DEG
    if tolerance is not None:
        SIMPLIFY_TOLERANCE_DEG = tolerance

    with open(geojson_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    by_code = {}
    skipped_code = 0
    skipped_geometry = 0
    for feature in data.get("features", []):
        props = feature.get("properties") or {}
        code = props.get(CODE_PROPERTY)
        if not is_valid_code(code):
            skipped_code += 1
            continue
        geometry = feature.get("geometry")
        if not geometry or geometry.get("type") not in ("Polygon", "MultiPolygon"):
            skipped_geometry += 1
            continue
        for raw_polygon in collect_polygons(geometry):
            poly = process_polygon(raw_polygon)
            if poly is not None:
                by_code.setdefault(code, []).append(poly)

    model = {}
    for code in sorted(by_code):
        if by_code[code]:
            model[code] = by_code[code]

    print("features skipped (bad %s): %d" % (CODE_PROPERTY, skipped_code))
    print("features skipped (no Polygon/MultiPolygon geometry): %d" % skipped_geometry)
    return model


# ---------------------------------------------------------------- encoding

def write_uvarint(out, value):
    if value < 0:
        raise ValueError("uvarint must be non-negative")
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return


def zigzag(value):
    """Standard zigzag for a signed integer that fits in 64 bits."""
    if not (-(1 << 63) <= value < (1 << 63)):
        raise ValueError("value out of 64-bit range: %d" % value)
    if value >= 0:
        return value << 1
    return ((-value) << 1) - 1


def write_svarint(out, value):
    write_uvarint(out, zigzag(value))


def encode(model):
    """Encode the model into the binary format described in the README."""
    out = bytearray()
    out += MAGIC
    out.append(VERSION)
    write_uvarint(out, len(model))

    for code, polygons in model.items():
        out += code.encode("ascii")
        all_points = [p for poly in polygons for ring in poly for p in ring]
        min_lat = min(p[0] for p in all_points)
        max_lat = max(p[0] for p in all_points)
        min_lon = min(p[1] for p in all_points)
        max_lon = max(p[1] for p in all_points)
        write_svarint(out, min_lat)
        write_svarint(out, min_lon)
        write_svarint(out, max_lat)
        write_svarint(out, max_lon)
        write_uvarint(out, len(polygons))
        for poly in polygons:
            write_uvarint(out, len(poly))
            for ring in poly:
                write_uvarint(out, len(ring))
                prev_lat = 0
                prev_lon = 0
                for lat_u, lon_u in ring:
                    write_svarint(out, lat_u - prev_lat)
                    write_svarint(out, lon_u - prev_lon)
                    prev_lat = lat_u
                    prev_lon = lon_u
    return bytes(out)


def count_stats(model):
    polygons = 0
    rings = 0
    points = 0
    for code, polys in model.items():
        polygons += len(polys)
        for poly in polys:
            rings += len(poly)
            for ring in poly:
                points += len(ring)
    return len(model), polygons, rings, points


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("input", help="Natural Earth admin-0 countries GeoJSON")
    parser.add_argument("output", help="output .bin path (parent dirs are created)")
    parser.add_argument(
        "--tolerance",
        type=float,
        default=None,
        help="Douglas-Peucker tolerance in degrees (default %g)" % SIMPLIFY_TOLERANCE_DEG,
    )
    args = parser.parse_args(argv)

    model = build_model(args.input, args.tolerance)
    data = encode(model)

    out_dir = os.path.dirname(os.path.abspath(args.output))
    os.makedirs(out_dir, exist_ok=True)
    with open(args.output, "wb") as f:
        f.write(data)

    countries, polygons, rings, points = count_stats(model)
    print("tolerance_deg: %g" % SIMPLIFY_TOLERANCE_DEG)
    print("countries: %d" % countries)
    print("polygons: %d" % polygons)
    print("rings: %d" % rings)
    print("points: %d" % points)
    print("bytes: %d" % len(data))
    print("wrote: %s" % args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
