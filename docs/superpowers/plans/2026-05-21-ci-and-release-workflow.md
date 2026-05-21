# CI and Automatic Release Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the `beans` Android app a GitHub Actions CI pipeline and an automated `release-please` release pipeline, modelled on `shoriminimoe/pocket-pets`.

**Architecture:** Two workflows under `.github/workflows/` — `ci.yml` gates branches and PRs on ktlint, Android Lint, unit tests, and a debug APK build; `release-please.yml` cuts releases from Conventional Commit history and attaches a signed debug APK. Supporting changes wire ktlint into the Gradle build, add a committed debug keystore for reproducible signing, and parameterize the app version so `release-please` can manage it.

**Tech Stack:** GitHub Actions, Gradle 8.11.1 (Kotlin DSL), AGP 8.7.3, Kotlin 2.1.0, JDK 17, ktlint (`org.jlleitschuh.gradle.ktlint` 12.3.0 / ktlint engine 1.5.0 / compose-rules 0.4.22), `googleapis/release-please-action@v4`.

**Reference spec:** `docs/superpowers/specs/2026-05-21-ci-and-release-workflow-design.md`

---

## Prerequisites

Complete these before Task 1. They produce no commit — they establish a working build environment. `local.properties` is git-ignored and intentionally not committed.

- [ ] **Confirm the working branch.** Run `git branch --show-current`. You should be on the feature/worktree branch, not `main`.

- [ ] **Confirm JDK 17.** Run `java -version`. It must report a 17.x JVM. If `JAVA_HOME` is unset, set it to a JDK 17 install (e.g. `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`). `keytool` (used in Task 1) ships with the JDK.

- [ ] **Confirm the Android SDK and `local.properties`.** Tasks 2, 4, and 5 run Gradle builds that require the Android SDK. If `local.properties` does not exist at the repo root, create it with the SDK path, e.g.:

  ```properties
  sdk.dir=/home/sam/Android/Sdk
  ```

  Verify the path exists and contains `platforms/android-35` and `build-tools/35.0.0`. If the Android SDK is **not installed** in this environment, the Gradle verification steps cannot run — **stop and report this as a blocker.** Do not mark build-dependent tasks complete without running their verification commands.

- [ ] **Confirm `./gradlew` runs.** Run `./gradlew --version`. Expected: Gradle 8.11.1, JVM 17.

---

## Task 1: Add the committed debug keystore

**Files:**
- Modify: `.gitignore`
- Create: `app/debug.keystore` (binary)

A fixed, repo-committed debug keystore makes every CI- and locally-built debug APK share one signing identity, so they can be sideloaded over each other without uninstalling. It uses the Android standard debug credentials (well-known and public) and grants no production access.

- [ ] **Step 1: Add the keystore exception to `.gitignore`**

The current keystore section is:

```gitignore
# Keystore files
*.jks
*.keystore
```

Change it to:

```gitignore
# Keystore files
*.jks
*.keystore
# …except the stable debug keystore, which is intentionally committed.
!app/debug.keystore
```

- [ ] **Step 2: Generate the keystore**

Run from the repo root:

```bash
keytool -genkeypair -v \
  -keystore app/debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

A warning that the JKS keystore format is proprietary and a suggestion to migrate to PKCS12 is expected and harmless — AGP reads JKS debug keystores fine.

- [ ] **Step 3: Verify the keystore**

Run:

```bash
keytool -list -v -keystore app/debug.keystore -storepass android
```

Expected: one entry, `Alias name: androiddebugkey`, `Owner: CN=Android Debug, O=Android, C=US`. Note the `SHA256` certificate fingerprint — Task 2 confirms the built APK is signed by this same certificate.

- [ ] **Step 4: Commit**

```bash
git add .gitignore app/debug.keystore
git commit -m "build: add committed debug keystore for reproducible signing

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Wire debug signing and release versioning into `app/build.gradle.kts`

**Files:**
- Modify: `app/build.gradle.kts`

This makes the debug build type sign with the committed keystore, makes `versionCode` overridable by the release workflow via a Gradle property, and adds the `x-release-please-version` marker `release-please` rewrites.

- [ ] **Step 1: Parameterize `versionCode` and `versionName`**

In the `defaultConfig { }` block, the current lines are:

```kotlin
        versionCode = 1
        versionName = "1.0"
```

Replace them with:

```kotlin
        versionCode = (project.findProperty("releaseVersionCode") as String?)?.toInt() ?: 1
        versionName = "0.1.0" // x-release-please-version
```

`versionCode` defaults to `1` for local/dev builds and is overridden by `-PreleaseVersionCode=<n>` in the release workflow. `versionName` starts at `0.1.0` (the seeded release version) and the trailing comment is the anchor `release-please` updates.

- [ ] **Step 2: Add a `signingConfigs` block**

Inside the `android { }` block, immediately **before** the `buildTypes { }` block, add:

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

- [ ] **Step 3: Add a `debug` build type**

The current `buildTypes { }` block contains only a `release { }` entry. Add a `debug { }` entry so it reads:

