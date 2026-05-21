# CI and Automatic Release Workflow — Design

**Date:** 2026-05-21
**Status:** Approved design, pending implementation plan

## Goal

Give the `beans` Android app a GitHub Actions CI pipeline and an automated
release pipeline, modelled on `shoriminimoe/pocket-pets`. CI gates every branch
and PR on linting, tests, and a build. Releases are cut automatically from
Conventional Commit history by `release-please`, with a signed debug APK
attached to each GitHub release.

## Context

### Current state of `beans`

- Android app, Kotlin 2.1.0, Jetpack Compose, Gradle 8.11.1, AGP 8.7.3.
- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, JDK 17.
- Plugins declared inline in `build.gradle.kts` (no version catalog).
- 28 Kotlin files; unit tests under `app/src/test` (no instrumented tests).
- `versionCode = 1`, `versionName = "1.0"` — both hardcoded.
- No `.github/` directory, no ktlint, no Android `lint {}` config, no committed
  keystore. `.gitignore` ignores `*.keystore`, `*.jks`, `*.apk`, `*.aab`.

### Example: `shoriminimoe/pocket-pets`

Uses two workflows — `ci.yml` and `release-please.yml` — plus
`release-please-config.json`, `.release-please-manifest.json`, an `.editorconfig`,
a committed `app/debug.keystore`, and ktlint wired through the root build script.
This design ports that setup to `beans`.

## Decisions

Confirmed with the user during brainstorming:

1. **CI checks:** full mirror — ktlint, Android Lint, unit tests, debug APK build.
   This requires adding the ktlint Gradle plugin to `beans` and fixing existing
   style/lint violations so CI passes on the first run.
2. **Release versioning:** start at `0.1.0` in prerelease mode
   (`bump-minor-pre-major`, releases marked as prereleases). `feat:` commits bump
   the `0.x` minor; `fix:` bump the patch.
3. **Release artifact:** attach a debug APK signed by a stable, repo-committed
   `app/debug.keystore`, so APKs across releases share a signature and can be
   sideloaded over one another without uninstalling.
4. **Build-script footprint (Approach A):** add ktlint inline in the existing
   `build.gradle.kts`. No migration to a Gradle version catalog — CI and release
   do not require it, and that migration is an orthogonal refactor.

### Lint strictness

`pocket-pets` sets `warningsAsErrors = true` in its `lint {}` block. `beans` has
never been lint-gated, so it may carry a pre-existing warning backlog.
Implementation will mirror `abortOnError = true`, `checkReleaseBuilds = true`,
and `disable += "AndroidGradlePluginVersion"`, but enable `warningsAsErrors = true`
**only if the existing code is already warning-clean** after running lint
locally. If it is not, the warning count will be reported back to the user
rather than silently weakening the config.

## File inventory

### New files

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | CI: ktlint + Android Lint + unit tests + debug APK build |
| `.github/workflows/release-please.yml` | Release automation + APK attach |
| `release-please-config.json` | release-please configuration |
| `.release-please-manifest.json` | Version tracker, seeded at `0.1.0` |
| `.editorconfig` | ktlint style configuration |
| `app/debug.keystore` | Stable debug keystore (binary), generated during implementation |

### Modified files

| File | Change |
|---|---|
| `build.gradle.kts` (root) | Add ktlint plugin + `subprojects {}` block |
| `app/build.gradle.kts` | Debug `signingConfig`, `versionCode` wiring, `versionName` marker, `lint {}` block |
| `.gitignore` | Add `!app/debug.keystore` exception |
| Existing `*.kt` files | Fix ktlint / Android Lint violations so CI passes |

`CHANGELOG.md` is created and maintained by `release-please`; it is not authored
here.

## Detailed design

### `.github/workflows/ci.yml`

Mirrors `pocket-pets`. Triggers on push to any branch except `main` and on PRs
targeting `main`; a `concurrency` group cancels superseded runs. Two jobs:

- **lint** (`timeout-minutes: 15`): checkout → JDK 17 (Temurin) → Android SDK →
  Gradle → `./gradlew ktlintCheck --stacktrace` → `./gradlew :app:lintDebug
  --stacktrace`. Uploads ktlint + lint reports on failure.
- **test-and-build** (`timeout-minutes: 30`): same setup → `./gradlew test
  --stacktrace` → `./gradlew :app:assembleDebug --stacktrace`. Uploads test
  reports on failure and the debug APK always.

Adjustment from the example: Android SDK packages are
`platforms;android-35 build-tools;35.0.0 platform-tools` (the example uses
`android-36`) because `beans` targets `compileSdk = 35`.

