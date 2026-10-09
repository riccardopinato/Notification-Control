# STEP 27A — V1.0 RC1 Freeze & Automated Certification

## Canonical RC

- RC ID: `notification-control-v1.0.0-rc1`
- Android `versionName`: `1.0.0-rc1`
- Android `versionCode`: `1000001`
- Source of truth: `release/RC.properties`
- Branch: `step-27a-v1-rc1-automated-certification`

The RC source commit is immutable for certification purposes: if application code,
build logic, tests, workflows, resources, manifests or release documentation change,
the source SHA changes and all mandatory automated gates must pass again.

## 27A mandatory automated gates

The same RC source SHA must be green in:

1. Android CI.
2. Certified QA.
3. AppLab FULL Runtime Gate.

Each workflow verifies the canonical RC identity before executing its main checks.
Certified QA additionally verifies the built release APK metadata and records SHA-256
checksums for the release APK/AAB and the source SHA in the Evidence Bundle.

## Artifact truth

The release APK/AAB are built from the frozen RC source SHA by Certified QA.
AppLab intentionally exercises the installable `qaPremium` release-like variant so
Premium/native flows can be tested without pretending that an unsigned public
release artifact is installable or publishable.

Therefore the 27A invariant is **same RC identity + same source SHA**, not a false
claim that the AppLab QA APK and public release APK are byte-identical.

If production signing secrets are unavailable, release outputs remain
`UNSIGNED_VALIDATION` and `release_publishable=false`. That does not invalidate
27A automated source certification, but it blocks store/release certification in
27C.

## Evidence contract

Certified QA must record:

- RC ID, versionName and versionCode;
- exact Git source SHA;
- workflow run ID/ref/event;
- release APK SHA-256;
- release AAB SHA-256;
- signing state and certificate digest when available;
- size-budget result;
- privacy/manifest gates;
- dependency and Room schema evidence;
- automated verdict boundaries.

Android CI records RC identity and hashes for the QA/perf validation artifacts.
AppLab is accepted only when its run resolves to the identical RC source SHA.

## PASS boundary

`STEP_27A_AUTOMATED=PASS` means only:

- canonical RC identity is internally consistent;
- Android CI is green on the frozen SHA;
- Certified QA is green on the frozen SHA;
- AppLab FULL is green on the frozen SHA;
- release validation artifacts and checksums exist for that SHA.

It does **not** mean V1.0 is production certified.

Still required outside 27A:

- STEP 23/26 physical Xiaomi/Samsung/Android 14+ evidence;
- long-run/Doze/reboot/battery and real notification/media validation;
- Play signing and Play Internal Testing;
- live Google OAuth signing match;
- live Billing purchase/restore;
- final store/privacy/data-safety verification.

Those remain 27B/27C gates.