```kotlin
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

- [ ] **Step 4: Build the debug APK and verify the signature**

Run:

```bash
./gradlew :app:assembleDebug --stacktrace
keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk
```

Expected: the build succeeds, and the printed certificate `Owner` is `CN=Android Debug, O=Android, C=US` with a `SHA256` fingerprint matching the one from Task 1 Step 3. (If the build cannot run because the Android SDK is unavailable, stop and report — do not mark this task complete.)

- [ ] **Step 5: Verify the `versionCode` override**

Run:

```bash
./gradlew :app:assembleDebug -PreleaseVersionCode=200 --stacktrace
"$(grep -m1 sdk.dir local.properties | cut -d= -f2)/build-tools/35.0.0/aapt2" dump badging \
  app/build/outputs/apk/debug/app-debug.apk | grep -o "versionCode='[0-9]*'"
```

Expected: `versionCode='200'`. This confirms the Gradle property flows into the manifest.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: sign debug builds with committed keystore and parameterize version

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Add ktlint to the Gradle build

**Files:**
- Create: `.editorconfig`
- Modify: `build.gradle.kts` (root)

This installs ktlint with Compose-aware rules. After this task `ktlintCheck` *runs* but is expected to *fail* on pre-existing style violations — Task 4 fixes them.

- [ ] **Step 1: Create `.editorconfig`**

Create `.editorconfig` at the repo root with exactly this content:

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

- [ ] **Step 2: Add the ktlint plugin to the root `build.gradle.kts`**

The current root `build.gradle.kts` is:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
```

Replace the whole file with:

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

- [ ] **Step 3: Verify ktlint is wired in**

Run:

```bash
./gradlew ktlintCheck --stacktrace
```

Expected: the task **runs** (it resolves the ktlint plugin and ruleset and produces `app/build/reports/ktlint/` reports). It is **expected to FAIL** on pre-existing violations in `app/src/**` and possibly `app/build.gradle.kts` — that is fine and is fixed in Task 4. If instead it fails to *resolve the plugin or ruleset*, that is a real error to fix here.

- [ ] **Step 4: Commit**

