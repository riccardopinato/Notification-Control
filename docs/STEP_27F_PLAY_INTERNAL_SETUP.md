# STEP 27F — Play Internal Release Setup

Date: 2026-10-11  
Status: **REPOSITORY PREPARED / EXTERNAL ACTION REQUIRED**

This step closes the gap between the already verified persistent Android signing lane and a real Google Play Internal Testing distribution.

## Immutable release identity

- package: `com.riccardopinato.notificationcontrol`
- RC ID: `notification-control-v1.0.0-rc1`
- versionName: `1.0.0-rc1`
- versionCode: `1000001`
- certified application source: `3ff070fc3bb2b2122a2982c0eb449e3bd0eb7cb2`
- upload certificate SHA-1: `26:BD:10:CA:4E:0C:37:AB:CC:A6:66:D5:03:6F:8D:D0:88:DD:3D:2E`
- upload certificate SHA-256: `AA:9A:47:C3:99:32:87:CC:32:A3:A2:B0:69:E6:B1:A2:A9:57:BB:EB:9E:A5:BD:BF:EB:D3:44:B3:8C:38:FA:3A`

The upload key is already verified in STEP 27E. Do not replace it unless a future explicit upload-key reset is required.

## 1. Google Cloud / Play Developer API service account

Create or reuse a Google Cloud project dedicated to release automation.

Required configuration:

1. Enable **Google Play Android Developer API** in the Google Cloud project.
2. Create a Google Cloud **service account**.
3. Create one JSON key for that service account.
4. In Play Console > **Users and permissions**, grant that service-account email access to Notification Control.
5. Minimum intended app-level permission for CI publication:
   - view app information / non-financial data;
   - release to testing tracks.
6. Store the **entire unmodified JSON file contents** in the GitHub Actions repository secret:
   - `PLAY_SERVICE_ACCOUNT_JSON`

Do not commit the JSON key to Git, attach it to issues/PRs, or paste it in repository files.

### Release preflight added in STEP 27F

When `publish_play_internal=true`, `.github/workflows/release.yml` now fails closed unless `PLAY_SERVICE_ACCOUNT_JSON`:

- is valid JSON;
- has `type=service_account`;
- contains non-empty `project_id`, `private_key_id`, `private_key`, `client_email`, `client_id`, `token_uri`;
- has a service-account email;
- has a PEM private key;
- uses the expected Google OAuth token endpoint.

The workflow never prints the secret values.

This validates structure only. Real Google Play permissions are proven only by a successful API upload.

## 2. Play App Signing

For the Play-distributed application, distinguish:

- **Upload key**: held by the owner/GitHub CI; already verified in STEP 27E.
- **App signing key**: held by Google Play and used to sign APKs delivered to users.

For a new Play application using Android App Bundles, Play App Signing is expected to be enabled as part of the first bundle-release flow.

After Play App Signing exists:

1. Open Notification Control in Play Console.
2. Open the Play App Signing / App Integrity signing page.
3. Record every relevant app-signing certificate fingerprint shown by Play:
   - SHA-1;
   - SHA-256.
4. Keep these fingerprints separate from the upload-key fingerprints above.

Do not substitute the upload SHA-1 for the Play App Signing SHA-1 when configuring authentication for Play-installed builds.

## 3. Google OAuth Android identity

Production package:

`com.riccardopinato.notificationcontrol`

The existing Web OAuth client remains the server client ID used by the app.

For Play-installed builds:

1. In Google Auth Platform / Google Cloud credentials, create or verify Android OAuth client identity.
2. Package name must be exactly `com.riccardopinato.notificationcontrol`.
3. SHA-1 must correspond to the **Google Play App Signing** certificate used for delivered APKs.
4. If Play exposes multiple app-signing certificates for different signing schemes/devices, register the relevant fingerprints as separate Android client identities when required.
5. Keep QA/debug Android OAuth identity separate from production Play identity.

OAuth is not considered verified until Google Sign-In works from the app installed through Play Internal Testing.

## 4. Premium subscription products

Canonical product contract already used by the app:

- subscription product ID: `notification_control_premium`
- monthly base-plan ID: `monthly`
- annual base-plan ID: `annual`

In Play Console:

1. Open **Monetize with Play > Products > Subscriptions**.
2. Create subscription `notification_control_premium`.
3. Add auto-renewing base plan `monthly`.
4. Add auto-renewing base plan `annual`.
5. Configure supported countries/regions and prices.
6. Activate both base plans.

Do not rename these IDs after activation. The application and release verifier expect these exact IDs.

Real entitlement remains Play Billing-only in production. The `qaPremium` override must never be treated as production purchase evidence.

## 5. Internal Testing release

After `PLAY_SERVICE_ACCOUNT_JSON` and Play Console setup are complete:

Dispatch `Android Release / Play Internal` from `main` with:

- `publish_play_internal = true`
- `create_github_prerelease = true`

The workflow must:

1. load the frozen RC1 binding;
2. checkout exact certified source `3ff070fc...`;
3. run unit tests and release lint;
4. build APK + AAB once;
5. verify APK and AAB signatures;
6. verify upload certificate SHA-256;
7. create/update the GitHub RC prerelease;
8. upload the **same already hashed AAB bytes** to Play Internal Testing;
9. upload release-reality evidence.

A successful API call is only:

`DISTRIBUTION_SUBMITTED`

It is not yet:

`DISTRIBUTION_VERIFIED`

## 6. Manual evidence required after upload

From Play Internal Testing:

- install the app from Play, not by sideload;
- confirm package/version identity;
- confirm update path if another test build exists;
- verify Google profile login;
- buy `monthly` with a license tester;
- restore purchase;
- verify Premium entitlement survives app restart;
- verify offline/grace behavior according to the entitlement contract;
- optionally verify annual purchase path;
- capture Play App Signing fingerprints used by the installed artifact.

Only after these pass can STEP 27C move from external-required/submitted to verified.

## 7. Separate physical/OEM gate

STEP 27B remains independent and mandatory:

- Xiaomi/HyperOS;
- Samsung/One UI;
- Android 14+;
- real Notification Listener;
- WhatsApp/media;
- reboot/process death/Doze/burst;
- long-run battery/memory;
- TalkBack/font-scale.

Internal Testing cannot replace STEP 27B.

## STEP 27F verdict

Repository-side release preparation: **PASS / READY**

Still external:

- `PLAY_SERVICE_ACCOUNT_JSON`;
- service-account Play permissions;
- Play App Signing certificate evidence;
- production Android OAuth binding;
- activation of `notification_control_premium` + `monthly` + `annual`;
- real Internal Testing submission and install;
- live Billing and Google profile evidence.

