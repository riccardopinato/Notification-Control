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

Without them, only a manually dispatched **unsigned-validation** artifact may be produced. Tagged public releases and Play uploads now fail closed unless Android signing is configured.

## Play Internal Testing
Optional upload uses:
- `PLAY_SERVICE_ACCOUNT_JSON`
- configured Google Play application/product records.

The upload step runs only when signing and Play credentials are available.

## Google identity
Live Sign in with Google additionally requires the OAuth Web client ID in `google_web_client_id`. Identity remains optional and separate from Vault security and Premium entitlement.

## Certified QA
`Certified QA` runs unit tests, release lint, release APK/AAB validation builds, privacy/manifest gates, localization gates and CI provenance capture. Its release binaries are explicitly classified as **UNSIGNED_VALIDATION**, not store-ready releases. It produces a `notification-control-certified-evidence` artifact containing reports, binaries, dependency evidence, checksums, provenance and a verdict file.

## AppLab trusted runtime
PRs also run the pinned AppLab native Android FULL gate against the release-like `qaPremium` APK. The trusted verifier covers runtime launch, visual/system checks, persistence/restart, lifecycle stress, process death, background/Doze and storage integrity where the hosted Android environment supports them. Notification Listener secure access and real WhatsApp delivery still require physical-device validation.

Automated PASS does not substitute for physical-device validation. Final product verdict remains **BLOCKED** until required manual/device checks and external store/OAuth configuration are completed for the intended release.


## v1 RC media-permission split
The publishable release variant is least-privilege: it does **not** carry
READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED or legacy READ_EXTERNAL_STORAGE.
Production WhatsApp recovery therefore relies on notification-provided media plus the
explicit user-authorized SAF WhatsApp Images folder.

The non-publishable qaPremium validation variant intentionally retains broad image
permission and enables BROAD_MEDIA_RECOVERY_ALLOWED so the physical certification
matrix can still exercise the MediaStore fallback. Certified QA verifies this split in
the merged manifests. This permission in QA must never be interpreted as permission
to ship broad gallery access in the Play release.

Default validation version is 1.0.0-rc.1; the release workflow still injects explicit
VERSION_NAME and VERSION_CODE for signed/tagged artifacts.
