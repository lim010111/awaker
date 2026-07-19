# 07 — 오프라인 replay 하네스: 로깅 스키마 AC의 집행자

Status: ready-for-human

## What to build

Python 분석 하네스(ADR-0011·0012). export된 로그를 파싱해 공통 타임라인을 재구성하고:

1. **재현성 검증** — teacher 룰을 replay로 재구현해 온디바이스 판정 이벤트(04)와 대조. 이 일치가 곧 ADR-0011의 로깅 스키마 acceptance criteria("로그만으로 디바이스 없이 판별 재현")의 기계적 증명이다.
2. **권한-경량 후보 채점** — 자이로 휴리스틱 후보 1개를 같은 로그에 돌려 teacher 룰 대비 일치율/오탐/미탐 리포트를 낸다. distillation 천장(ADR-0010)의 첫 예고편이자, 이후 student 모델 백테스트가 재사용할 파이프라인.

윈도우 분절이 X(권한-경량 센서)만으로 계산되는지 확인한다 — ADR-0010 train/serve 누수 관리 (a).

05·06과 병렬 진행 가능 — 04의 로그만 있으면 된다.

## Acceptance criteria

- [x] 실기기 로그에 대해 replay 룰 판정이 온디바이스 판정 전이와 허용 오차 내 일치한다 (불일치 시 원인이 문서화된다)
- [x] 윈도우 분절이 X만으로 계산됨을 확인했다
- [x] 자이로 휴리스틱 후보 1개 vs teacher 룰 비교 리포트(일치율·오탐·미탐)가 산출된다
- [x] 합성 픽스처 로그 기반 파서 테스트가 있어 스키마 회귀를 잡는다

## Blocked by

- 04-as-scroll-collection-teacher-rule.md — 대조할 온디바이스 판정 이벤트 필요 (05·06과는 병렬 가능)

## Comments

**2026-07-18 (agent)**: 코드 완성 — `replay/` Python 패키지(표준 라이브러리만,
플롯만 matplotlib 선택). `awaker_replay.cli report <log>`가 ① teacher 룰 replay
재현성 검증(허용 오차 기본 6s — tick 위상차 흡수), ② 자이로 휴리스틱 v0 vs
teacher 윈도우 채점(일치율/오탐/미탐)을 한 번에 산출. 합성 픽스처 데모:
재현성 일치(Δ0ms/Δ2000ms), 채점 40윈도우 일치율 87.5% (오탐 3/미탐 2).
윈도우 분절(10s 격자, 세션 start 앵커)은 X 타입만 소비 —
`test_segmentation_is_x_only`가 AS 라인 유무 무관 동일 분절을 강제 (ADR-0010 (a)).
`teacher_rule.py`는 `TeacherRule.kt`와 의도적 1:1 복제 (README에 동기화 규칙).
pytest 18건. AC1(실기기 로그 대조)은 실기기 로그 export 후 `cli report`로 —
불일치 시 원인을 이 코멘트에 기록할 것. 플롯(`cli plot`)은 이슈 03 AC2 도구.

**2026-07-19 (agent)**: 실사용 로그 3건 대조 (AC1):

- 삼성브라우저 3e2e888c — **일치** (enter Δ0ms, exit Δ−2.2s, 허용 오차 내).
- Chrome aa14f672 — **불일치**: 기기 enter 1건(@168478669ms)이 replay에 없음.
- Instagram 14ab4451 — **불일치**: replay-만 enter 3건.

**원인 규명 (문서화 조건 충족 → 체크)**: `TeacherRule`은 전역 1인스턴스로 앱
전환을 가로질러 fling 윈도우를 누적하는데, 로그는 세션(앱)별 파일로 분할된다.
동시 열림 세션 최대 3개 실측 — scroll/rule 라인은 활성 세션 파일에만 기록되고,
활성 전환은 5s 포그라운드 폴 지연을 타서 오귀속도 발생(YouTube 세션 파일 안의
Chrome scroll 실존). 결과: 단일 파일은 기기 룰 입력의 부분집합만 보므로 세션이
인터리브된 구간에서 파일 단위 replay가 구조적으로 재현 불가. ADR-0011의 "로그만
으로 재현" AC를 세션 인터리브가 깨는 설계 결함 — 구조 결정은 이슈 10으로 분리.
휴리스틱 채점은 일치 세션에서만 유효(3e2e888c: 일치율 76.7%, 오탐 14/미탐 21).
