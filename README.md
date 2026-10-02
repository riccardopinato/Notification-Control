# Notification Control

Android notification-control utility built around **Single Capture / Single Vault / Multiple Consumers**.

## Product baseline
- Kotlin + Jetpack Compose + Material 3
- one `NotificationListenerService` capture core
- Room local Vault with notification revisions, cumulative structured messaging content and FTS search
- capture-quality hardening for group summaries, fallback message identity and remove/repost replay deduplication
- local-first retention and lightweight media previews
- Notification Undo history semantics without falsely treating every removal as sender deletion
- Rules Engine, Critical Alert, Pausa Ping, Follow Up and Pickup Code
- Luminous visual-alert module with Free baseline and Premium app/sender profiles
- optional Google profile identity separated from Vault security and Premium
- biometric/device-credential Vault gate
- encrypted manual backup/restore through Android Storage Access Framework
- Google Play Billing entitlement engine with subscription/lifetime support
- EN/IT/ES/FR/PT resources and per-app language override
- automatic Android cloud backup disabled

## Release and QA
- `.github/workflows/android.yml`: standard PR/main CI
- `.github/workflows/release.yml`: APK/AAB release build, optional signing, GitHub Release and optional Play Internal Testing upload
- `.github/workflows/certified.yml`: SHOS-style Certified Evidence Bundle with tests, lint, release binaries, privacy/localization gates, checksums and dependency evidence

See:
- `docs/PRODUCT_BIBLE.txt`
- `docs/ROADMAP.txt`
- `docs/RELEASE_CERTIFIED_QA.md`
- `PROJECT_RULES.txt`
- `AGENTS.txt`

## External release configuration
Live Google Sign-In, Premium purchases and Play publication require their corresponding OAuth / Play Console / signing credentials. The app remains local-first and usable without Google identity.