```bash
git add .editorconfig build.gradle.kts
git commit -m "build: add ktlint with Compose-aware rules

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Fix ktlint violations

**Files:**
- Modify: `app/build.gradle.kts` and files under `app/src/**` (exact set determined by ktlint output)

The existing codebase was never ktlint-gated, so it carries style violations. This task makes `ktlintCheck` pass. The exact violations are unknown until the tool runs — follow the process below rather than a fixed file list.

- [ ] **Step 1: Auto-format**

Run:

```bash
./gradlew ktlintFormat --stacktrace
```

This auto-fixes the bulk of violations (indentation, import ordering, trailing whitespace, blank lines).

- [ ] **Step 2: Re-check and inspect remaining violations**

Run:

```bash
./gradlew ktlintCheck --stacktrace
```

If it PASSES, skip to Step 4. If it FAILS, open the report at `app/build/reports/ktlint/` (or read the console output) to see each remaining violation, its file:line, and its rule ID.

- [ ] **Step 3: Fix remaining violations by hand**

`ktlintFormat` cannot auto-fix everything. For each remaining violation, apply the fix the rule describes. Common manually-fixed rules:
- `standard:max-line-length` — break the line (extract a local, wrap arguments).
- `standard:no-wildcard-imports` — replace `import foo.*` with explicit imports.
- `standard:property-naming` / `standard:function-naming` — rename, unless it is a `@Composable` (already exempted via `.editorconfig`).
- compose-rules findings (e.g. `compose:modifier-missing-check`) — apply the rule's documented fix.

Re-run `./gradlew ktlintFormat` after manual edits in case they reopen auto-fixable formatting, then re-check.

- [ ] **Step 4: Verify ktlint passes**

Run:

```bash
./gradlew ktlintCheck --stacktrace
```

Expected: `BUILD SUCCESSFUL`, no violations.

- [ ] **Step 5: Verify tests still pass**

Formatting changes must not break compilation. Run:

```bash
./gradlew test --stacktrace
```

Expected: `BUILD SUCCESSFUL`, all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "style: fix ktlint violations across the codebase

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Add Android Lint configuration

**Files:**
- Modify: `app/build.gradle.kts`

This adds a `lint { }` block so `:app:lintDebug` gates CI. `warningsAsErrors` is decided based on the current code's warning count (see spec "Lint strictness").

- [ ] **Step 1: Add the `lint` block**

Inside the `android { }` block of `app/build.gradle.kts`, immediately after the `buildFeatures { }` block, add:

```kotlin
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // AGP 8.7.x lags the latest release; lint flags this and we track
        // toolchain upgrades separately rather than failing CI on it.
        disable += "AndroidGradlePluginVersion"
    }
```

- [ ] **Step 2: Run Android Lint and assess warnings**

Run:

```bash
./gradlew :app:lintDebug --stacktrace
```

Open `app/build/reports/lint-results-debug.html` (or the `.txt`/`.xml` alongside it). Count the warnings.

- [ ] **Step 3: Decide on `warningsAsErrors`**

- If lint reports **zero warnings** (or only ones easily fixed): fix any errors/warnings, then add `warningsAsErrors = true` to the `lint { }` block so it reads:

  ```kotlin
  lint {
      abortOnError = true
      warningsAsErrors = true
      checkReleaseBuilds = true
      // AGP 8.7.x lags the latest release; lint flags this and we track
      // toolchain upgrades separately rather than failing CI on it.
      disable += "AndroidGradlePluginVersion"
  }
  ```

- If lint reports a **non-trivial pre-existing warning backlog**: leave `warningsAsErrors` out, and **report the warning count and categories back to the user** — per the spec, do not silently weaken the config or silently absorb a large fix. Let the user decide whether to fix the backlog now or defer.

Fix any actual lint **errors** regardless — `abortOnError = true` means errors fail the build.

- [ ] **Step 4: Verify lint and ktlint pass**

Run:

```bash
./gradlew :app:lintDebug ktlintCheck --stacktrace
```

Expected: `BUILD SUCCESSFUL` (ktlint is re-run because `app/build.gradle.kts` changed; keep the new block at 4-space indent).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: add Android Lint configuration

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Add the CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create `.github/workflows/ci.yml`**

Create the file with exactly this content:

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

- [ ] **Step 2: Validate the YAML**

Run:

```bash
python3 -c "import yaml,sys; d=yaml.safe_load(open('.github/workflows/ci.yml')); assert 'jobs' in d and set(d['jobs'])=={'lint','test-and-build'}; print('ci.yml OK')"
```

Expected: `ci.yml OK`. If `python3`/PyYAML is unavailable, instead run `actionlint .github/workflows/ci.yml` if installed, or visually confirm the indentation matches the block above.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add CI workflow for ktlint, lint, tests, and build

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Add the release automation workflow

**Files:**
- Create: `release-please-config.json`
- Create: `.release-please-manifest.json`
- Create: `.github/workflows/release-please.yml`

- [ ] **Step 1: Create `release-please-config.json`**

Create the file at the repo root with exactly this content:

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

- [ ] **Step 2: Create `.release-please-manifest.json`**

Create the file at the repo root with exactly this content:

```json
{
  ".": "0.1.0"
}
```

- [ ] **Step 3: Create `.github/workflows/release-please.yml`**

Create the file with exactly this content:

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

- [ ] **Step 4: Validate the JSON and YAML**

Run:

```bash
python3 -m json.tool release-please-config.json > /dev/null && echo "config JSON OK"
python3 -m json.tool .release-please-manifest.json > /dev/null && echo "manifest JSON OK"
python3 -c "import yaml; d=yaml.safe_load(open('.github/workflows/release-please.yml')); assert set(d['jobs'])=={'release-please','build-and-attach'}; print('release-please.yml OK')"
```

Expected: `config JSON OK`, `manifest JSON OK`, `release-please.yml OK`. (`json.tool` is Python stdlib; if `python3` is entirely unavailable, use `jq . <file>` for the JSON files and `actionlint` for the YAML.)

- [ ] **Step 5: Commit**

```bash
git add release-please-config.json .release-please-manifest.json .github/workflows/release-please.yml
git commit -m "ci: add release-please automation workflow

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Integration verification (after all tasks)

These confirm the whole pipeline; they need a push and so are done with the user, not as committed steps.

- [ ] **CI runs on the branch.** Push the feature branch to `origin`. Because `ci.yml` triggers on `push` to any branch except `main`, the `lint` and `test-and-build` jobs should start. Both should pass — the local Gradle commands in Tasks 2, 4, and 5 already exercised the same tasks.

- [ ] **Release workflow activates on `main`.** `release-please.yml` only triggers on push to `main`, so it cannot be exercised from the feature branch. After this branch merges to `main`, `release-please` opens its first release PR. Note (per spec): this batch contains only `build:`/`ci:`/`style:`/`docs:` commits, none of which bump the version — `release-please` will not propose a release until a `feat:` or `fix:` commit lands on `main`. That is expected behavior.

---

## Self-review notes

- **Spec coverage:** All six new files and four modified files in the spec's file inventory map to tasks (keystore + `.gitignore` → T1; `app/build.gradle.kts` signing/version → T2; ktlint + `.editorconfig` + root `build.gradle.kts` → T3; violation fixes → T4; `lint {}` → T5; `ci.yml` → T6; `release-please-config.json` + `.release-please-manifest.json` + `release-please.yml` → T7). The "Lint strictness" decision is implemented as T5 Step 3. `CHANGELOG.md` is correctly excluded (release-please owns it).
- **Version consistency:** `versionName "0.1.0"` (T2) matches the manifest seed `0.1.0` (T7) and the spec. ktlint versions — plugin `12.3.0`, engine `1.5.0`, compose-rules `0.4.22` — match the spec. Android SDK `android-35`/`build-tools;35.0.0` is consistent across T6 and T7 and matches `compileSdk = 35`.
- **No placeholders:** Every file's full content is inline; Task 4's open-ended nature is inherent (violations are unknown pre-run) and is handled with an explicit process plus a "report back" gate, not a TODO.
