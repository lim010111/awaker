import json

import pytest

from awaker_replay.schema import SchemaError, parse_lines
from fixtures import PKG, T0_NS, synthetic_log


def test_parses_fixture_header_and_records():
    tl = parse_lines(synthetic_log())
    assert tl.header.version == 1
    assert tl.header.session_id == "fx1"
    assert tl.header.pkg == PKG
    assert tl.header.elapsed_ns == T0_NS
    assert tl.session_start_ns == T0_NS
    assert tl.session_end_ns is not None
    assert len(tl.of_type("gyro")) > 1000
    assert len(tl.of_type("scroll")) == 45


def test_records_sorted_by_t_even_if_lines_interleaved():
    lines = synthetic_log()
    # 스레드 인터리빙 흉내: 본문 라인 순서를 뒤집는다 (header 제외)
    shuffled = [lines[0]] + list(reversed(lines[1:]))
    tl = parse_lines(shuffled)
    ts = [r["t"] for r in tl.records]
    assert ts == sorted(ts)


def test_unknown_types_ignored_but_reported():
    tl = parse_lines(synthetic_log(include_future_types=True))
    assert "checkpoint" in tl.unknown_types
    assert "n1" in tl.unknown_types
    assert not any(r["type"] in ("checkpoint", "n1") for r in tl.records)


def test_missing_header_raises():
    with pytest.raises(SchemaError, match="header"):
        parse_lines(['{"type":"gyro","t":1,"x":0,"y":0,"z":0}'])


def test_duplicate_header_raises():
    lines = synthetic_log()
    with pytest.raises(SchemaError, match="duplicate"):
        parse_lines(lines + [lines[0]])


def test_truncated_last_line_tolerated():
    lines = synthetic_log()
    tl = parse_lines(lines + ['{"type":"gyro","t":99,"x":0.'])
    assert tl.records  # 파싱은 성공하고 잘린 라인만 버린다


def test_future_schema_version_warns():
    lines = synthetic_log()
    head = json.loads(lines[0])
    head["v"] = 2
    with pytest.warns(UserWarning, match="schema version"):
        parse_lines([json.dumps(head)] + lines[1:])
