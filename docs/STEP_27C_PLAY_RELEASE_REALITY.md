# STEP 27C — Play Internal Testing & Release Reality

## Status

27C is split into what can be proven in repository/CI now and what requires
external Google Play / OAuth / physical evidence.

The automated source candidate remains:

- RC: `notification-control-v1.0.0-rc1`
- versionName: `1.0.0-rc1`
- versionCode: `1000001`
- certified source SHA:
  `3ff070fc3bb2b2122a2982c0eb449e3bd0eb7cb2`
- Android CI: `37911862397` PASS
- Certified QA: `37911862389` PASS
- AppLab FULL: `37911863392` PASS

The binding above lives in `release/RC_CERTIFICATION.env`.

## Control-plane vs source-plane

The latest `main` workflow may improve release machinery without mutating the
frozen RC source. For real release/internal delivery the workflow must:

1. load the control-plane certification record from current `main`;
2. checkout the exact `CERTIFIED_SOURCE_SHA` into a separate source directory;
3. verify the source RC identity matches the certification record;
4. build that exact source once;
5. hash the exact APK/AAB;
6. upload/distribute those same bytes.

This prevents a later workflow/documentation commit from silently becoming a
different application RC.

## Signing truth

A Play/Internal build is valid only when persistent Android signing credentials
exist. The workflow must fail closed for real distribution when signing is
missing.

Required GitHub secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON`

Recommended repository variable or secret:

- `EXPECTED_UPLOAD_CERT_SHA256`

When `EXPECTED_UPLOAD_CERT_SHA256` is configured, the signed APK certificate
digest must match before Play upload.

Do not confuse the upload-key certificate with the Google Play App Signing
certificate. OAuth for the Play-installed app must use the Play App Signing
SHA fingerprint.

## Billing contract

Repository contract:

- subscription product: `notification_control_premium`
- primary base plan: `monthly`
- secondary base plan: `annual`

Repository checks can prove only the IDs used by the app. They cannot prove that
the products/base plans are active in Play Console.

## Release privacy boundary

Public release remains least-privilege:

- notification media from notification payload when available;
- explicit SAF folder grant as fallback;
- broad MediaStore photo permissions removed from the release merged manifest;
- broad MediaStore recovery remains QA/debug-only unless a future policy review
  explicitly changes the product decision.

## Evidence states

Repository/CI can reach:

- static release identity: PASS;
- release media policy: PASS;
- billing contract IDs: PASS;
- exact certified source binding: PASS;
- signed artifact provenance: PASS only when signing secrets exist;
- Play upload: PASS only when the real upload action succeeds.

Still external/manual:

- Play App Signing fingerprint recorded;
- Android OAuth client matched to Play App Signing SHA;
- product/base-plan activation;
- Internal Testing installation/update;
- live purchase and restore;
- live Google profile;
- physical/OEM 27B matrix.

A successful Play API upload is **DISTRIBUTION SUBMITTED**, not
`DISTRIBUTION VERIFIED` until installation/update of the same artifact has been
observed.

## 27C verdict

Until the external/manual items exist:

`STEP_27C = BLOCKED_EXTERNAL_AND_MANUAL`

This is expected and is not a code failure.
