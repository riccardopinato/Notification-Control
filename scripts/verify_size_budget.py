#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

DEFAULT_MAX_APK_BYTES = 8 * 1024 * 1024
DEFAULT_MAX_AAB_BYTES = 12 * 1024 * 1024


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--aab", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--max-apk-bytes", type=int, default=DEFAULT_MAX_APK_BYTES)
    parser.add_argument("--max-aab-bytes", type=int, default=DEFAULT_MAX_AAB_BYTES)
    args = parser.parse_args()

    apk = Path(args.apk)
    aab = Path(args.aab)
    output = Path(args.output)

    if not apk.is_file():
        raise SystemExit(f"Missing APK: {apk}")
    if not aab.is_file():
        raise SystemExit(f"Missing AAB: {aab}")

    apk_bytes = apk.stat().st_size
    aab_bytes = aab.stat().st_size
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        "\n".join(
            [
                f"release_apk={apk}",
                f"release_apk_bytes={apk_bytes}",
                f"release_apk_budget_bytes={args.max_apk_bytes}",
                f"release_aab={aab}",
                f"release_aab_bytes={aab_bytes}",
                f"release_aab_budget_bytes={args.max_aab_bytes}",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    failures: list[str] = []
    if apk_bytes > args.max_apk_bytes:
        failures.append(
            f"release APK exceeds budget: {apk_bytes} > {args.max_apk_bytes}"
        )
    if aab_bytes > args.max_aab_bytes:
        failures.append(
            f"release AAB exceeds budget: {aab_bytes} > {args.max_aab_bytes}"
        )

    if failures:
        raise SystemExit("\n".join(failures))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
