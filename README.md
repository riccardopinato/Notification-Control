# Notification Control

Android notification-control utility built around **Single Capture / Single Vault / Multiple Consumers**.

## Baseline implemented on development branch
- Kotlin + Jetpack Compose + Material 3
- `NotificationListenerService` capture core
- Room local Vault
- structured `MessagingStyle` message extraction
- posted/updated/removed lifecycle + removal reason when Android provides it
- listener reconnect reconciliation with active notifications
- Free monitored-app limit: 3, centralized in product policy
- local thumbnail extraction for notification `EXTRA_PICTURE` (light WebP preview)
- daily retention worker: Free 7 days; Premium policy infrastructure 1/3/7/30/forever
- protected items excluded from automatic cleanup
- Luminous donor refactored into shared flash coordinator + OLED overlay + face-down/proximity suppression
- Battery Guard respects charging state
- EN/IT/ES/FR/PT resource baseline with device locale default
- initial 4-step onboarding and Profile control center
- automatic Android cloud backup disabled

## Planned next
Rules Engine, Critical Alert, Follow Up, Pausa Ping, Pickup Code, Google Sign-In optional profile identity, biometric Vault gate, Play Billing entitlements and deeper media/storage controls.

See `docs/PRODUCT_BIBLE.txt`, `docs/ROADMAP.txt`, `PROJECT_RULES.txt`, `AGENTS.txt`.
