# Releasing Gambit Chess to Google Play

## One-time: create your signing key (KEEP IT FOREVER)
On any machine with Java:
    keytool -genkeypair -v -keystore gambit-release.jks -alias gambit \
      -keyalg RSA -keysize 2048 -validity 10000
Store the .jks file and both passwords in a password manager. Losing them
means you can never update the app again (unless you enroll in Play App
Signing, which is recommended at first upload — Play then holds the app key
and your keystore becomes the upload key).

## One-time: give CI the key (4 repository secrets)
Repo -> Settings -> Secrets and variables -> Actions:
- GAMBIT_KEYSTORE_PATH      e.g. /home/runner/gambit-release.jks
- GAMBIT_KEYSTORE_PASSWORD
- GAMBIT_KEY_ALIAS          gambit
- GAMBIT_KEY_PASSWORD
Then add a checkout-adjacent step or commit the keystore ENCRYPTED — simplest
robust route: base64 the .jks into a 5th secret GAMBIT_KEYSTORE_B64 and add
`echo "$GAMBIT_KEYSTORE_B64" | base64 -d > $GAMBIT_KEYSTORE_PATH` before the
release step. (Without secrets, CI still produces a DEBUG-SIGNED release build
so R8 is exercised on every push.)

## Every release
1. Bump versionCode (+1 always) and versionName in app/build.gradle.kts.
2. Push. Download `gambit-release-aab` from the Actions run.
3. Play Console -> Production -> Create release -> upload the .aab.
4. Store listing assets live in store/ (feature graphic, descriptions).
5. Privacy policy: host PRIVACY.md via GitHub Pages and paste its URL in
   Play Console -> App content -> Privacy policy.

## Current status checklist
[x] targetSdk 35  [x] R8 exercised in CI  [x] adaptive+monochrome icon
[x] versionCode 2 / 1.1  [x] GPL notices (THIRD_PARTY_LICENSES.md)
[ ] keystore + secrets (yours)  [ ] Play Console account ($25 once)
