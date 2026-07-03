#!/usr/bin/env python3
import argparse
import json
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "legacy-translation-provider-report/v1"
DEFAULT_TEXT = "삼성전자 영업이익 증가"
PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_PATH = PROJECT_ROOT / "reports/deepl-translation-smoke-report.json"


def main() -> None:
    args = _parse_args()
    generated_at = (
        datetime.fromisoformat(args.generated_at)
        if args.generated_at
        else datetime.now(timezone.utc)
    )
    report = build_report(
        text=args.text,
        source_type=args.source_type,
        original_url=args.original_url,
        generated_at=generated_at,
    )
    report_path = _project_path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(_json(report), encoding="utf-8")
    print(_json(report), end="")


def build_report(
    *,
    text: str,
    source_type: str,
    original_url: str,
    generated_at: datetime,
) -> dict[str, Any]:
    return {
        "schema_version": SCHEMA_VERSION,
        "generated_at": generated_at.isoformat(),
        "sample": {
            "source_type": source_type,
            "original_text": text,
            "original_url": original_url,
            "external_translation_join_key": _join_key(source_type, text, original_url),
        },
        "providers": {
            "deepl": {
                "status": "legacy_disabled",
                "reason": "Active translation uses OpenAI GPT via OPENAI_API_KEY; DeepL is never called.",
            },
            "papago": {
                "status": "legacy_disabled",
                "reason": "Papago adapter is removed and is never called.",
            },
        },
        "security": {
            "credential_source": "none",
            "api_key_recorded": False,
        },
    }


def _join_key(source_type: str, text: str, original_url: str) -> str:
    return sha256(f"{source_type}:{text}:{original_url}".encode()).hexdigest()


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Write a redacted legacy-provider report without calling DeepL."
    )
    parser.add_argument("--text", default=DEFAULT_TEXT)
    parser.add_argument("--source-type", default="NEWS")
    parser.add_argument("--original-url", default="local-smoke://legacy-translation")
    parser.add_argument("--generated-at")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT_PATH)
    return parser.parse_args()


def _project_path(path: Path) -> Path:
    if path.is_absolute():
        return path
    return PROJECT_ROOT / path


def _json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


if __name__ == "__main__":
    main()
