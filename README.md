# Notification Control

Android notification-control utility built around **Single Capture / Single Vault / Multiple Consumers**.

## Product baseline
- Kotlin + Jetpack Compose + Material 3
- one `NotificationListenerService` capture core with group-summary and logical replay deduplication
- Room local Vault with notification revisions, structured messaging content and FTS search
- local-first retention with per-revision WhatsApp/media archive previews
- Premium WhatsApp media capture/recovery with notification-first capture; public release uses explicit SAF fallback, while broad MediaStore recovery is QA/debug-only unless later Play-approved
- Premium Saved Vault Filters plus user-initiated readable CSV / structured JSON Vault export
- Notification Undo history semantics without falsely treating every removal as sender deletion
- Rules Engine, Critical Alert, Pausa Ping, Follow Up and Pickup Code
- Pausa Ping Free global cooldown plus Premium per-app rolling Notification Budget for Notification Control visual alerts
- Premium rotating protected Recovery Point before restore, with AES-GCM encrypted metadata, app-private media sidecars and one-step rollback of the last restore
- Luminous visual-alert module with Free baseline and Premium app/sender profiles
- optional Google profile identity via Credential Manager/Google ID 1.1.1 + stable sub/email token claims, separated from Vault security and Premium
- biometric/device-credential Vault gate
- streamlined core-only onboarding with sideload/restricted-settings recovery
- lazy screen-scoped data subscriptions and deferred heavy initialization for smoother runtime
- serialized notification hot path with reactive Rules/Critical/Luminous caches
- Paging 3 Vault with indexed Room v12 queries, transactional restore journal and incremental message FTS
- hardware-friendly Luminous renderer and on-demand WorkManager startup
- Luminous overlay compatibility preserved down to minSdk 24
- optimized installable `perfTest` APK plus Macrobenchmark performance harness
- isolated `qaPremium` APK for owner testing; production entitlement remains Play Billing-only
- encrypted manual backup/restore through Android Storage Access Framework
- subscription-first Google Play Billing entitlement engine with canonical monthly + annual base plans; Lifetime remains optional compatibility
- QA-only physical reliability diagnostics for Notification Listener, Android 14+ photo scope, SAF and MediaStore evidence
- EN/IT/ES/FR/PT resources and per-app language override
- automatic Android cloud backup disabled

## Release and QA
- `.github/workflows/android.yml`: standard PR/main CI plus debug/perfTest APKs and benchmark compile gate
- `.github/workflows/release.yml`: release-reality pipeline that checks out the exact certified RC source, builds once, records signing/artifact provenance, can create an RC prerelease and can submit the same hashed AAB to Play Internal Testing
- `.github/workflows/certified.yml`: SHOS-style Certified Evidence Bundle with tests, lint, release binaries, privacy/localization gates, checksums and dependency evidence
- `.github/workflows/applab.yml`: pinned AppLab FULL trusted-runtime gate for PRs, covering emulator launch, visual/runtime checks, persistence, lifecycle stress, process death, background/Doze and storage integrity where supported

See:
- `docs/PRODUCT_BIBLE.txt`
- `docs/ROADMAP.txt`
- `docs/RELEASE_CERTIFIED_QA.md`
- `docs/STEP_27E_SIGNING_VERIFICATION.md`
- `PROJECT_RULES.txt`
- `AGENTS.txt`

## External release configuration
Persistent Android signing for the exact STEP 27A RC1 source is verified: GitHub reconstructs the canonical upload keystore, the APK/AAB signatures pass, and the upload certificate matches `EXPECTED_UPLOAD_CERT_SHA256`. Remaining release configuration is Play-specific: `PLAY_SERVICE_ACCOUNT_JSON`, Play App Signing certificate capture, Android OAuth bound to the Play signing SHA-1, activation of `notification_control_premium` (`monthly` + `annual`) and real Internal Testing. STEP 27C binds distribution to the immutable STEP 27A certified source SHA. Public release defaults to notification-direct media plus explicit SAF rather than broad gallery access. Physical/OEM and Play-installed live behavior remain separate required evidence before final V1.0 certification.
