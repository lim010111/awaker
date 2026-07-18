# awaker-replay

오프라인 replay 하네스 (ADR-0011·0012, 이슈 07). export된 세션 로그(JSONL,
`docs/log-schema.md`)를 파싱해 공통 타임라인을 재구성하고:

1. **재현성 검증** — teacher 룰 v0를 replay로 재구현해 온디바이스 `rule` 전이와
   대조. 이 일치가 ADR-0011 로깅 스키마 AC("로그만으로 디바이스 없이 판별 재현")의
   기계적 증명이다.
2. **권한-경량 후보 채점** — 자이로 휴리스틱 후보를 같은 로그에 돌려 teacher 룰
   대비 일치율/오탐/미탐 리포트를 낸다. 이후 student 모델 백테스트가 재사용할
   파이프라인의 시작.

의존성은 표준 라이브러리뿐이다 (플롯만 matplotlib 선택 의존).

## 사용

```bash
# 실기기 로그 가져오기
adb pull /sdcard/Android/data/com.awaker/files/logs/ ./logs/

# 재현성 검증 + 휴리스틱 리포트
python3 -m awaker_replay.cli report logs/awaker-<sessionId>.jsonl

# 센서 타임라인 플롯 (이슈 03 AC; matplotlib 필요)
python3 -m awaker_replay.cli plot logs/awaker-<sessionId>.jsonl -o timeline.png

# 테스트 (합성 픽스처 기반 — 스키마 회귀 감시)
uv run pytest
```

## teacher 룰 파리티

`awaker_replay/teacher_rule.py`는 앱의 `TeacherRule.kt`와 **의도적으로 1:1
복제** 관계다 (debounce·윈도우·임계·median 인덱싱까지). 한쪽을 바꾸면 반드시
같이 바꿀 것 — 불일치는 `cli report`의 재현성 검증이 잡는다.
