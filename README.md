# Notification Control

Android notification control utility built around a local-first **Single Capture / Single Vault / Multiple Consumers** architecture.

## Current baseline

- Kotlin + Jetpack Compose + Material 3
- Android `NotificationListenerService` capture core
- Room local notification Vault
- structured `MessagingStyle` extraction
- notification posted/updated/removed lifecycle
- reconnect reconciliation using active notifications
- Free monitored-app limit: 3
- Smart Retention infrastructure
- Luminous flash/AOD donor integrated as the visual-alert module
- EN / IT / ES / FR / PT resource baseline, using device locale by default
- no mandatory login and no cloud requirement for core use

## Product modules

`Vault / Notification Undo` · `Rules Engine` · `Critical Alert` · `Follow Up` · `Pausa Ping` · `Pickup Code` · `Luminous`

See `docs/PRODUCT_BIBLE.txt`, `docs/ROADMAP.txt`, `PROJECT_RULES.txt` and `AGENTS.txt` before implementing new features.

## Privacy baseline

Notification content is stored locally. Android automatic cloud backup is disabled. Google Sign-In will remain optional and separate from local Vault protection.

## Build

CI installs Gradle 9.3.1 and runs unit tests, lint and a debug APK build. The donor ZIP did not include the Gradle wrapper JAR, so the repository intentionally does not rely on `./gradlew` yet.

## Web preview

Not provided for this native Android utility: its core value depends on Android-only notification listener, overlay, camera/flash, alarms and sensor APIs. UI-only web emulation may be considered later, but it is not treated as functional verification.
