"""합성 세션 로그 픽스처 — 스키마 회귀를 잡는 기준 데이터 (이슈 07 AC).

시나리오: 2초 케이던스 무지성 스크롤 90초 → 침묵 → 5분 away 타임아웃으로 세션
종료. teacher 룰 v0 기준 예상 전이: enter@31000ms(스크롤 구동, 결정적),
exit@silence(디바이스 tick 위상에 따라 ±). 디바이스 tick 위상은 replay(세션
시작 앵커)와 다르게 2초로 줘서 허용 오차 로직까지 검증한다.
"""

from __future__ import annotations

import json
import random

T0_NS = 50_000_000_000
WALL0_MS = 1_700_000_000_000
PKG = "com.google.android.youtube"

# 시나리오 상수 (테스트가 기대값으로 재사용)
SCROLL_START_MS = 1_000
SCROLL_GAP_MS = 2_000
SCROLL_COUNT = 45                      # 마지막 스크롤 89_000ms
LAST_SCROLL_MS = SCROLL_START_MS + (SCROLL_COUNT - 1) * SCROLL_GAP_MS
EXPECT_ENTER_MS = 31_000               # 8번째 이후 span 30s 충족 시점 (스크롤 구동)
DEVICE_EXIT_MS = 122_000               # 디바이스 tick(위상 2s)의 silence 판정 시점
SESSION_END_MS = LAST_SCROLL_MS + 5 * 60_000 + 11_000  # away 5분 룰 만료(대략)


def ns(ms: float) -> int:
    return T0_NS + int(ms * 1_000_000)


def abs_ms(rel_ms: int) -> int:
    """세션 상대 ms → 공통 타임라인 절대 ms (replay 전이·윈도우가 쓰는 단위)."""
    return T0_NS // 1_000_000 + rel_ms


def _line(**kw) -> str:
    return json.dumps(kw, ensure_ascii=False)


def synthetic_log(
    include_as: bool = True,
    include_future_types: bool = False,
    device_enter_ms: int = EXPECT_ENTER_MS,
    away: bool = False,
) -> list[str]:
    rng = random.Random(42)
    lines = [
        _line(
            type="header", v=1, sessionId="fx1", pkg=PKG,
            wallMs=WALL0_MS, elapsedNs=T0_NS, model="Fixture", sdk=35, app="test",
        ),
        _line(type="session", t=ns(0), event="start", sessionId="fx1", pkg=PKG),
        _line(type="foreground", t=ns(0), pkg=PKG),
        _line(type="screen", t=ns(0), on=True),
        _line(type="battery", t=ns(0), pct=80, charging=False),
    ]

    scroll_times = [SCROLL_START_MS + i * SCROLL_GAP_MS for i in range(SCROLL_COUNT)]
    if away:
        # 50~70초 구간 이탈(away 리셋) 후 케이던스 재개 — 재진입은 71s+30s=101s
        scroll_times = [t for t in scroll_times if t < 50_000]
        scroll_times += list(range(71_000, 109_001, 2_000))
        lines.append(_line(type="foreground", t=ns(50_500), pkg="com.android.launcher"))
        lines.append(_line(type="foreground", t=ns(70_000), pkg=PKG))
        lines.append(
            _line(type="session", t=ns(70_000), event="resume", sessionId="fx1", pkg=PKG, awayMs=19_500),
        )
    last_scroll_ms = max(scroll_times)

    # 자이로 50Hz: 스크롤 활성 구간만 (away/세션 밖 샘플링 정지 — 이슈 03)
    spike_ms = set()
    for t in scroll_times:
        spike_ms.add(t // 20 * 20)
        spike_ms.add(t // 20 * 20 + 20)
    for t_ms in range(0, last_scroll_ms + 31_000, 20):
        if away and 50_500 <= t_ms <= 70_000:
            continue
        if t_ms in spike_ms:
            mag = 1.2
            x, y, z = mag, 0.05, 0.02
        else:
            x = rng.gauss(0.02, 0.01)
            y = rng.gauss(0.02, 0.01)
            z = rng.gauss(0.01, 0.005)
        lines.append(_line(type="gyro", t=ns(t_ms), x=round(x, 4), y=round(y, 4), z=round(z, 4)))
        # accel은 대표로 드문드문 (파서/플롯 경로 확인용)
        if t_ms % 1_000 == 0:
            lines.append(_line(type="accel", t=ns(t_ms), x=0.1, y=9.7, z=0.3))

    if include_as:
        for t in scroll_times:
            lines.append(_line(type="scroll", t=ns(t), pkg=PKG, dx=0, dy=-800))
        # 온디바이스 rule 판정 라인 (시뮬레이션): enter는 스크롤 구동이라 결정적,
        # exit은 tick 위상 2s 가정.
        lines.append(_line(
            type="rule", t=ns(device_enter_ms), state="enter",
            flings=16, spanMs=30_000, medianGapMs=2_000, maxGapMs=2_000,
        ))
        if away:
            # away 리셋 후 재진입 — exit 라인 없는 연속 enter (스키마 문서화된 동작)
            lines.append(_line(
                type="rule", t=ns(101_000), state="enter",
                flings=16, spanMs=30_000, medianGapMs=2_000, maxGapMs=2_000,
            ))
        # exit은 tick 구동 — 디바이스 tick 위상(2s)만큼 replay와 어긋난다
        device_exit_ms = last_scroll_ms + 31_000 + 2_000
        lines.append(_line(
            type="rule", t=ns(device_exit_ms), state="exit", reason="silence",
            flings=14, spanMs=26_000, medianGapMs=2_000, maxGapMs=2_000,
        ))

    if include_future_types:
        lines.append(_line(type="checkpoint", t=ns(95_000), shown=True))
        lines.append(_line(type="n1", t=ns(96_000), left=False))

    session_end_ms = last_scroll_ms + 5 * 60_000 + 11_000
    lines.append(_line(type="foreground", t=ns(last_scroll_ms + 40_000), pkg="com.android.launcher"))
    lines.append(_line(
        type="session", t=ns(session_end_ms), event="end", sessionId="fx1", pkg=PKG,
        reason="AWAY_TIMEOUT", endedAtWallMs=WALL0_MS + last_scroll_ms + 40_000,
    ))
    return lines
