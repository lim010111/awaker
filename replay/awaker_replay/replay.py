"""재현성 검증 — teacher 룰을 로그 위에서 재실행해 온디바이스 판정과 대조.

온디바이스 평가 시점 = 각 스크롤 이벤트 + 폴링 tick(세션 활성 중 ~5s). replay는
같은 시점 구조(스크롤 + 합성 5s tick)로 룰을 돌린다. tick 위상이 다르므로
전이 시각은 tick 간격만큼 어긋날 수 있다 → 허용 오차(기본 6s) 내 일치로 판정.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from .schema import Timeline
from .teacher_rule import TeacherRule, Transition

TICK_MS = 5_000  # 앱 Tunables.FOREGROUND_POLL_MS와 동기
DEFAULT_TOLERANCE_MS = 6_000


def replay_rule(timeline: Timeline, tick_ms: int = TICK_MS) -> list[Transition]:
    """scroll + foreground 라인으로 teacher 룰을 재실행한다.

    온디바이스 동작 미러링(DetectionPipeline): 세션 앱이 포그라운드가 아닌 동안의
    tick은 룰을 조용히 리셋한다(away 중 케이던스 단절). 스크롤은 무조건 공급 —
    실기기에서도 포그라운드일 때만 도착하는 신호라서 동작이 같다.
    """
    pkg = timeline.header.pkg
    scrolls = [r["t"] // 1_000_000 for r in timeline.of_type("scroll")]
    foregrounds = [
        (r["t"] // 1_000_000, r.get("pkg")) for r in timeline.of_type("foreground")
    ]
    start_ns = timeline.session_start_ns
    end_ns = timeline.session_end_ns
    start_ms = start_ns // 1_000_000 if start_ns is not None else (scrolls[0] if scrolls else 0)
    end_candidates = [end_ns // 1_000_000] if end_ns is not None else []
    if scrolls:
        end_candidates.append(scrolls[-1] + tick_ms)
    end_ms = max(end_candidates) if end_candidates else start_ms

    rule = TeacherRule()
    transitions: list[Transition] = []

    events = [(t, "scroll", None) for t in scrolls]
    events += [(t, "fg", fg) for t, fg in foregrounds]
    events += [(t, "tick", None) for t in range(start_ms, end_ms + 1, tick_ms)]
    events.sort(key=lambda e: e[0])

    current_fg: str | None = pkg  # 세션은 자기 앱이 포그라운드인 상태로 시작한다
    for at_ms, kind, payload in events:
        if kind == "fg":
            current_fg = payload
            continue
        if kind == "scroll":
            tr = rule.on_scroll(at_ms)
        elif current_fg == pkg:
            tr = rule.on_tick(at_ms)
        else:
            rule.reset(at_ms)  # away 중 — 온디바이스와 동일하게 조용히 리셋
            tr = None
        if tr:
            transitions.append(tr)
    # 세션 종료 = 암묵적 해제 (로그에 exit 라인 없음) — 대조 대상에 넣지 않는다.
    return transitions


@dataclass
class ParityReport:
    device: list[dict]
    replayed: list[Transition]
    matched: list[tuple[dict, Transition, int]] = field(default_factory=list)
    unmatched_device: list[dict] = field(default_factory=list)
    unmatched_replay: list[Transition] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.unmatched_device and not self.unmatched_replay

    def summary(self) -> str:
        lines = [
            f"재현성: 온디바이스 전이 {len(self.device)}건, replay 전이 {len(self.replayed)}건, "
            f"일치 {len(self.matched)}건",
        ]
        for dev, rep, dt in self.matched:
            lines.append(
                f"  ✓ {dev['state']:5s} device t={dev['t'] // 1_000_000}ms ↔ replay {rep.at_ms}ms (Δ{dt}ms)"
            )
        for dev in self.unmatched_device:
            lines.append(f"  ✗ 온디바이스에만 있음: {dev['state']} @ {dev['t'] // 1_000_000}ms")
        for rep in self.unmatched_replay:
            lines.append(f"  ✗ replay에만 있음: {rep.state} @ {rep.at_ms}ms ({rep.reason or ''})")
        lines.append("판정: " + ("일치 (허용 오차 내)" if self.ok else "불일치 — 원인 조사 필요"))
        return "\n".join(lines)


def compare(
    timeline: Timeline,
    replayed: list[Transition],
    tolerance_ms: int = DEFAULT_TOLERANCE_MS,
) -> ParityReport:
    """온디바이스 rule 라인과 replay 전이를 순서 보존 그리디로 짝짓는다."""
    device = timeline.of_type("rule")
    report = ParityReport(device=device, replayed=replayed)

    remaining = list(replayed)
    for dev in device:
        dev_ms = dev["t"] // 1_000_000
        match_idx = None
        for i, rep in enumerate(remaining):
            if rep.state == dev["state"] and abs(rep.at_ms - dev_ms) <= tolerance_ms:
                match_idx = i
                break
        if match_idx is None:
            report.unmatched_device.append(dev)
        else:
            rep = remaining.pop(match_idx)
            report.matched.append((dev, rep, rep.at_ms - dev_ms))
    report.unmatched_replay = remaining
    return report
