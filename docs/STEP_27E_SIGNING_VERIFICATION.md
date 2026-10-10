# STEP 27E — Persistent Signing Identity Verification

Date: 2026-10-10  
Status: **PASS**  
Scope: persistent Android upload/release signing only. This does not replace Play, OAuth, Billing, Internal Testing or physical-device certification.

## Canonical identities

- RC ID: `notification-control-v1.0.0-rc1`
- versionName: `1.0.0-rc1`
- versionCode: `1000001`
- package: `com.riccardopinato.notificationcontrol`
- certified application source: `3ff070fc3bb2b2122a2982c0eb449e3bd0eb7cb2`
- release control plane: `a233476455c48df06ef52f1eb02c28833369e344`
- Factory baseline: App-Factory-Core `a4a97ca79e85d2faebcdcc9a3ae4a9644f5caf03`

## GitHub signing verification

Verification workflow run: `38057014909`  
Verification branch head: `b93d8ba01ec0a88d4a4c1c95a89690fee245243d`

The verification was intentionally performed on a temporary branch so the diagnostic workflow itself did not modify the certified source or release control plane.

Verified conditions:

- all five required GitHub signing secrets are present;
- `ANDROID_KEYSTORE_BASE64` reconstructs the canonical Notification Control upload keystore;
- configured alias resolves to the canonical upload certificate;
- `EXPECTED_UPLOAD_CERT_SHA256` matches the canonical upload certificate;
- exact certified RC1 source builds successfully as signed release APK and signed AAB;
- APK signature verification passes;
- AAB JAR signature verification passes;
- RC identity verification passes;
- signing lane is `PRODUCTION`;
- signing identity match is `VERIFIED`;
- signing-side `RELEASE_PUBLISHABLE=true`.

## Upload certificate

SHA-256:

`AA:9A:47:C3:99:32:87:CC:32:A3:A2:B0:69:E6:B1:A2:A9:57:BB:EB:9E:A5:BD:BF:EB:D3:44:B3:8C:38:FA:3A`

SHA-1:

`26:BD:10:CA:4E:0C:37:AB:CC:A6:66:D5:03:6F:8D:D0:88:DD:3D:2E`

Important: this is the **upload-key certificate**. When Google Play App Signing is enabled, the certificate used to sign Play-distributed APKs can be different and its SHA-1/SHA-256 must be recorded separately.

## Exact signed verification artifacts

- APK SHA-256: `f881a1494cd0daf29a766dfbaa7393fc52c62d01b71f3849d5bc097397236726`
- AAB SHA-256: `acd9a7370cc5b15842cc9fd5b00a3d53ac0b9ae1ef1fca6e63b77df861b5938d`
- GitHub Actions artifact: `notification-control-rc1-signed-verification`
- artifact archive digest: `sha256:3227ceaf6cfa5d3e1463fa2e2cce634456f3a12896f1ed2d7b1a8049132a73c8`

These signed bytes prove the persistent signing configuration and identity. They do **not** by themselves prove Play distribution.

## Remaining external gates

Still required before final V1.0 CERTIFIED:

1. `PLAY_SERVICE_ACCOUNT_JSON` configured for the Play publication lane.
2. Google Play App Signing configured and signing certificate SHA-1/SHA-256 recorded.
3. Android OAuth client matched to the **Play App Signing** SHA-1 for Play-installed builds.
4. Play product `notification_control_premium` active with:
   - `monthly`
   - `annual`
5. Exact AAB submitted to Play Internal Testing.
6. Play-installed update/install verified.
7. Live purchase + restore verified.
8. Live Google profile verified on Play-installed signed build.
9. STEP 27B physical/OEM certification completed.

## Verdict

`STEP_27E_PERSISTENT_SIGNING_IDENTITY=PASS`

Persistent Android signing is no longer an external blocker. The remaining blockers are Play/store configuration and physical/runtime evidence.
