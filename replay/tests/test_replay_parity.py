from awaker_replay.replay import compare, replay_rule
from awaker_replay.schema import parse_lines
from fixtures import EXPECT_ENTER_MS, abs_ms, synthetic_log


def test_replay_reproduces_device_transitions_within_tolerance():
    tl = parse_lines(synthetic_log())
    transitions = replay_rule(tl)

    assert [t.state for t in transitions] == ["enter", "exit"]
    assert transitions[0].at_ms == abs_ms(EXPECT_ENTER_MS)  # 스크롤 구동 — 결정적
    assert transitions[0].metrics.median_gap_ms == 2_000

    report = compare(tl, transitions)
    assert report.ok, report.summary()
    assert len(report.matched) == 2
    # enter는 정확 일치, exit은 tick 위상차(2s)만큼 어긋나되 허용 오차 안
    enter_match = report.matched[0]
    assert enter_match[2] == 0
    exit_match = report.matched[1]
    assert abs(exit_match[2]) == 2_000


def test_away_reset_produces_double_enter_on_both_sides():
    tl = parse_lines(synthetic_log(away=True))
    transitions = replay_rule(tl)
    # away 중 조용한 리셋 → exit 라인 없이 enter가 두 번 (스키마 문서화된 동작)
    assert [t.state for t in transitions] == ["enter", "enter", "exit"]
    report = compare(tl, transitions)
    assert report.ok, report.summary()


def test_parity_failure_is_detected():
    # 온디바이스 enter가 허용 오차(6s) 밖에 있으면 불일치로 보고돼야 한다
    tl = parse_lines(synthetic_log(device_enter_ms=EXPECT_ENTER_MS + 10_000))
    report = compare(tl, replay_rule(tl))
    assert not report.ok
    assert report.unmatched_device and report.unmatched_replay


def test_replay_needs_only_x_and_scroll_not_device_rule_lines():
    # rule 라인을 지워도 replay 전이는 동일 — 재현이 로그 자체에서 나옴을 확인
    with_rule = replay_rule(parse_lines(synthetic_log()))
    lines = [l for l in synthetic_log() if '"type": "rule"' not in l and '"type":"rule"' not in l]
    without_rule = replay_rule(parse_lines(lines))
    assert [(t.state, t.at_ms) for t in with_rule] == [(t.state, t.at_ms) for t in without_rule]
