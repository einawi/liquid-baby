# Liquid Baby: checklist before each upload

Use this list before every upload to GitHub Actions. Tick each item.

1. Version
   - [ ] `appVersionCode` in `app/build.gradle.kts` is higher than the last uploaded build.
   - [ ] `versionName` matches the release notes heading.
2. Release notes
   - [ ] `RELEASE_NOTES.md` has a new entry at the top.
   - [ ] The entry is in English and Arabic.
3. Code
   - [ ] Every changed file is a full file in the zip (no partial snippets).
   - [ ] No duplicate imports; no unused imports in files you touched.
   - [ ] No new `requestPermissions` or `startActivityForResult` calls (use the Activity Result API).
4. Build
   - [ ] GitHub Actions compiles. If it fails, paste the first error line and the file name.
5. Device test (on the phone that runs the build)
   - [ ] App starts; the role screen or the current role screen opens.
   - [ ] Pairing works (only if you changed pairing, talk or the connection).
   - [ ] The screen you changed behaves as expected in both languages.
   - [ ] Diagnostics shows no new errors after the test.
6. Ship
   - [ ] Zip named with the next number (for example LiquidBaby_NN.zip).
   - [ ] Update notes sent to the user in both languages.