```yaml
name: CI

on:
  push:
    branches-ignore:
      - main
  pull_request:
    branches:
      - main

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: "platforms;android-35 build-tools;35.0.0 platform-tools"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' && github.event_name != 'pull_request' }}

      - name: ktlint
        run: ./gradlew ktlintCheck --stacktrace

      - name: Android Lint
        run: ./gradlew :app:lintDebug --stacktrace

      - name: Upload lint reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: lint-reports
          path: |
            app/build/reports/ktlint/
            app/build/reports/lint-results-*.html
            app/build/reports/lint-results-*.xml
            app/build/intermediates/lint_intermediate_text_report/
          if-no-files-found: ignore

  test-and-build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: "platforms;android-35 build-tools;35.0.0 platform-tools"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' && github.event_name != 'pull_request' }}

      - name: Run unit tests
        run: ./gradlew test --stacktrace

      - name: Assemble debug APK
        run: ./gradlew :app:assembleDebug --stacktrace

      - name: Upload test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: app/build/reports/tests/
          if-no-files-found: ignore

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
```

### `.github/workflows/release-please.yml`

Mirrors `pocket-pets`. Runs on push to `main` with `contents: write` and
`pull-requests: write`. Two jobs:

- **release-please**: runs `googleapis/release-please-action@v4`, which maintains
  a rolling "release" PR; when that PR merges it cuts a GitHub release and tag.
  Exposes `release_created`, `tag_name`, `major`, `minor`, `patch` as outputs.
- **build-and-attach**: gated on `release_created == 'true'`. Checks out the new
  tag, derives an integer `versionCode` from `major.minor.patch`
  (`M*10000 + m*100 + p`, floored at `100`; e.g. `0.1.0` → `100`), builds
  `:app:assembleDebug -PreleaseVersionCode=<n>`, renames the APK to
  `beans-<tag>-debug.apk`, and uploads it to the release with `gh release upload`.

```yaml
name: release-please

on:
  push:
    branches:
      - main

permissions:
  contents: write
  pull-requests: write

jobs:
  release-please:
    runs-on: ubuntu-latest
    outputs:
      release_created: ${{ steps.rp.outputs.release_created }}
      tag_name: ${{ steps.rp.outputs.tag_name }}
      major: ${{ steps.rp.outputs.major }}
      minor: ${{ steps.rp.outputs.minor }}
      patch: ${{ steps.rp.outputs.patch }}
    steps:
      - id: rp
        uses: googleapis/release-please-action@v4
        with:
          config-file: release-please-config.json
          manifest-file: .release-please-manifest.json

  build-and-attach:
    needs: release-please
    if: ${{ needs.release-please.outputs.release_created == 'true' }}
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ needs.release-please.outputs.tag_name }}

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: "platforms;android-35 build-tools;35.0.0 platform-tools"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Derive versionCode from tag
        id: ver
        run: |
          MAJOR="${{ needs.release-please.outputs.major }}"
          MINOR="${{ needs.release-please.outputs.minor }}"
          PATCH="${{ needs.release-please.outputs.patch }}"
          # Monotonically-increasing integer: M*10000 + m*100 + p
          # Supports up to minor 99 / patch 99 per major.
          CODE=$((10#$MAJOR * 10000 + 10#$MINOR * 100 + 10#$PATCH))
          # Reserve 1 as the dev default; release codes start at >= 100.
          if [ "$CODE" -lt 100 ]; then CODE=$((CODE + 100)); fi
          echo "code=$CODE" >> "$GITHUB_OUTPUT"
          echo "Derived versionCode=$CODE for ${{ needs.release-please.outputs.tag_name }}"

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug -PreleaseVersionCode=${{ steps.ver.outputs.code }} --stacktrace

      - name: Rename APK
        run: |
          mkdir -p release-artifacts
          cp app/build/outputs/apk/debug/app-debug.apk \
             "release-artifacts/beans-${{ needs.release-please.outputs.tag_name }}-debug.apk"

      - name: Attach APK to release
        run: |
          gh release upload "${{ needs.release-please.outputs.tag_name }}" \
            release-artifacts/*.apk --clobber
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### `release-please-config.json`

`release-type: simple` with a `generic` `extra-files` entry pointing at
`app/build.gradle.kts`; release-please rewrites the version on the line marked
`x-release-please-version`. Prerelease + pre-major bumping per Decision 2.

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
  "release-type": "simple",
  "include-component-in-tag": false,
  "include-v-in-tag": true,
  "prerelease": true,
  "draft": false,
  "bump-minor-pre-major": true,
  "bump-patch-for-minor-pre-major": false,
  "packages": {
    ".": {
      "package-name": "beans",
      "changelog-path": "CHANGELOG.md",
      "extra-files": [
        {
          "type": "generic",
          "path": "app/build.gradle.kts"
        }
      ]
    }
  }
}
```

### `.release-please-manifest.json`

```json
{
  ".": "0.1.0"
}
```

### `.editorconfig`

Ports the `pocket-pets` file. The `compose_allowed_composition_locals` line is
omitted — it lists `pocket-pets`-specific composition locals and has no
equivalent in `beans` yet.

```ini
root = true

[*]
indent_style = space
end_of_line = lf
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true

[*.{kt,kts}]
indent_size = 4
ktlint_code_style = ktlint_official
# Composable functions intentionally use PascalCase per the Compose framework
# convention; compose-rules provides its own Composable-aware naming check.
ktlint_function_naming_ignore_when_annotated_with = Composable

[*.{xml,yml,yaml,json,toml,md}]
indent_size = 2
```

