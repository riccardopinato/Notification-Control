# Notification Control — Release & Certified QA

## Release outputs
The release workflow builds:
- release APK;
- release AAB;
- R8 mapping file;
- SHA-256 checksums.

Version data is injected with `VERSION_NAME` and `VERSION_CODE`. A tagged `v*` build can create/update a GitHub Release. Manual dispatch accepts explicit version name/code.

## Signing
Repository code contains no signing material. Configure these GitHub Actions secrets for signed release artifacts:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Without them the release workflow can still compile release outputs, but they are not eligible for Play publication.

## Play Internal Testing
Optional upload uses:
- `PLAY_SERVICE_ACCOUNT_JSON`
- configured Google Play application/product records.

The upload step runs only when signing and Play credentials are available.

## Google identity
Live Sign in with Google additionally requires the OAuth Web client ID in `google_web_client_id`. Identity remains optional and separate from Vault security and Premium entitlement.

## Certified QA
`Certified QA` runs unit tests, release lint, release APK/AAB builds, privacy/manifest gates and localization gates. It produces a `notification-control-certified-evidence` artifact containing reports, binaries, dependency evidence, checksums and a verdict file.

Automated PASS does not substitute for physical-device validation. Final product verdict remains **BLOCKED** until required manual/device checks and external store/OAuth configuration are completed for the intended release.
