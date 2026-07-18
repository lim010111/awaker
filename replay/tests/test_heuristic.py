import random

from awaker_replay.gyro_heuristic import label_windows, window_label
from awaker_replay.replay import replay_rule
from awaker_replay.report import score, teacher_window_labels
from awaker_replay.schema import parse_lines
from awaker_replay.windows import segment_windows
from fixtures import synthetic_log


def _samples(base: float, spikes: int, n: int = 500) -> list[float]:
    rng = random.Random(7)
    out = [abs(rng.gauss(base, base / 3 + 1e-9)) for _ in range(n)]
    step = n // (spikes + 1) if spikes else n
    for i in range(spikes):
        out[(i + 1) * step] = 1.5
    return out


def test_still_with_fling_spikes_is_positive():
    assert window_label(_samples(0.03, spikes=5), window_ms=10_000)


def test_constant_motion_is_negative():
    # 걷기/이동: 정지 비율 낮음
    assert not window_label(_samples(0.5, spikes=5), window_ms=10_000)


def test_still_without_spikes_is_negative():
    # 영상 정주행: 폰은 정지지만 fling이 없음
    assert not window_label(_samples(0.03, spikes=0), window_ms=10_000)


def test_empty_window_is_negative():
    assert not window_label([], window_ms=10_000)


def test_scoring_pipeline_on_fixture():
    tl = parse_lines(synthetic_log())
    windows = segment_windows(tl)
    teacher = teacher_window_labels(windows, replay_rule(tl))
    candidate = label_windows(tl, windows)
    result = score(teacher, candidate)

    assert result.windows == len(windows)
    assert result.agree + result.false_positive + result.false_negative == result.windows
    assert result.teacher_positive > 0
    # 스크롤 케이던스 구간에서 후보도 양성을 내는지 — 파이프라인 유효성의 최소 보증
    assert result.agreement > 0.5
    assert "일치율" in result.summary()
