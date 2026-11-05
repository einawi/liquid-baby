# Liquid Baby - project notes

Keep this file up to date with every delivery so a brand-new chat can continue the project.

## What the app is
A baby monitor that works on the local Wi-Fi only (no internet). One app, installed on every phone.
On first open the user chooses the role:
- CHILD unit (exactly one): sits next to the sleeping child, listens, records 15-60 s clips when it hears the child, sends them to the parents. Can play the parent's voice (walkie-talkie) and stream the camera live.
- PARENT unit (one or two): list of recordings, slider for clip length (15-60 s), walkie-talkie button, camera button (live view).
Roles can be swapped later (a parent phone can become the child unit).

Owner: Einawi. App name: Liquid Baby. Package: com.liquid.liquidbaby. Public GitHub repo (name chosen in the wizard, default liquid-baby).
Test phones: Pixel 7 Pro, Galaxy A9 2018, Galaxy J50 (Android 6.0.1), Redmi Note 9, HONOR Pad X9a.
minSdk 23 (Android 6), compileSdk/targetSdk 36.

## Working method (no Android Studio)
- Claude writes all code and delivers ONE zip (LiquidBaby.zip, same name every time).
- User unzips over the project folder (replace all) and double-clicks Rebuild-APK.bat.
- GitHub Actions (.github/workflows/release.yml) runs unit tests, builds a signed release APK, verifies it, and publishes a GitHub Release tagged v<version>-build<code>.
- The app updates itself from the latest GitHub Release (repo comes from the build, via -PgithubRepo).
- EVERY delivery must raise appVersionCode in app/build.gradle.kts (the workflow refuses an existing tag) and update RELEASE_NOTES.md. "[required]" in the notes = required update.
- Build errors: user pastes build-error.txt; fix every error.
- Signing key: liquidbaby.jks + keys-info.txt in Documents\LiquidBaby-KEYS (user must back up; if lost, installed app cannot be updated). Stored as GitHub secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.

## Technical decisions
- No third-party libraries (only the Android framework + JUnit for tests). UI is built in code (Ui.kt), Theme.Material.Light.NoActionBar, own insets handling (edge-to-edge on Android 15+).
- AGP 9.1.1 (Kotlin built in, no kotlin-android plugin), Gradle 9.3.1, JDK 21 in CI.
- Updater installs through PackageInstaller sessions (no FileProvider needed). Checks GitHub API releases/latest; verifies .sha256.
- Background check: JobScheduler job every ~12 h plus a check at app start (throttled to 6 h).
- Crash handler writes to Diagnostics log; two crashes within 20 s of start -> SafeModeActivity on next launch.
- Backup/Export: settings as JSON via share / clipboard. Recordings will join it when recording exists.

## Files
- app/src/main/java/com/liquid/liquidbaby: App, MainActivity, SettingsActivity, UpdatesActivity, DiagnosticsActivity, SafeModeActivity, Updater, Notifier, UpdateJobService, InstallResultReceiver, Diag, CrashGuard, Prefs, AppInfo, VersionUtil, Ui
- app/src/test: VersionUtilTest
- tools/Wizard.ps1 (menu: build and publish / follow running build / key location), Rebuild-APK.bat

## Features done (build 1, version 0.1.0)
- Role choice screen (saved only, no behaviour yet)
- Settings, App updates, Diagnostics, safe mode, settings backup

## Planned stages
1. Pairing (6-digit code, encrypted, local discovery via NSD), sound-triggered recording 15-60 s, parent list + slider, background service on child unit
2. Walkie-talkie
3. Live camera
4. Role swap and polish

## Known risks
- Android background limits: child unit must run a foreground service, battery optimisation off. Remote camera start from background may fail on Android 14+.
- Router AP/client isolation blocks local connections.
- Nothing in this build has been compiled or run by Claude. The first GitHub build is the real compiler.
