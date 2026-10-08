# Notification Control — Step 23 Physical Certification

Status: tooling implemented; physical evidence still required.

## Scope

The QA Premium build exposes a device-local diagnostic card with:
- manufacturer/model/API level;
- Notification Listener permission and live connection state;
- last listener connection, event and reconciliation timestamps;
- Android photo access classification: FULL / SELECTED_ONLY / NONE;
- persisted WhatsApp SAF folder access;
- MediaStore version/generation when full photo access is available.

This tooling does not replace real-device evidence.

## Required physical matrix

Minimum:
1. Xiaomi / HyperOS device.
2. Samsung / One UI device.
3. Android 14+ device for selected-photo permission behavior.

## Notification Listener scenarios

For each device:
1. fresh install and grant Notification Access;
2. receive ordinary notification;
3. receive WhatsApp text notification;
4. dismiss notification;
5. force-stop source app where relevant;
6. kill Notification Control process;
7. reopen and verify listener reconciliation;
8. reboot device;
9. verify listener reconnect and new capture;
10. verify no duplicate Vault events after reconnect/replay.

Evidence:
- diagnostic card before/after;
- Vault event count;
- listener timestamps;
- no crash/ANR.

## WhatsApp media scenarios

Test independently:
1. media exposed directly in notification;
2. MediaStore fallback with FULL photo access;
3. Android 14+ SELECTED_ONLY photo access: must not be treated as full MediaStore recovery;
4. SAF WhatsApp Images fallback with persisted read permission;
5. multi-photo burst;
6. delayed MediaStore indexing;
7. ambiguous candidates -> Media Rescue Inbox, never arbitrary attachment;
8. direct-preview plus candidate -> perceptual-hash proof;
9. process kill while pending -> WorkManager resumes recovery;
10. reboot while pending -> pending state remains recoverable.

## Premium boundary

- new WhatsApp media archive/recovery is Premium;
- already archived media remains readable/removable after downgrade;
- Recovery Point/rollback is Premium;
- physical QA tooling is QA-build-only and is not a consumer Premium benefit.

## Pass criteria

Step 23 can be marked PHYSICAL PASS only when the required matrix has evidence for:
- listener reconnect/reboot;
- text capture;
- direct media;
- MediaStore fallback;
- SAF fallback;
- ambiguous rescue behavior;
- Android 14+ selected-photo behavior;
- process-kill recovery;
- no duplicate events.

Until then the release verdict remains MANUAL_REQUIRED for physical-device reliability.
