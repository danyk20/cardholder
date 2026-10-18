# 10. Updates from within the app

Date: 2026-10-18

## Status
Accepted

## Context
The app is published in two places: the AAB on Google Play and the APK on GitHub releases. Users asked to
check for and install updates from within the app. The app is offline by design, and Google Play's
*Device and Network Abuse* policy forbids apps distributed on Play from updating themselves by any
means other than Play (including holding the `REQUEST_INSTALL_PACKAGES` permission for that purpose).

## Decision
- The `app` module has two product flavors, `play` and `github`. The release workflow publishes the
  `play` AAB (for Google Play) and the `github` APK (for GitHub). Library modules have no flavors.
- One `UpdateSource` interface, bound per flavor:
  - `play`: Google Play In-App Updates (immediate flow). Play downloads and installs; the build has no
    install permission and no download code.
  - `github`: reads `releases/latest` from GitHub's API, downloads `Cardholder-<version>.apk` (HTTPS,
    GitHub hosts only, redirects only within them, size limit), verifies the SHA-256 digest GitHub
    publishes for the asset and hands the file to Android's `PackageInstaller`. Android asks the user to
    confirm and only accepts an APK signed with the installed app's key. Only this flavor declares
    `REQUEST_INSTALL_PACKAGES`.
- Checks are manual (*Settings > About > Check for updates*). An automatic check can be switched on; it is
  off by default and runs at most once a week, when the app opens, and only shows something if an update
  exists. No background work.
- Settings gets the section through a slot provided by the app, so `feature:settings` doesn't depend on
  the flavors.

## Consequences
- Which store a copy updates from is decided by the build, not by inspecting the installer at runtime: a
  Play copy is always the `play` flavor, everything else is the GitHub APK.
- The privacy policy, README and SECURITY.md describe the additional, user-initiated network access.
- CI and the documented commands keep `testDebugUnitTest` and `lintDebug`; in `app` these run both flavors.
