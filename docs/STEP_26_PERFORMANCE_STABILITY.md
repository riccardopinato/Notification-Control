NOTIFICATION CONTROL — STEP 26 PERFORMANCE, BATTERY & LONG-RUN STABILITY
Version: 1.0
Status: IMPLEMENTED / AUTOMATED VALIDATION + PHYSICAL EVIDENCE REQUIRED

======================================================================
1. SCOPE
======================================================================

Step 26 hardens the app before the v1.0 release candidate without changing
product semantics.

Automated scope:
- notification pipeline runtime telemetry in debug/QA builds;
- queue depth/high-water and command latency evidence;
- media-recovery wakeup/run telemetry;
- coalesced MediaStore observer recovery;
- coalesced unique WorkManager media recovery;
- large-Vault SQLite query-plan/profile regression test;
- cold + warm startup Macrobenchmark harness;
- regular + sustained navigation/jank Macrobenchmark harness;
- release APK/AAB binary size budgets;
- AppLab FULL performance/resource/background/process-death evidence.

Physical-only scope:
- Xiaomi/HyperOS and Samsung/One UI macrobenchmark execution;
- long Doze/App Standby/reboot soak;
- real notification burst latency;
- OEM battery restrictions;
- real memory/leak observation over extended sessions.

======================================================================
2. RELEASE OVERHEAD RULE
======================================================================

PERFORMANCE_DIAGNOSTICS_ENABLED:
- release: false;
- debug: true;
- perfTest: true;
- qaPremium: true.

Runtime counters therefore do not collect in the public release build.

No persistent analytics, network upload, SDK or third-party telemetry is added.

======================================================================
3. NOTIFICATION HOT-PATH TELEMETRY
======================================================================

QA diagnostics record:
- current listener-command queue depth;
- queue high-water mark;
- POSTED / REMOVED / RECONCILE command counts;
- processed command count;
- average and maximum command latency;
- commands >= 250 ms;
- MediaStore observer signal count;
- media-recovery run count;
- average and maximum media-recovery duration;
- pending recovery count from the last run.

The telemetry is process-local diagnostic evidence only.

Acceptance:
- no unbounded queue growth during a controlled burst;
- no sustained increase after the burst drains;
- investigate any repeated >=250 ms command on a physical device;
- capture screenshots/logs from the QA diagnostics card for physical evidence.

======================================================================
4. MEDIA RECOVERY BATTERY POLICY
======================================================================

Media recovery keeps the existing reliability model but reduces redundant work.

Rules:
- notification registration schedules a short 250 ms coalesced recovery;
- MediaStore observer signals schedule a 750 ms coalesced recovery;
- a newer signal replaces the pending in-process debounce job;
- reconnect performs one immediate reconciliation/recovery;
- WorkManager unique work uses KEEP instead of REPLACE;
- repeated enqueue requests must not restart an already queued/running retry chain;
- rescue cleanup is performed by the media worker only when pending recovery is
  drained, and remains covered by the periodic retention worker.

No network constraint is required because media recovery is local-only.

======================================================================
5. LARGE VAULT DATABASE GATE
======================================================================

VaultHotPathQueryPlanTest seeds 5,000 Vault rows and verifies:
- capture lookup uses a platform-key index;
- app-filter paging uses packageName + updatedAt index;
- 100 indexed active-notification lookups complete inside a deliberately loose
  5 s CI guardrail.

The timing value is profiling evidence, not a device-performance claim.
Physical latency remains a separate acceptance gate.

======================================================================
6. MACROBENCHMARK MATRIX
======================================================================

Harness scenarios:
- coldStartup;
- warmStartup;
- tabSwitchAndVaultScrollFrames;
- sustainedNavigationAndVaultScrollFrames.

The sustained scenario repeats Vault/Rules/Profile/Home navigation and Vault
scrolling inside each measurement iteration.

CI compiles the harness.
Runtime Macrobenchmark execution remains MANUAL_REQUIRED because stable frame
and startup claims require a controlled physical device or dedicated benchmark
environment after one-time onboarding.

======================================================================
7. SIZE BUDGET
======================================================================

Certified QA hard limits:
- release APK <= 8 MiB;
- release AAB <= 12 MiB.

Evidence:
- certified-evidence/SIZE_BUDGET.txt

A budget increase requires an explicit product rationale. Do not raise the
limit merely to make CI green.

======================================================================
8. LONG-RUN / PHYSICAL ACCEPTANCE
======================================================================

Run on at least:
- Xiaomi / HyperOS;
- Samsung / One UI;
- one Android 14+ device.

Minimum physical scenarios:
A. 100+ notification burst with mixed POSTED/REMOVED events.
B. 30 min normal use with repeated Home/Vault/Rules/Profile switching.
C. WhatsApp media burst with observer + WorkManager recovery.
D. Doze + App Standby, then Follow Up/Critical due time.
E. process kill and listener reconnect.
F. reboot and listener/recovery verification.
G. 30 min screen-off/background observation.
H. thumbnail-heavy Vault scrolling/search.
I. Luminous overlay/flash enable-disable cycles.

Record:
- queue high-water;
- average/max command latency;
- slow-command count;
- media observer signals vs recovery runs;
- cold/warm startup benchmark output;
- frame timing;
- memory before/after soak;
- battery delta when meaningful;
- crash/ANR/logcat evidence.

======================================================================
9. MEMORY / LEAK BOUNDARY
======================================================================

Automated AppLab evidence covers:
- PSS/RSS snapshot;
- process death/recovery;
- resource pressure;
- background/Doze;
- storage/restart.

It does not prove absence of leaks over hours.

Final v1.0 certification therefore requires a physical soak with memory sampled
before/after repeated thumbnail, overlay, sensor and notification-burst flows.
A materially monotonic memory increase must be investigated before Step 27.

======================================================================
10. VERDICT SEMANTICS
======================================================================

Automated PASS means:
- unit tests green;
- query-plan gate green;
- lint/build green;
- size budget green;
- AppLab FULL green for applicable automated runtime labs.

It does NOT mean:
- physical Macrobenchmark PASS;
- OEM battery PASS;
- leak-free long soak;
- Play/Internal Testing PASS.

Those remain MANUAL_REQUIRED / EXTERNAL_REQUIRED until actual evidence exists.
