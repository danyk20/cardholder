# 6. Scanning with ML Kit

- Status: accepted
- Date: 2026-10-06

## Context
Capturing card sides should feel like a scanner app (edge detection, perspective correction), and
loyalty barcodes should be read from the camera or from photos, without sending images anywhere.

## Decision
- **Card sides**: ML Kit *Document Scanner* (Google Play services). It needs no camera permission;
  without Play services the app falls back to the system photo picker.
- **Barcodes**: ML Kit barcode scanning with the **bundled** model (offline, no Play services
  dependency) on CameraX frames and on card photos.
- The live scanner is rendered inside the editor window (not a dialog) so `FLAG_SECURE` applies.

## Consequences
The app works on devices without Google Play services, with reduced convenience for card sides.
The bundled barcode model adds a few MB to the APK.
