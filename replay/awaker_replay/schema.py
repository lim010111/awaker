"""세션 로그(JSONL, docs/log-schema.md) 파서.

파서 계약:
- 첫 줄은 header여야 하며 스키마 버전을 검사한다 (미래 버전은 경고 후 시도).
- 모르는 type은 무시한다(전방 호환) — 이슈 05/06이 타입을 추가한다.
- 서로 다른 스레드가 쓴 파일이라 라인 순서가 t 단조가 아닐 수 있다 → t로 정렬.
"""

from __future__ import annotations

import json
import warnings
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from . import SCHEMA_VERSION

# X(권한-경량 센서 융합) 소스 타입 — 윈도우 분절은 이 타입들만 소비해야 한다
# (ADR-0010 train/serve 누수 관리 (a)).
X_TYPES = frozenset({"gyro", "accel", "light", "screen", "battery", "foreground", "session"})

# AS(베타 한정 GT 전용) 소스 타입 — 판별 입력으로 쓰면 누수.
AS_TYPES = frozenset({"scroll", "rule"})

KNOWN_TYPES = X_TYPES | AS_TYPES | {"header", "ema_probe"}


@dataclass
class Header:
    version: int
    session_id: str
    pkg: str
    wall_ms: int
    elapsed_ns: int
    model: str = "?"
    sdk: int = 0
    app: str = "?"


@dataclass
class Timeline:
    """세션 하나의 공통 타임라인. records는 t 오름차순 정렬된 dict 목록."""

    header: Header
    records: list[dict[str, Any]] = field(default_factory=list)
    unknown_types: set[str] = field(default_factory=set)

    def of_type(self, *types: str) -> list[dict[str, Any]]:
        wanted = set(types)
        return [r for r in self.records if r["type"] in wanted]

    @property
    def session_start_ns(self) -> int | None:
        for r in self.records:
            if r["type"] == "session" and r.get("event") == "start":
                return r["t"]
        return None

    @property
    def session_end_ns(self) -> int | None:
        for r in reversed(self.records):
            if r["type"] == "session" and r.get("event") == "end":
                return r["t"]
        return None


class SchemaError(ValueError):
    pass


def parse_lines(lines: list[str], source: str = "<memory>") -> Timeline:
    if not lines:
        raise SchemaError(f"{source}: empty log")

    head = json.loads(lines[0])
    if head.get("type") != "header":
        raise SchemaError(f"{source}: first line must be header, got {head.get('type')!r}")
    version = head.get("v")
    if version != SCHEMA_VERSION:
        warnings.warn(
            f"{source}: schema version {version} != supported {SCHEMA_VERSION}; parsing anyway",
            stacklevel=2,
        )
    header = Header(
        version=version,
        session_id=head["sessionId"],
        pkg=head["pkg"],
        wall_ms=head["wallMs"],
        elapsed_ns=head["elapsedNs"],
        model=head.get("model", "?"),
        sdk=head.get("sdk", 0),
        app=head.get("app", "?"),
    )

    records: list[dict[str, Any]] = []
    unknown: set[str] = set()
    for lineno, raw in enumerate(lines[1:], start=2):
        raw = raw.strip()
        if not raw:
            continue
        try:
            rec = json.loads(raw)
        except json.JSONDecodeError as e:
            # append 도중 죽은 프로세스의 마지막 잘린 라인은 용인한다.
            if lineno == len(lines):
                warnings.warn(f"{source}:{lineno}: truncated last line ignored ({e})", stacklevel=2)
                continue
            raise SchemaError(f"{source}:{lineno}: invalid JSON: {e}") from e
        rtype = rec.get("type")
        if rtype not in KNOWN_TYPES:
            unknown.add(rtype)
            continue
        if rtype == "header":
            raise SchemaError(f"{source}:{lineno}: duplicate header")
        if "t" not in rec:
            raise SchemaError(f"{source}:{lineno}: record without t: {rtype}")
        records.append(rec)

    records.sort(key=lambda r: r["t"])
    return Timeline(header=header, records=records, unknown_types=unknown)


def load_log(path: str | Path) -> Timeline:
    path = Path(path)
    return parse_lines(path.read_text(encoding="utf-8").splitlines(), source=str(path))
