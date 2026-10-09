#!/usr/bin/env python3
"""Verify Notification Control alignment with its pinned App Factory baseline.

This is intentionally local and deterministic. It does not fetch App-Factory-Core
during CI and therefore cannot claim that the pinned baseline is still CURRENT.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

SHA40 = re.compile(r"^[0-9a-f]{40}$")
USES_RE = re.compile(r"^\s*(?:-\s*)?uses:\s*([^@\s]+)@([^\s#]+)")

REQUIRED_KEYS = (
    "FACTORY_REPO",
    "FACTORY_SOURCE_SHA",
    "MASTER_VERSION",
    "GOLDEN_INDEX_VERSION",
    "GOLDEN_REGISTRY_VERSION",
    "CI_RELEASE_GOLDEN",
    "CI_RELEASE_GOLDEN_VERSION",
    "CI_RELEASE_GOLDEN_FRESHNESS",
    "CHECKOUT_ACTION_SHA",
    "UPLOAD_ARTIFACT_ACTION_SHA",
)


def read_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise SystemExit(f"Missing Factory baseline manifest: {path}")
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise SystemExit(f"Invalid baseline line: {raw}")
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if not key or not value:
            raise SystemExit(f"Empty Factory baseline property: {raw}")
        values[key] = value
    return values


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    manifest = root / "release/FACTORY_BASELINE.env"
    values = read_env(manifest)

    missing = [key for key in REQUIRED_KEYS if not values.get(key)]
    if missing:
        raise SystemExit(f"Factory baseline missing required keys: {', '.join(missing)}")

    for key in ("FACTORY_SOURCE_SHA", "CHECKOUT_ACTION_SHA", "UPLOAD_ARTIFACT_ACTION_SHA"):
        if not SHA40.fullmatch(values[key]):
            raise SystemExit(f"{key} must be an immutable 40-character SHA")

    if values["CI_RELEASE_GOLDEN_FRESHNESS"] != "CURRENT":
        raise SystemExit("CI release Golden must be CURRENT for supply-chain pin adoption")

    workflow_dir = root / ".github/workflows"
    workflows = sorted({
        *workflow_dir.glob("*.yml"),
        *workflow_dir.glob("*.yaml"),
    })
    if not workflows:
        raise SystemExit("No GitHub Actions workflows found")

    checked_uses = 0
    checkout_refs = 0
    upload_refs = 0
    errors: list[str] = []

    for workflow in workflows:
        for number, raw in enumerate(workflow.read_text(encoding="utf-8").splitlines(), start=1):
            match = USES_RE.match(raw)
            if not match:
                continue
            target, ref = match.groups()
            checked_uses += 1

            if not SHA40.fullmatch(ref):
                errors.append(
                    f"{workflow.relative_to(root)}:{number}: non-immutable uses ref {target}@{ref}"
                )
                continue

            if target == "actions/checkout":
                checkout_refs += 1
                if ref != values["CHECKOUT_ACTION_SHA"]:
                    errors.append(
                        f"{workflow.relative_to(root)}:{number}: checkout ref {ref} "
                        f"!= Factory Golden {values['CHECKOUT_ACTION_SHA']}"
                    )

            if target == "actions/upload-artifact":
                upload_refs += 1
                if ref != values["UPLOAD_ARTIFACT_ACTION_SHA"]:
                    errors.append(
                        f"{workflow.relative_to(root)}:{number}: upload-artifact ref {ref} "
                        f"!= Factory Golden {values['UPLOAD_ARTIFACT_ACTION_SHA']}"
                    )

    if checkout_refs == 0:
        errors.append("No actions/checkout use found")
    if upload_refs == 0:
        errors.append("No actions/upload-artifact use found")

    if errors:
        raise SystemExit("Factory alignment failed:\n- " + "\n- ".join(errors))

    lines = [
        f"factory_repo={values['FACTORY_REPO']}",
        f"factory_source_sha={values['FACTORY_SOURCE_SHA']}",
        f"master_version={values['MASTER_VERSION']}",
        f"golden_index_version={values['GOLDEN_INDEX_VERSION']}",
        f"golden_registry_version={values['GOLDEN_REGISTRY_VERSION']}",
        f"ci_release_golden={values['CI_RELEASE_GOLDEN']}",
        f"ci_release_golden_version={values['CI_RELEASE_GOLDEN_VERSION']}",
        f"ci_release_golden_freshness={values['CI_RELEASE_GOLDEN_FRESHNESS']}",
        f"checkout_action_sha={values['CHECKOUT_ACTION_SHA']}",
        f"upload_artifact_action_sha={values['UPLOAD_ARTIFACT_ACTION_SHA']}",
        f"workflow_files={len(workflows)}",
        f"immutable_uses_checked={checked_uses}",
        f"checkout_refs_checked={checkout_refs}",
        f"upload_artifact_refs_checked={upload_refs}",
        "factory_alignment=PASS",
        "factory_currentness=PINNED_SNAPSHOT_NOT_LIVE_CHECK",
    ]
    output = "\n".join(lines) + "\n"
    print(output, end="")

    if args.output:
        destination = Path(args.output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(output, encoding="utf-8")


if __name__ == "__main__":
    main()
