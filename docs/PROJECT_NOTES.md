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
- app/src/main/java/com/liquid/liquidbaby: App, MainActivity, SettingsActivity, UpdatesActivity, DiagnosticsActivity, SafeModeActivity, ChildActivity, ParentActivity, UnitService, ChildEngine, ParentEngine, AudioMonitor, Discovery, Net, Conn (+ message types), Crypto (+ Aead), Store (PairStore), Hex (+ Wire, Wav, SoundDetector), Updater, Notifier, UpdateJobService, InstallResultReceiver, Diag, CrashGuard, Prefs, AppInfo, VersionUtil, Ui
- app/src/test: VersionUtilTest, CoreTest
- tools/Wizard.ps1 (menu: build and publish / follow running build / key location), Rebuild-APK.bat

## Features done
Build 1 (0.1.0): role choice, Settings, App updates, Diagnostics, safe mode, settings backup. Installed and opened OK on Pixel 7 Pro and Galaxy J50 (Android 6.0.1).
Build 2 (0.2.0): pairing, child listening + sound-triggered recording, parent recordings list + sliders, foreground service. In-app update from build 1 to build 2 worked on the Pixel (Android 17, API 37).
Build 7 (0.2.5): deleted clips came back because SYNC used the newest clip still on the parent's disk. Parent now stores lastSyncTs in PairStore (max timestamp ever received). SYNC(-1) = fresh pairing: child answers SYNC_ACK(newest clip ts) and sends nothing old; parent stores max(1, ack). Old SYNC semantics kept for ts >= 0.
Build 6 (0.2.4): Galaxy J5 (Android 6.0.1) child screen showed "Wi-Fi: not found" although Wi-Fi was connected (cause not known: Net.wifiNetwork() found no Wi-Fi network on that phone). Net.wifiIp() now falls back to the wlan interface and then WifiManager; Net.vpnActive() also looks for tun/ppp interfaces; Diagnostics now lists the networks Android reports (Net.dump) so the cause can be read from the next copy. Pinning to Wi-Fi is skipped when no Wi-Fi network object is found (fine without a VPN).
Build 5 (0.2.3): Galaxy J5 (Android 6.0.1) could check for updates but the download failed with CertPathValidatorException (Trust anchor not found) on the release-assets.githubusercontent.com host. Added TrustFallback (res/raw/cacert.pem = Mozilla roots from certifi, 118 certs) used by Updater after an SSLException. The J5 must be updated by copying the APK once (it cannot download the fix itself).
Build 4 (0.2.2): VPN-blocking warning on both screens. The child unit recorded clips fine in build 2/3 on the Xiaomi (audio part works).
Build 3 (0.2.1): fixes after the first Pixel test. The user typed 129.168.1.101 instead of 192.168.1.101 (manual IP box now prefilled with the Wi-Fi prefix, validated, same-/24 warning). Pinning an already-bound UDP socket to Wi-Fi gave EPERM on Android 17, so UDP sockets are now created unbound, pinned, then bound (still falls back to unpinned). Updater: one download at a time (two threads collided), fallback to the github.com/releases/latest redirect + raw RELEASE_NOTES.md when the API answers 403/429.

## Network design (build 2)
- One persistent TCP connection (port 45455) per parent phone; the PARENT connects to the CHILD (child = server). Everything else (clips, settings, ping) travels on it. Future walkie-talkie and camera will use the same connection.
- Discovery: parent sends a UDP broadcast "LBQ1" to port 45454, child answers "LBR1|id|name|port". Fallback: parent types the IP shown... (the child screen shows its Wi-Fi IP in the status text).
- VPN: client and UDP sockets are pinned to the Wi-Fi network with Network.bindSocket(). TEST RESULT (build 3, Pixel 7 Pro Android 17 + Xiaomi M2003J15SC Android 12, Proton VPN free): with a VPN on, bindSocket fails with EPERM (Android only allows it if the VPN app allows bypass), the TCP connect leaves from the tunnel address 10.2.0.2 and times out, UDP discovery finds nothing. So with such a VPN the local connection is impossible: the VPN must be OFF (or a VPN with split tunneling / allow-LAN) on the phones. Build 4 detects this (Net.vpnBlocksLocal) and shows a warning + button to the VPN settings. With the VPN off, pinning works.
- Pairing: ECDH (secp256r1). Both phones show the same 6-digit number derived from the shared secret (SAS); the user checks it matches and both sides confirm. Long-term key = SHA-256 of the shared secret, stored in the private "pairing" SharedPreferences (not part of the settings export). Child accepts at most 2 parents, only while "Allow a new parent phone" is on (2 minutes).
- Login: parent sends parentId + nonce; child answers with its nonce; session key = HMAC-SHA256(pairKey, "LBSESS"+nc+ns); all later frames are AES-256-GCM with counter nonces (direction prefix 0 = parent to child, 1 = child to parent).
- Frame: [4-byte length][type + payload], encrypted after login. Message types are in Conn.kt (object T).
- Recording: AudioRecord 16 kHz mono, 100 ms blocks, SoundDetector (3 loud blocks in a row, adaptive noise floor, 5 sensitivity levels), 2 s pre-roll, clip length 15-60 s set from the parent. Stored as WAV (about 1.9 MB per minute). Child keeps the last 30 clips, parent keeps 100. On connect the parent sends SYNC with its newest clip time and gets missed clips (max 20).
- Child listens continuously (no on/off interval yet; the interval idea is still to be discussed).
- UnitService (foreground): child = microphone type, parent = connectedDevice type; holds wake lock, Wi-Fi lock, multicast lock. After a phone restart the user must open the app (Android does not allow starting the microphone from boot).

## Planned stages
2. Walkie-talkie (parent voice to child speaker, over the same connection)
3. Live camera
4. Role swap and polish; listening interval; clip compression; more backups

## Open questions / not tested yet
- Nothing in this build has been run on a phone by Claude. Unit tests and a loopback test of the encrypted handshake were run on a JVM; the Android code was only type-checked.
- VPN cases (child with VPN is the least certain), router client isolation, battery optimisation on each brand, sensitivity levels in a real room.

## Planned stages
1. Pairing (6-digit code, encrypted, local discovery via NSD), sound-triggered recording 15-60 s, parent list + slider, background service on child unit
2. Walkie-talkie
3. Live camera
4. Role swap and polish

## Known risks
- Android background limits: child unit must run a foreground service, battery optimisation off. Remote camera start from background may fail on Android 14+.
- Router AP/client isolation blocks local connections.
- Claude cannot run the app; the GitHub build is the real compiler.
