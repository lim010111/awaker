# 04 — AS 스크롤 운동학 수집 + teacher 룰 v0

Status: ready-for-human

## What to build

베타 한정 AccessibilityService(ADR-0004 — 출시 빌드에선 제거되는 경로)로 후보 앱의 스크롤 운동학(fling cadence, 콘텐츠 체류시간, 앱 맥락)을 03의 공통 타임라인에 기록하고, 그 위에서 **teacher 룰 v0**(ADR-0010의 dense 라벨원이자 ADR-0011의 라이브 트리거)를 온디바이스로 평가한다. 룰의 양성 진입/해제 상태 전이를 이벤트로 기록한다 — 이 이벤트가 05 체크포인트의 발동 입력이자 07 replay의 대조 기준이 된다.

룰 임계의 1차 튜닝은 본인 사용 데이터 + self-annotation으로 한다. ADR-0010 "남은 미결" 중 GT 룰 수식의 초안을 여기서 확정하는 것이 이 슬라이스의 산출물이다(ADR-0011 Consequences).

## Acceptance criteria

- [ ] AS 스크롤 이벤트가 센서 로그와 공통 타임라인에 정렬 기록된다
- [ ] teacher 룰 v0의 양성 진입/해제 전이가 이벤트로 기록된다
- [ ] 의도적 무지성 스크롤 흉내와 정독 스크롤에서 룰 판정이 갈리는 것을 self-annotation으로 확인했다
- [x] 확정한 룰 수식/임계 초안이 ADR-0011 갱신 또는 본 이슈 코멘트로 기록됐다

## Blocked by

- 03-sensor-raw-logging-and-export.md — 공통 타임라인 로깅 위에 얹힘

## Comments

**2026-07-18 (agent)**: 코드 완성. **teacher 룰 v0 수식/임계 초안** (구현:
`TeacherRule.kt`, 문서: `docs/log-schema.md`):

- fling = 스크롤 이벤트를 500ms debounce로 뭉친 제스처. 60s 슬라이딩 윈도우의
  fling 타임스탬프 f₁…fₙ, 간격 gᵢ에 대해
- **양성 진입** (모두 충족): n ≥ 8 ∧ span(fₙ−f₁) ≥ 30s ∧ median(g) ≤ 8s ∧ max(g) ≤ 20s
- **양성 해제** (하나라도): 침묵(now−fₙ) > 30s ∨ 윈도우 내 n < 4 ∨ 세션 종료(암묵)
- 직관은 CONTEXT.md 예시 대화("2초 간격 반사적 넘김 + 멈춤 없음 = 무지성,
  멈춰 읽는 구간 = 아님")의 조작화. span 조건은 순간 폭주 오탐 배제.

임계는 전부 `TeacherRule.Config` 기본값 — self-annotation(AC3) 후 재조정 대상.
AS 수집은 스크롤 이벤트만(canRetrieveWindowContent=false), 화면 내용 미조회.
AC 1·2(실기기 기록)와 AC 3(self-annotation)은 PR "실기기 검증 대기" 참조.
