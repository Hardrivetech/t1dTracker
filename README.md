# t1dTracker

Simple Type 1 diabetes tracker (Compose + Room).

## Quick build & run (Windows)

- Ensure JDK 17 is installed and `JAVA_HOME` points to it. Example (PowerShell):

```powershell
setx -m JAVA_HOME "C:\Program Files\Java\jdk-17"
# Restart your shell/IDE after setx
```

- Build debug APK:

```powershell
./gradlew assembleDebug
```

- Run static checks (ktlint + detekt):

```bash
./gradlew ktlintCheck detekt
```

## Running tests

- Unit tests:

```bash
./gradlew test
```

- Instrumented tests (requires emulator or device):

```bash
./gradlew connectedAndroidTest
```

## Backup / Restore

- In the app: Settings → enter a backup password → **Export backup** → choose a file location.
- To restore: Settings → **Import backup** → pick the previously saved file and enter the same password.
- **Share backup** writes an encrypted file to app cache and opens the system share sheet.
- Backups are encrypted using PBKDF2-derived AES-GCM (300k iterations by default).
- Backups are encrypted using PBKDF2-derived AES-GCM (400,000 iterations by default).

## Privacy & Telemetry

- The app includes an in-app privacy notice (Settings → Privacy policy).
- Telemetry (Crashlytics / Analytics) is disabled by default; enable via Settings → Allow telemetry.
- For Play Store publishing you will need to host a public privacy policy URL (GitHub Pages, Netlify, etc.).

## Biometric lock

- Optional biometric lock (Settings → Require biometric). When enabled, the app will prompt for biometric or device credential on startup.

## Notes for release

- Provide `google-services.json` to enable Crashlytics/Analytics.
	- For local development you can opt in to Firebase processing with a Gradle property or environment variable. If `google-services.json` exists in `app/` the build will only apply Firebase plugins when you pass `-PenableFirebase=true` or set `ENABLE_FIREBASE=true` in the environment. Example:

```powershell
./gradlew assembleRelease -PenableFirebase=true
# or in CI set ENV var ENABLE_FIREBASE=true
```
- Configure signing in `app/build.gradle` and add a secure signing key to CI for release builds.
- CI already runs `ktlint` and `detekt` before building (`.github/workflows/android-ci.yml`).
- CI already runs `ktlint` and `detekt` before building (`.github/workflows/android-ci.yml`).
- A CodeQL security scan workflow is included to run static analysis for common vulnerabilities (`.github/workflows/codeql-analysis.yml`).

### Quick commands

- Build debug APK:

```powershell
./gradlew assembleDebug
```

- Run unit tests (JVM):

```powershell
./gradlew testDebugUnitTest
```


### Release build & R8

- A `release` buildType is configured with `minifyEnabled` and `shrinkResources` enabled. ProGuard/R8 rules live in `app/proguard-rules.pro`.
- For Play Store release you must supply a proper signing key. The build script looks for the following Gradle project properties (set these in `~/.gradle/gradle.properties` or CI secrets):
	- `RELEASE_STORE_FILE` — path to keystore
	- `RELEASE_STORE_PASSWORD` — keystore password
	- `RELEASE_KEY_ALIAS` — key alias
	- `RELEASE_KEY_PASSWORD` — key password
- If those properties are not present, the build will fall back to the debug keystore (for local/CI verification only). Do NOT publish with the debug keystore.

Build a signed release bundle locally (example using `gradle.properties`):

```powershell
./gradlew bundleRelease
```

Or assemble an APK for quick testing:

```powershell
./gradlew assembleRelease
```

## Security recommendations

- Audit encryption paths and consider SQLCipher migration testing before enabling encrypted DB for existing users.
- Keep backup passwords safe — the app cannot recover them.

## Contact

Support/contact: hardrivetech@proton.me.
# t1dTracker (Android)

Minimal Android app scaffold (Kotlin + Jetpack Compose) with a simple insulin calculator.

Quick start
- Open this folder in Android Studio (File → Open) and let Gradle sync.
- Build & run on an emulator or device.

Files of interest
- [app/src/main/java/com/example/t1dtracker/MainActivity.kt](app/src/main/java/com/example/t1dtracker/MainActivity.kt) — Compose UI and calculator screen.
- [app/src/main/java/com/example/t1dtracker/insulin/InsulinCalculator.kt](app/src/main/java/com/example/t1dtracker/insulin/InsulinCalculator.kt) — calculation logic.

Next steps I can do for you
- Add Room (or EncryptedRoom) persistence for logged entries.
- Add history screen and charts.
- Add reminders/notifications and CSV export.
