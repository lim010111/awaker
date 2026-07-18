from awaker_replay.schema import parse_lines
from awaker_replay.windows import WINDOW_MS, segment_windows
from fixtures import SESSION_END_MS, abs_ms, synthetic_log


def test_windows_cover_session_with_fixed_grid():
    tl = parse_lines(synthetic_log())
    windows = segment_windows(tl)
    assert windows[0].start_ms == abs_ms(0)
    assert all(w.end_ms - w.start_ms <= WINDOW_MS for w in windows)
    assert windows[-1].end_ms == abs_ms(SESSION_END_MS)
    for a, b in zip(windows, windows[1:]):
        assert b.start_ms == a.end_ms


def test_segmentation_is_x_only():
    """AS 라인(scroll/rule)을 전부 빼도 분절이 동일 — ADR-0010 누수 관리 (a)."""
    with_as = segment_windows(parse_lines(synthetic_log(include_as=True)))
    without_as = segment_windows(parse_lines(synthetic_log(include_as=False)))
    assert with_as == without_as
