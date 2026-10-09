#!/usr/bin/env python3
"""Static Play/release-readiness verifier for Notification Control.

This verifier intentionally checks only repository facts. Provider/store/device
facts remain EXTERNAL_REQUIRED or MANUAL_REQUIRED until real evidence exists.
"""

from __future__ import annotations

import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path

SHA40 = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_PACKAGE = "com.riccardopinato.notificationcontrol"
EXPECTED_SUBSCRIPTION = "notification_control_premium"
EXPECTED_MONTHLY = "monthly"
EXPECTED_ANNUAL = "annual"
FORBIDDEN_RELEASE_MEDIA_PERMISSIONS = (
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.READ_EXTERNAL_STORAGE",
)


def read_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise SystemExit(f"Missing properties file: {path}")
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"Invalid property line in {path}: {raw}")
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if not key or not value:
            raise SystemExit(f"Empty property in {path}: {raw}")
        result[key] = value
    return result


def require_text(path: Path, needle: str, label: str) -> None:
    if not path.is_file():
        raise SystemExit(f"Missing {label}: {path}")
    if needle not in path.read_text(encoding="utf-8"):
        raise SystemExit(f"{label} missing expected contract: {needle}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--control-root", default=".")
    parser.add_argument("--source-root", default=".")
    parser.add_argument("--output")
    args = parser.parse_args()

    control = Path(args.control_root).resolve()
    source = Path(args.source_root).resolve()

    rc = read_properties(source / "release/RC.properties")
    cert = read_properties(control / "release/RC_CERTIFICATION.env")

    for key in ("RC_ID", "VERSION_NAME", "VERSION_CODE"):
        if rc.get(key) != cert.get(key):
            raise SystemExit(
                f"Certification binding mismatch for {key}: "
                f"source={rc.get(key)!r} control={cert.get(key)!r}"
            )

    certified_sha = cert.get("CERTIFIED_SOURCE_SHA", "")
    if not SHA40.fullmatch(certified_sha):
        raise SystemExit(f"Invalid CERTIFIED_SOURCE_SHA: {certified_sha!r}")

    gradle = source / "app/build.gradle.kts"
    require_text(
        gradle,
        f'applicationId = "{EXPECTED_PACKAGE}"',
        "Gradle application identity",
    )
    require_text(
        gradle,
        'buildConfigField("boolean", "MEDIASTORE_RECOVERY_ENABLED", "false")',
        "release least-privilege media policy",
    )

    billing = source / (
        "app/src/main/java/com/riccardopinato/notificationcontrol/"
        "billing/BillingProducts.kt"
    )
    require_text(
        billing,
        f'PREMIUM_SUBSCRIPTION = "{EXPECTED_SUBSCRIPTION}"',
        "Premium subscription product",
    )
    require_text(
        billing,
        f'PREMIUM_MONTHLY_BASE_PLAN = "{EXPECTED_MONTHLY}"',
        "Premium monthly base plan",
    )
    require_text(
        billing,
        f'PREMIUM_ANNUAL_BASE_PLAN = "{EXPECTED_ANNUAL}"',
        "Premium annual base plan",
    )

    release_manifest = source / "app/src/release/AndroidManifest.xml"
    if not release_manifest.is_file():
        raise SystemExit(f"Missing release manifest: {release_manifest}")
    manifest_root = ET.parse(release_manifest).getroot()
    android_ns = "{http://schemas.android.com/apk/res/android}"
    tools_ns = "{http://schemas.android.com/tools}"
    permission_nodes = {
        node.attrib.get(android_ns + "name"): node
        for node in manifest_root.findall("uses-permission")
    }
    for permission in FORBIDDEN_RELEASE_MEDIA_PERMISSIONS:
        node = permission_nodes.get(permission)
        if node is None or node.attrib.get(tools_ns + "node") != "remove":
            raise SystemExit(
                f"Release manifest does not explicitly remove {permission}"
            )

    lines = [
        f"rc_id={rc['RC_ID']}",
        f"version_name={rc['VERSION_NAME']}",
        f"version_code={rc['VERSION_CODE']}",
        f"certified_source_sha={certified_sha}",
        f"android_ci_run={cert.get('ANDROID_CI_RUN', 'UNKNOWN')}",
        f"certified_qa_run={cert.get('CERTIFIED_QA_RUN', 'UNKNOWN')}",
        f"applab_full_run={cert.get('APPLAB_FULL_RUN', 'UNKNOWN')}",
        f"package_id={EXPECTED_PACKAGE}",
        "static_identity=PASS",
        "release_media_policy=PASS",
        "billing_contract=PASS",
        "production_signing=EXTERNAL_REQUIRED",
        "play_service_account=EXTERNAL_REQUIRED",
        "play_app_signing_fingerprint=EXTERNAL_REQUIRED",
        "google_oauth_release_match=EXTERNAL_REQUIRED",
        "play_products_activation=EXTERNAL_REQUIRED",
        "play_internal_upload=EXTERNAL_REQUIRED",
        "play_internal_install_update=MANUAL_REQUIRED",
        "live_purchase_restore=MANUAL_REQUIRED",
        "live_google_profile=MANUAL_REQUIRED",
        "step27b_physical_oem=MANUAL_REQUIRED",
        "step27c_static_readiness=PASS",
        "step27c_final=BLOCKED_EXTERNAL_AND_MANUAL",
    ]
    result = "\n".join(lines) + "\n"
    print(result, end="")
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(result, encoding="utf-8")


if __name__ == "__main__":
    main()
