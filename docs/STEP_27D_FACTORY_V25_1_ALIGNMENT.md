# STEP 27D — App Factory v25.1 Alignment

Pinned Factory baseline:

- repository: `riccardopinato/App-Factory-Core`
- source SHA: `a4a97ca79e85d2faebcdcc9a3ae4a9644f5caf03`
- Master Prompt: `25.1`
- Golden Index: `10`
- Golden Registry: `2`

This alignment follows the App Factory hierarchy without replacing Notification
Control's Product Bible, roadmap or implementation truth.

## Golden delta applied

### CI + Android Release + Web Deploy v2 — applied where compatible

Notification Control is native Kotlin/Compose, so Flutter/Web-specific template
steps are not copied. The reusable supply-chain rules are applied:

- `actions/checkout` pinned to the Golden v2 verified immutable SHA;
- `actions/upload-artifact` pinned to the Golden v2 verified immutable SHA;
- every external/reusable `uses:` reference remains a 40-character immutable SHA;
- the local verifier fails CI when these pins drift.

### Android Signing / Release Identity — extended

Existing STEP 27C exact-source and same-artifact rules remain canonical.

Additional hardening:

- signing lane is recorded separately from signing status;
- signed Certified artifacts are not called publishable unless the expected
  production/upload certificate SHA-256 is configured and matches;
- APK and AAB signatures are both verified when release signing exists;
- temporary CI keystore is deleted with an `always()` cleanup;
- Factory baseline and action provenance are included in evidence.

### Release Reality / AppLab — already stronger than the candidate Golden

Kept as-is:

- immutable source/artifact binding;
- CI / Certified / AppLab evidence separation;
- physical/OEM STEP 27B remains MANUAL_REQUIRED;
- Play/OAuth/Billing live evidence remains external/manual;
- BUILD GREEN is not promoted to PHYSICAL DEVICE VERIFIED.

### Capability / Permission / Health — no duplicate subsystem

Current implementation already separates:

- Notification Listener permission;
- listener connected runtime state;
- connection/event/reconciliation timestamps;
- Android 14 photo scope;
- SAF state;
- QA-only diagnostics.

The app resets listener `connected=false` at process start, preventing stale
persisted connection state from being shown as current health. No second health
coordinator is introduced.

### Android Native Reliability — existing architecture retained

Current Single Capture / Single Vault / Multiple Consumers architecture already
satisfies the single-owner intent. WorkManager recovery is unique/bounded and
STEP 26 already reduced retry/wakeup amplification. Physical OEM behavior
remains deferred, not waived.

### Entitlement Contract — existing architecture retained

Current implementation already has:

- centralized `EntitlementStore`;
- provider adapter in `PlayBillingManager`;
- 72-hour offline subscription grace;
- centralized `ProductLimits`;
- QA Premium override isolated by build variant;
- downgrade without user-data deletion.

No provider-agnostic duplicate entitlement model is added.

## Freshness boundary

At Factory baseline `a4a97ca…`, only the CI/Release Golden v2 among the
provider-sensitive Goldens used here is marked `CURRENT`.

Notification reliability, capability health, native reliability, signing and
entitlement are marked `NEEDS_REVIEW`. They are therefore used only as
architecture/process guidance; they are **not** treated as current provider
certification.

## Evidence

`release/FACTORY_BASELINE.env` is the app-local provenance snapshot.

`scripts/verify_factory_alignment.py` verifies deterministic local alignment
without fetching App-Factory-Core on every CI run.

This preserves the Master rule:

pinned baseline != live-currentness proof.
