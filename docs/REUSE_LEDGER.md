# Notification Control — Reuse & Provenance Ledger

Updated: 2026-10-03

This ledger records reusable sources and infrastructure used by Notification Control.
It is intentionally separate from runtime dependency resolution.

## Product donor

### The Luminous Infinite Circle
- Classification: internal / user-owned donor project.
- Role: primary product donor for the Luminous visual-alert module.
- Reused concepts: flash coordination, circular visual alert, OLED-black presentation,
  pixel shifting, proximity/pocket awareness and quiet/battery behavior.
- Notification Control status: donor concepts were audited and adapted to the canonical
  Single Capture / Single Vault / Multiple Consumers architecture.
- Important corrections made in Notification Control:
  - real gravity/accelerometer face-down detection;
  - coherent suppression for proximity/face-down;
  - no claim that the overlay is hardware/system AOD;
  - entitlement is kept outside the renderer;
  - notification data is sourced from the canonical Vault/capture pipeline.
- Third-party ownership: no third-party donor ownership is recorded for this donor.

## Runtime libraries

Runtime libraries are declared through Gradle/version catalogs and are not copied into
the repository as donor source. Certified QA records the resolved release runtime
dependency inventory for every audited commit.

Primary families currently include:
- AndroidX / Jetpack;
- Jetpack Compose / Material 3;
- Room / Paging / WorkManager;
- AndroidX Biometric / AppCompat / Core;
- Kotlin Coroutines;
- Google Play Billing;
- Android Credential Manager / Google ID integration.

Each component remains subject to its own license or SDK terms. Notification Control
does not relicense those components.

## QA and development infrastructure

### AppLab
- Repository: riccardopinato/AppLab
- Classification: external project infrastructure, not consumer-app runtime code.
- Consumption: reusable GitHub Actions native Android verifier pinned to an immutable
  commit SHA in .github/workflows/applab.yml.
- Shipped in APK/AAB: no.

### GitHub Actions
- Workflow actions are pinned to immutable commit SHA where used by this repository.
- Certified QA records CI provenance in the evidence bundle.

## Rules for future reuse

Before adding copied code or a new donor:
1. record source/repository and ownership;
2. record exact commit/tag when applicable;
3. record license/terms compatibility;
4. prefer adaptation behind existing canonical interfaces over architectural duplication;
5. update this ledger in the same PR as the reuse.
