# Releasing Emersion Chess to Google Play

## One-time: create your signing key (KEEP IT FOREVER)
On any machine with Java:
    keytool -genkeypair -v -keystore gambit-release.jks -alias gambit \
      -keyalg RSA -keysize 2048 -validity 10000
Store the .jks file and both passwords in a password manager. Losing them
means you can never update the app again (unless you enroll in Play App
Signing, which is recommended at first upload — Play then holds the app key
and your keystore becomes the upload key).

## One-time: switch on online play for store users
Set repo secrets EMERSION_ONLINE_PROJECT_ID and EMERSION_ONLINE_API_KEY (see
SERVER_SETUP.md, "Shipping to app-store users"). Skip this and the store build
ships with online play showing a setup notice instead.

## One-time: give CI the key (4 repository secrets)
Repo -> Settings -> Secrets and variables -> Actions -> New repository secret:
- EMERSION_KEYSTORE_B64        the .jks file, base64-encoded (single line)
- EMERSION_KEYSTORE_PASSWORD
- EMERSION_KEY_ALIAS           e.g. gambit
- EMERSION_KEY_PASSWORD       (PKCS12: SAME value as the keystore password)

To produce the base64 on a computer:  base64 -w0 gambit-release.jks
No computer? Run the "generate-keystore" workflow (Actions tab) with your two
passwords as inputs; download the keystore-b64 artifact it produces, open the
file, and paste its single line into EMERSION_KEYSTORE_B64. Keep a copy of the
artifact somewhere safe — it IS your upload key.

Every push then uploads a Play-ready signed .aab as the gambit-release-aab
artifact. Enroll in Play App Signing at first upload (recommended): Google
holds the real app key and this keystore is only your upload key.

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
