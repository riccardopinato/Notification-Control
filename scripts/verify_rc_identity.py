#!/usr/bin/env python3
"""Fail-closed verification for Notification Control release-candidate identity."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

RC_VERSION_RE = re.compile(r"^\d+\.\d+\.\d+-rc\d+$")
MAX_ANDROID_VERSION_CODE = 2_100_000_000


def read_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise SystemExit(f"RC properties not found: {path}")
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"Invalid RC property line: {raw}")
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or not value:
            raise SystemExit(f"Invalid empty RC property: {raw}")
        result[key] = value
    return result


def verify_metadata(path: Path, expected_name: str, expected_code: int) -> None:
    if not path.is_file():
        raise SystemExit(f"Android output metadata not found: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    elements = payload.get("elements") or []
    if len(elements) != 1:
        raise SystemExit(f"Expected one Android output element, got {len(elements)}")
    element = elements[0]
    actual_name = str(element.get("versionName", ""))
    actual_code = int(element.get("versionCode", -1))
    if actual_name != expected_name:
        raise SystemExit(
            f"versionName mismatch: expected {expected_name!r}, got {actual_name!r}"
        )
    if actual_code != expected_code:
        raise SystemExit(
            f"versionCode mismatch: expected {expected_code}, got {actual_code}"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--properties", default="release/RC.properties")
    parser.add_argument("--metadata")
    parser.add_argument("--version-suffix", default="")
    parser.add_argument("--output")
    args = parser.parse_args()

    props = read_properties(Path(args.properties))
    required = ["RC_ID", "VERSION_NAME", "VERSION_CODE", "RC_SEQUENCE", "CHANNEL"]
    missing = [key for key in required if key not in props]
    if missing:
        raise SystemExit("Missing RC properties: " + ", ".join(missing))

    version_name = props["VERSION_NAME"]
    if not RC_VERSION_RE.fullmatch(version_name):
        raise SystemExit(f"VERSION_NAME is not an RC semantic version: {version_name}")

    try:
        version_code = int(props["VERSION_CODE"])
    except ValueError as exc:
        raise SystemExit("VERSION_CODE must be an integer") from exc

    if not (1 <= version_code <= MAX_ANDROID_VERSION_CODE):
        raise SystemExit(f"VERSION_CODE out of Android range: {version_code}")

    expected_rc_id = f"notification-control-v{version_name}"
    if props["RC_ID"] != expected_rc_id:
        raise SystemExit(
            f"RC_ID mismatch: expected {expected_rc_id!r}, got {props['RC_ID']!r}"
        )

    if props["CHANNEL"] != "release-candidate":
        raise SystemExit("CHANNEL must be release-candidate")

    try:
        rc_sequence = int(props["RC_SEQUENCE"])
    except ValueError as exc:
        raise SystemExit("RC_SEQUENCE must be an integer") from exc

    suffix_sequence = re.search(r"-rc(\d+)$", version_name)
    if not suffix_sequence or int(suffix_sequence.group(1)) != rc_sequence:
        raise SystemExit(
            f"RC_SEQUENCE={rc_sequence} does not match VERSION_NAME={version_name}"
        )

    expected_built_name = version_name + args.version_suffix
    if args.metadata:
        verify_metadata(Path(args.metadata), expected_built_name, version_code)

    lines = [
        f"rc_id={props['RC_ID']}",
        f"version_name={version_name}",
        f"version_code={version_code}",
        f"rc_sequence={rc_sequence}",
        f"channel={props['CHANNEL']}",
        f"expected_built_version_name={expected_built_name}",
        "rc_identity=PASS",
    ]
    result = "\n".join(lines) + "\n"
    print(result, end="")
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(result, encoding="utf-8")


if __name__ == "__main__":
    main()
