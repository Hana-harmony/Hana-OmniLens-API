#!/usr/bin/env python3
import argparse
import json
import os
import re
import resource
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from typing import Any

SCHEMA_VERSION = "openai-translation-smoke-report/v1"
DEFAULT_TEXT = "삼성전자 영업이익 증가와 자사주 소각으로 주주환원 기대가 커졌다."
DEFAULT_BASE_URL = "https://api.openai.com"
DEFAULT_MODEL = "gpt-4o-mini"
PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REPORT_PATH = PROJECT_ROOT / "reports/openai-translation-smoke-report.json"

LOCAL_GLOSSARY = (
    ("영업이익", "operating profit"),
    ("자사주", "treasury shares"),
    ("주주환원", "shareholder returns"),
    ("상장폐지", "delisting"),
    ("거래정지", "trading halt"),
    ("유상증자", "rights offering"),
    ("무상증자", "bonus issue"),
    ("전환사채", "convertible bond"),
    ("공급계약", "supply contract"),
    ("개미", "retail investors"),
    ("대장주", "bellwether stock"),
    ("코스피", "KOSPI"),
    ("코스닥", "KOSDAQ"),
)


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
        api_key=os.environ.get("OPENAI_API_KEY", ""),
        model=args.model,
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
    model: str,
    timeout_seconds: float,
    generated_at: datetime,
) -> dict[str, Any]:
    local_started_at = time.monotonic()
    local_text, matched_terms = _local_glossary_translate(text)
    local_latency_ms = round((time.monotonic() - local_started_at) * 1000, 3)
    gpt_result = _openai_smoke(
        text=text,
        base_url=base_url,
        api_key=api_key,
        model=model,
        timeout_seconds=timeout_seconds,
    )
    local_result = {
        "status": "translated" if local_text != text else "source_language_fallback",
        "provider": "local-financial-glossary",
        "model_version": "local-financial-glossary-v1",
        "translated_text": local_text,
        "latency_ms": local_latency_ms,
        "peak_rss_mb": _peak_rss_mb(),
        "matched_terms": matched_terms,
        "quality": _quality(text, local_text),
    }
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
            "openai_gpt": gpt_result,
            "local_translation_model": local_result,
            "deepl": {
                "status": "legacy_disabled",
                "reason": "DeepL is not called by the active translation path.",
            },
        },
        "comparison": _comparison(text, gpt_result, local_result),
        "local_model_plan": {
            "training_runtime": "MacBook M4 Pro 24GB: MLX LoRA/QLoRA candidate workflow",
            "serving_runtime": "AWS Graviton t4g.medium-class CPU: llama.cpp OpenAI-compatible sidecar",
            "candidate_model": "Qwen3-0.6B GGUF Q4 or smaller translation-specific GGUF",
            "serving_guards": {
                "timeout_ms": 1200,
                "max_batch_size": 1,
                "max_input_chars": 1200,
                "fallback": "return source text with SOURCE_LANGUAGE_FALLBACK status",
            },
            "promotion_gate": "Do not route live traffic until GPT-vs-local report passes glossary, number, latency, and memory gates.",
        },
        "security": {
            "credential_source": "OPENAI_API_KEY environment variable",
            "api_key_recorded": False,
        },
    }


def _openai_smoke(
    *,
    text: str,
    base_url: str,
    api_key: str,
    model: str,
    timeout_seconds: float,
) -> dict[str, Any]:
    if not api_key:
        return {
            "status": "skipped_missing_credential",
            "provider": "openai",
            "model": model,
            "base_url": base_url,
            "translated_text": "",
            "latency_ms": 0,
            "quality": _quality(text, ""),
        }
    payload = {
        "model": model,
        "instructions": (
            "Translate Korean financial news into natural, concise English. "
            "Return only the translation. Preserve stock codes, numbers, dates, URLs, "
            "and company names."
        ),
        "input": [{"role": "user", "content": text}],
        "store": False,
        "temperature": 0,
        "max_output_tokens": 1024,
    }
    request = urllib.request.Request(
        url=base_url.rstrip("/") + "/v1/responses",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    started_at = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = json.loads(response.read().decode("utf-8"))
            translated_text = _output_text(body)
            return {
                "status": "success" if translated_text else "empty_output",
                "provider": "openai",
                "model": model,
                "base_url": base_url,
                "http_status": response.status,
                "translated_text": translated_text,
                "latency_ms": round((time.monotonic() - started_at) * 1000),
                "quality": _quality(text, translated_text),
            }
    except urllib.error.HTTPError as exception:
        return _error_result("http_error", base_url, model, started_at, exception.code, text)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exception:
        return _error_result(type(exception).__name__, base_url, model, started_at, 0, text)


def _local_glossary_translate(text: str) -> tuple[str, list[str]]:
    translated = text
    matched_terms: list[str] = []
    for source, target in LOCAL_GLOSSARY:
        if source not in translated:
            continue
        translated = translated.replace(source, target)
        matched_terms.append(source)
    return " ".join(translated.split()), matched_terms


def _output_text(payload: dict[str, Any]) -> str:
    for item in payload.get("output", []):
        if not isinstance(item, dict) or item.get("type") != "message":
            continue
        for content in item.get("content", []):
            if isinstance(content, dict) and content.get("type") == "output_text":
                return str(content.get("text", ""))
    return ""


def _quality(source_text: str, translated_text: str) -> dict[str, Any]:
    source_numbers = set(re.findall(r"\d+(?:[.,]\d+)?", source_text))
    translated_numbers = set(re.findall(r"\d+(?:[.,]\d+)?", translated_text))
    return {
        "remaining_hangul_count": len(re.findall(r"[가-힣]", translated_text)),
        "preserved_number_count": len(source_numbers & translated_numbers),
        "missing_numbers": sorted(source_numbers - translated_numbers),
        "translated_non_empty": bool(translated_text.strip()),
    }


def _comparison(
    source_text: str,
    gpt_result: dict[str, Any],
    local_result: dict[str, Any],
) -> dict[str, Any]:
    return {
        "gpt_available": gpt_result["status"] == "success",
        "local_available": bool(local_result["translated_text"]),
        "gpt_remaining_hangul_count": gpt_result["quality"]["remaining_hangul_count"],
        "local_remaining_hangul_count": local_result["quality"]["remaining_hangul_count"],
        "source_hash": sha256(source_text.encode()).hexdigest(),
    }


def _error_result(
    status: str,
    base_url: str,
    model: str,
    started_at: float,
    http_status: int,
    source_text: str,
) -> dict[str, Any]:
    return {
        "status": status,
        "provider": "openai",
        "model": model,
        "base_url": base_url,
        "http_status": http_status,
        "translated_text": "",
        "latency_ms": round((time.monotonic() - started_at) * 1000),
        "quality": _quality(source_text, ""),
    }


def _peak_rss_mb() -> float:
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    divisor = 1024 * 1024 if sys.platform == "darwin" else 1024
    return round(peak / divisor, 3)


def _join_key(source_type: str, text: str, original_url: str) -> str:
    return sha256(f"{source_type}:{text}:{original_url}".encode()).hexdigest()


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run an OpenAI GPT translation smoke and compare a local glossary baseline."
    )
    parser.add_argument("--text", default=DEFAULT_TEXT)
    parser.add_argument("--source-type", default="NEWS")
    parser.add_argument("--original-url", default="local-smoke://openai-translation")
    parser.add_argument("--base-url", default=os.environ.get("OPENAI_TRANSLATION_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--model", default=os.environ.get("OPENAI_TRANSLATION_MODEL", DEFAULT_MODEL))
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
