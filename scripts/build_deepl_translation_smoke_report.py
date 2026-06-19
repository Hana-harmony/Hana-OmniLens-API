#!/usr/bin/env python3
import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "deepl-translation-smoke-report/v1"
DEFAULT_TEXT = "삼성전자 영업이익 증가"
DEFAULT_BASE_URL = "https://api-free.deepl.com"
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
        base_url=args.base_url,
        api_key=_api_key(),
        timeout_seconds=args.timeout_seconds,
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
    base_url: str,
    api_key: str,
    timeout_seconds: float,
    generated_at: datetime,
) -> dict[str, Any]:
    deepl_result = _deepl_smoke(
        text=text,
        base_url=base_url,
        api_key=api_key,
        timeout_seconds=timeout_seconds,
    )
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
            "deepl": deepl_result,
            "papago": {
                "status": "legacy_disabled",
                "reason": "Papago adapter is removed; DeepL is the active translation provider.",
            },
        },
        "security": {
            "credential_source": "environment",
            "api_key_recorded": False,
        },
    }


def _deepl_smoke(
    *,
    text: str,
    base_url: str,
    api_key: str,
    timeout_seconds: float,
) -> dict[str, Any]:
    if not api_key:
        return {
            "status": "skipped_missing_credential",
            "base_url": base_url,
            "translated_text": "",
            "latency_ms": 0,
        }

    request = urllib.request.Request(
        url=base_url.rstrip("/") + "/v2/translate",
        data=urllib.parse.urlencode(
            {
                "text": text,
                "source_lang": "KO",
                "target_lang": "EN-US",
            }
        ).encode(),
        headers={
            "Authorization": "DeepL-Auth-Key " + api_key,
            "Content-Type": "application/x-www-form-urlencoded",
        },
        method="POST",
    )
    started_at = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = response.read().decode("utf-8")
            payload = json.loads(body)
            translation = _first_translation(payload)
            return {
                "status": "success",
                "base_url": base_url,
                "http_status": response.status,
                "latency_ms": round((time.monotonic() - started_at) * 1000),
                "detected_source_language": translation.get("detected_source_language", ""),
                "translated_text": translation.get("text", ""),
            }
    except urllib.error.HTTPError as exception:
        return _error_result("http_error", base_url, started_at, exception.code)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exception:
        return _error_result(type(exception).__name__, base_url, started_at, 0)


def _first_translation(payload: dict[str, Any]) -> dict[str, str]:
    translations = payload.get("translations")
    if not isinstance(translations, list) or not translations:
        return {}
    first = translations[0]
    if not isinstance(first, dict):
        return {}
    return {str(key): str(value) for key, value in first.items()}


def _error_result(
    status: str,
    base_url: str,
    started_at: float,
    http_status: int,
) -> dict[str, Any]:
    return {
        "status": status,
        "base_url": base_url,
        "http_status": http_status,
        "latency_ms": round((time.monotonic() - started_at) * 1000),
        "translated_text": "",
    }


def _api_key() -> str:
    return (
        os.environ.get("DEEPL_API_KEY")
        or os.environ.get("OMNILENS_PROVIDERS_DEEP_L_TRANSLATION_API_KEY")
        or ""
    )


def _join_key(source_type: str, text: str, original_url: str) -> str:
    return sha256(f"{source_type}:{text}:{original_url}".encode()).hexdigest()


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a DeepL translation live smoke and write a redacted report."
    )
    parser.add_argument("--text", default=DEFAULT_TEXT)
    parser.add_argument("--source-type", default="NEWS")
    parser.add_argument("--original-url", default="local-smoke://deepl-translation")
    parser.add_argument(
        "--base-url",
        default=os.environ.get("DEEPL_TRANSLATION_BASE_URL", DEFAULT_BASE_URL),
    )
    parser.add_argument("--timeout-seconds", type=float, default=10.0)
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
