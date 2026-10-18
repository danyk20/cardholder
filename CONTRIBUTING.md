# Contributing to Cardholder

Thanks for your interest in improving Cardholder!

## Development setup
1. JDK 17+ and the Android SDK (Android Studio, or the command-line tools with `platforms;android-37`).
2. Clone the repo and open it in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```

## Before opening a pull request
```bash
./gradlew spotlessApply detekt lintDebug testDebugUnitTest
```
CI runs the same checks and must be green before merging. Changes to `.github/` are also checked with
[actionlint](https://github.com/rhysd/actionlint).

## Conventions
- **Branches**: `feat/…`, `fix/…`, `chore/…`, `docs/…`.
- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/) – e.g. `feat(cardlist): add type filter chips`.
- **Architecture**: follow the module rules in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Features never depend on other features; `core:model` and `core:domain` stay free of Android types.
- **UI**: stateless composables with hoisted state; every user-facing string lives in `strings.xml`.
- **Security**: never log card data, never store it unencrypted, and keep sensitive screens `FLAG_SECURE`.
- **Database**: every schema change needs a Room migration and a migration test; commit the exported schema JSON.
- **Tests**: new logic comes with unit tests; prefer fakes over mocks.

## Reporting security issues
Please follow [SECURITY.md](SECURITY.md) – do not open public issues for vulnerabilities.

## CI speed
CI runs its checks as parallel jobs and normally takes 1–3 minutes, because almost every task comes from the Gradle
build cache saved by the last `main` run. Two things make it slow (5–7 minutes), by design of Gradle's caching:
- **Updating Gradle or any build plugin** (AGP, Kotlin, Spotless, detekt, Kover, …) changes every task's cache key,
  so the first run afterwards rebuilds everything. Dependabot therefore bundles all Gradle updates into one
  weekly PR.
- **PRs only reuse caches that `main` has finished saving.** After merging a PR that changes the build setup, wait
  for `main`'s CI to finish before rebasing or pushing other PRs; otherwise they rebuild everything too.
