"""윈도우 분절 — X(권한-경량 센서)만으로 계산한다.

ADR-0010 train/serve 누수 관리 (a): y 윈도우 경계가 AS 타임스탬프에 의존하면
serve 시 재현 불가. 여기서는 세션 start(UsageStats 유래 = X)에 고정 길이 격자를
앵커링한다. 이 함수가 AS 타입(scroll/rule)을 소비하지 않음은 테스트가 강제한다.
"""

from __future__ import annotations

from dataclasses import dataclass

from .schema import X_TYPES, Timeline

WINDOW_MS = 10_000  # 윈도우 입자 초안 (ADR-0010 "남은 미결" — 튜닝 변수)


@dataclass(frozen=True)
class Window:
    index: int
    start_ms: int
    end_ms: int


def segment_windows(timeline: Timeline, window_ms: int = WINDOW_MS) -> list[Window]:
    """세션 start~end를 고정 길이 윈도우로 분절한다. X 타입 레코드만 본다."""
    x_records = [r for r in timeline.records if r["type"] in X_TYPES]
    start = end = None
    for r in x_records:
        if r["type"] == "session":
            if r.get("event") == "start" and start is None:
                start = r["t"] // 1_000_000
            if r.get("event") == "end":
                end = r["t"] // 1_000_000
    if start is None:
        return []
    if end is None:
        end = max(r["t"] for r in x_records) // 1_000_000

    windows = []
    index = 0
    at = start
    while at < end:
        windows.append(Window(index, at, min(at + window_ms, end)))
        index += 1
        at += window_ms
    return windows
