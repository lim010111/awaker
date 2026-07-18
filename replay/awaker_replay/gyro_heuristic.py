"""권한-경량 후보 v0 — 자이로 휴리스틱 (이슈 07 채점 대상).

distillation 천장(ADR-0010)의 첫 예고편: 출시 형태의 입력(X)만으로 teacher 룰을
얼마나 복원하는지 본다. 상수는 전부 미검증 초안 — 이 하네스의 목적은 후보의
품질이 아니라 *채점 파이프라인* 자체다 (이후 student 백테스트가 재사용).

직관: 무지성 스크롤 중 폰은 대체로 정지(미세 떨림)하고, fling 순간에만 짧은
회전 스파이크가 온다. → 윈도우 안에서 '정지 비율'이 높고 '스파이크 수'가
케이던스 대역에 들면 양성.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from .schema import Timeline
from .windows import Window


@dataclass(frozen=True)
class Config:
    still_rad_s: float = 0.15   # 이하면 '정지' 샘플
    spike_rad_s: float = 0.8    # 이상이면 fling 후보 스파이크
    min_still_frac: float = 0.5
    min_spikes_per_10s: int = 1
    max_spikes_per_10s: int = 12


def window_label(samples: list[float], window_ms: int, cfg: Config = Config()) -> bool:
    """자이로 크기 샘플들로 윈도우 양성 여부를 판정한다."""
    if not samples:
        return False
    still = sum(1 for m in samples if m < cfg.still_rad_s) / len(samples)
    spikes = _count_spikes(samples, cfg.spike_rad_s)
    scale = window_ms / 10_000
    return (
        still >= cfg.min_still_frac
        and cfg.min_spikes_per_10s * scale <= spikes <= cfg.max_spikes_per_10s * scale
    )


def _count_spikes(samples: list[float], threshold: float) -> int:
    """임계 상향 돌파 횟수 — 연속 샘플 뭉치는 스파이크 1개."""
    count = 0
    above = False
    for m in samples:
        if m >= threshold and not above:
            count += 1
        above = m >= threshold
    return count


def label_windows(
    timeline: Timeline, windows: list[Window], cfg: Config = Config(),
) -> list[bool]:
    gyro = timeline.of_type("gyro")
    labels = []
    gi = 0
    for w in windows:
        magnitudes = []
        while gi < len(gyro) and gyro[gi]["t"] // 1_000_000 < w.end_ms:
            r = gyro[gi]
            if r["t"] // 1_000_000 >= w.start_ms:
                magnitudes.append(math.sqrt(r["x"] ** 2 + r["y"] ** 2 + r["z"] ** 2))
            gi += 1
        labels.append(window_label(magnitudes, w.end_ms - w.start_ms, cfg))
    return labels