### `build.gradle.kts` (root)

Add the ktlint plugin (`apply false`) and a `subprojects {}` block that applies
ktlint to the `:app` module with Compose-aware rules. Versions match
`pocket-pets`: ktlint Gradle plugin `12.3.0`, ktlint engine `1.5.0`,
compose-rules `0.4.22`.

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0" apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
    }

    // Compose-aware ktlint rules so @Composable PascalCase doesn't fail function-naming.
    dependencies {
        add("ktlintRuleset", "io.nlopez.compose.rules:ktlint:0.4.22")
    }
}
```

### `app/build.gradle.kts`

Four changes:

1. **`versionCode` / `versionName`** in `defaultConfig` — `versionCode` reads the
   `releaseVersionCode` Gradle property (the release workflow passes it),
   defaulting to `1` for local/dev builds. `versionName` carries the
   `x-release-please-version` marker so release-please can rewrite it.

   ```kotlin
   versionCode = (project.findProperty("releaseVersionCode") as String?)?.toInt() ?: 1
   versionName = "0.1.0" // x-release-please-version
   ```

   This replaces the current `versionCode = 1` / `versionName = "1.0"`.

2. **`signingConfigs`** block (before `buildTypes`) — defines the `debug` signing
   config against the committed keystore:

   ```kotlin
   signingConfigs {
       // Stable, repo-committed debug keystore so CI-built APKs match
       // locally-built ones and can be sideloaded over each other without
       // a forced uninstall. Debug-only, well-known default credentials;
       // grants no production access.
       getByName("debug") {
           storeFile = file("debug.keystore")
           storePassword = "android"
           keyAlias = "androiddebugkey"
           keyPassword = "android"
       }
   }
   ```

3. **`buildTypes`** — add a `debug {}` block (none exists today) wiring the
   signing config:

   ```kotlin
   debug {
       signingConfig = signingConfigs.getByName("debug")
   }
   ```

4. **`lint`** block (inside `android {}`):

   ```kotlin
   lint {
       abortOnError = true
       checkReleaseBuilds = true
       // AGP 8.7.x lags the latest release; lint flags this and we track
       // toolchain upgrades separately rather than failing CI on it.
       disable += "AndroidGradlePluginVersion"
       // warningsAsErrors is enabled during implementation only if the
       // existing code is already warning-clean (see "Lint strictness").
   }
   ```

### `.gitignore`

The `*.keystore` rule must keep ignoring stray keystores but allow the committed
debug one. Update the keystore section to:

```gitignore
# Keystore files
*.jks
*.keystore
# …except the stable debug keystore, which is intentionally committed.
!app/debug.keystore
```

### `app/debug.keystore`

Generated once during implementation with the Android standard debug
credentials, then committed:

```sh
keytool -genkeypair -v \
  -keystore app/debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

It is a debug keystore with well-known public credentials; it grants no
production access and is safe to commit.

## Implementation notes

- **Violation fixing.** Adding ktlint will surface style violations across the
  28 existing Kotlin files. Implementation runs `./gradlew ktlintFormat` to
  auto-fix the bulk, then resolves the remainder by hand. The violation count is
  unknown until `ktlintCheck` first runs and will be reported.
- **Lint strictness.** Run `./gradlew :app:lintDebug` locally before finalizing
  the `lint {}` block; decide on `warningsAsErrors` per the "Lint strictness"
  section above.
- **Verification prerequisites.** Running Gradle locally needs `JAVA_HOME` set
  and a `local.properties` with the Android SDK path (per project build-env
  notes). These are environment setup, not committed files.
- **First release-please run.** On the first push to `main` after these files
  land, `release-please` opens its initial release PR by reading commit history.
  If the first proposed version or changelog looks wrong, it can be corrected by
  seeding `.release-please-manifest.json` or setting a `bootstrap-sha`. This is
  an operational follow-up, not a code change.

## Out of scope

- Gradle version catalog migration (`gradle/libs.versions.toml`).
- `buildConfig` / `BuildConfig` fields.
- Release (non-debug) signing configuration.
- An instrumented-test (`androidTest`) CI job — `beans` has only unit tests.
- Authoring `CHANGELOG.md` — `release-please` owns it.

## Success criteria

- `./gradlew ktlintCheck` passes on the existing codebase.
- `./gradlew :app:lintDebug` passes with the chosen `lint {}` config.
- `./gradlew test` passes.
- `./gradlew :app:assembleDebug` produces an APK signed by `app/debug.keystore`.
- `./gradlew :app:assembleDebug -PreleaseVersionCode=100` builds with
  `versionCode = 100`.
- Both workflow files are valid YAML and reference only existing paths/secrets.
- On push to a non-`main` branch / PR to `main`, `ci.yml` runs both jobs.
- On push to `main`, `release-please.yml` opens or updates a release PR; merging
  it cuts a release and attaches `beans-<tag>-debug.apk`.
