"""teacher 룰 v0의 replay 재구현 — 앱 `TeacherRule.kt`와 의도적 1:1 복제.

한쪽을 바꾸면 반드시 같이 바꿀 것. debounce·윈도우·임계는 물론 median 인덱싱
(`sorted_gaps[n // 2]` — 상위 중앙값)까지 동일해야 재현성 검증이 성립한다.
시간 단위: 단조 ms (elapsedRealtime).
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    window_ms: int = 60_000
    debounce_ms: int = 500
    min_flings: int = 8
    min_span_ms: int = 30_000
    max_median_gap_ms: int = 8_000
    max_entry_pause_ms: int = 20_000
    exit_silence_ms: int = 30_000
    exit_min_flings: int = 4


@dataclass(frozen=True)
class Metrics:
    flings: int
    span_ms: int
    median_gap_ms: int
    max_gap_ms: int


@dataclass(frozen=True)
class Transition:
    at_ms: int
    state: str  # "enter" | "exit"
    metrics: Metrics
    reason: str | None = None


class TeacherRule:
    def __init__(self, config: Config = Config()):
        self.config = config
        self._flings: deque[int] = deque()
        self._last_scroll_ms: int | None = None
        self.is_positive = False

    def on_scroll(self, at_ms: int) -> Transition | None:
        if self._last_scroll_ms is None or at_ms - self._last_scroll_ms >= self.config.debounce_ms:
            self._flings.append(at_ms)
        self._last_scroll_ms = at_ms
        return self._evaluate(at_ms)

    def on_tick(self, at_ms: int) -> Transition | None:
        return self._evaluate(at_ms)

    def reset(self, at_ms: int) -> Transition | None:
        was_positive = self.is_positive
        metrics = self._snapshot()
        self._flings.clear()
        self._last_scroll_ms = None
        self.is_positive = False
        if was_positive:
            return Transition(at_ms, "exit", metrics, reason="reset")
        return None

    def _evaluate(self, now_ms: int) -> Transition | None:
        cfg = self.config
        while self._flings and now_ms - self._flings[0] > cfg.window_ms:
            self._flings.popleft()
        metrics = self._snapshot()

        if not self.is_positive:
            enter = (
                metrics.flings >= cfg.min_flings
                and metrics.span_ms >= cfg.min_span_ms
                and metrics.median_gap_ms <= cfg.max_median_gap_ms
                and metrics.max_gap_ms <= cfg.max_entry_pause_ms
            )
            if enter:
                self.is_positive = True
                return Transition(now_ms, "enter", metrics)
            return None

        silence = (now_ms - self._flings[-1]) if self._flings else float("inf")
        if silence > cfg.exit_silence_ms:
            reason = "silence"
        elif metrics.flings < cfg.exit_min_flings:
            reason = "cadence_collapse"
        else:
            return None
        self.is_positive = False
        return Transition(now_ms, "exit", metrics, reason)

    def _snapshot(self) -> Metrics:
        flings = list(self._flings)
        if len(flings) < 2:
            return Metrics(len(flings), 0, 0, 0)
        gaps = sorted(flings[i + 1] - flings[i] for i in range(len(flings) - 1))
        return Metrics(
            flings=len(flings),
            span_ms=flings[-1] - flings[0],
            median_gap_ms=gaps[len(gaps) // 2],
            max_gap_ms=gaps[-1],
        )
