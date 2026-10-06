# Releasing

Releases are built and published by [`.github/workflows/release.yml`](../.github/workflows/release.yml)
when a tag `v*` is pushed.

## One-time setup: signing key
1. Create an upload/signing key and **keep it safe**. If you lose it, you can't update the app:
   ```bash
   keytool -genkeypair -v -keystore cardholder-release.jks -alias cardholder \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these repository secrets (*Settings → Secrets and variables → Actions*):
   | Secret | Value |
   |---|---|
   | `SIGNING_KEYSTORE_BASE64` | `base64 -i cardholder-release.jks` |
   | `SIGNING_STORE_PASSWORD` | keystore password |
   | `SIGNING_KEY_ALIAS` | `cardholder` |
   | `SIGNING_KEY_PASSWORD` | key password |

## Cutting a release
1. Update `versionCode`/`versionName` in `app/build.gradle.kts` and `CHANGELOG.md`.
2. Merge to `main` with green CI.
3. Tag and push:
   ```bash
   git tag -a v1.0.0 -m "Cardholder 1.0.0"
   git push origin v1.0.0
   ```
4. The workflow builds the signed APK and AAB (R8-minified) and attaches them to a GitHub release.

## Before tagging
- Run the instrumented tests (add the `run-instrumented` label to a PR or trigger the workflow manually).
- Smoke-test a minified build: R8 full mode is enabled, and keep rules live in `app/proguard-rules.pro`.
