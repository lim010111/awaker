"""CLI — 재현성 검증 + 휴리스틱 채점 + 타임라인 플롯.

    python3 -m awaker_replay.cli report logs/awaker-<id>.jsonl
    python3 -m awaker_replay.cli plot logs/awaker-<id>.jsonl -o timeline.png
"""

from __future__ import annotations

import argparse
import json
import sys

from . import gyro_heuristic, replay, report
from .schema import load_log
from .windows import segment_windows


def cmd_report(args: argparse.Namespace) -> int:
    timeline = load_log(args.log)
    print(
        f"세션 {timeline.header.session_id} ({timeline.header.pkg}) — "
        f"레코드 {len(timeline.records)}개, 스키마 v{timeline.header.version}"
    )
    if timeline.unknown_types:
        print(f"무시한 unknown 타입: {sorted(timeline.unknown_types)}")

    transitions = replay.replay_rule(timeline)
    parity = replay.compare(timeline, transitions, tolerance_ms=args.tolerance_ms)
    print()
    print(parity.summary())

    windows = segment_windows(timeline)
    teacher = report.teacher_window_labels(windows, transitions)
    candidate = gyro_heuristic.label_windows(timeline, windows)
    result = report.score(teacher, candidate)
    print()
    print(result.summary())

    if args.json:
        payload = {
            "sessionId": timeline.header.session_id,
            "parityOk": parity.ok,
            "deviceTransitions": len(parity.device),
            "replayTransitions": len(parity.replayed),
            "matched": len(parity.matched),
            "windows": result.windows,
            "agreement": result.agreement,
            "falsePositive": result.false_positive,
            "falseNegative": result.false_negative,
        }
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"\nJSON 리포트: {args.json}")

    return 0 if parity.ok else 1


def cmd_plot(args: argparse.Namespace) -> int:
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib이 필요합니다: pip install matplotlib", file=sys.stderr)
        return 2

    timeline = load_log(args.log)
    t0 = timeline.records[0]["t"] if timeline.records else 0

    def rel_s(t_ns: int) -> float:
        return (t_ns - t0) / 1e9

    fig, axes = plt.subplots(3, 1, sharex=True, figsize=(12, 8))

    for kind, axis in (("gyro", axes[0]), ("accel", axes[1])):
        records = timeline.of_type(kind)
        ts = [rel_s(r["t"]) for r in records]
        for comp in "xyz":
            axis.plot(ts, [r[comp] for r in records], label=comp, linewidth=0.6)
        axis.set_ylabel(kind)
        axis.legend(loc="upper right")

    scrolls = timeline.of_type("scroll")
    axes[2].eventplot([rel_s(r["t"]) for r in scrolls], lineoffsets=1, colors="tab:blue")
    for r in timeline.of_type("rule"):
        color = "tab:red" if r["state"] == "enter" else "tab:green"
        axes[2].axvline(rel_s(r["t"]), color=color, linestyle="--", linewidth=1)
    axes[2].set_ylabel("scroll / rule")
    axes[2].set_xlabel("session time (s)")

    fig.suptitle(f"{timeline.header.session_id} ({timeline.header.pkg})")
    fig.tight_layout()
    fig.savefig(args.out, dpi=120)
    print(f"플롯 저장: {args.out}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="awaker-replay")
    sub = parser.add_subparsers(dest="command", required=True)

    p_report = sub.add_parser("report", help="재현성 검증 + 휴리스틱 채점")
    p_report.add_argument("log")
    p_report.add_argument("--tolerance-ms", type=int, default=replay.DEFAULT_TOLERANCE_MS)
    p_report.add_argument("--json", help="JSON 리포트 출력 경로")
    p_report.set_defaults(func=cmd_report)

    p_plot = sub.add_parser("plot", help="센서 타임라인 플롯 (이슈 03 AC)")
    p_plot.add_argument("log")
    p_plot.add_argument("-o", "--out", default="timeline.png")
    p_plot.set_defaults(func=cmd_plot)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
