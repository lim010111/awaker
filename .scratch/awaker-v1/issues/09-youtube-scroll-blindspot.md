# 09 — YouTube AS-scroll 공백: 대표 무지성 표면이 teacher 룰 사각지대

Status: needs-triage

## Problem

YouTube 앱은 `TYPE_VIEW_SCROLLED` 접근성 이벤트를 사실상 방출하지 않는다.
실기기 1일차(2026-07-19, S21+) 실측:

- 실사용 9세션 104분 → scroll 레코드 **3건** (전부 dy=0, dx=±1 — 위젯 잡음).
  같은 날 삼성브라우저beta 1,750건 / Instagram 1,310건 / Chrome 690건.
- 통제 실험: 홈피드 실플링 8회 + Shorts 스와이프 6회 → **0건**.
  동일 방식 Chrome 5플링 → 20건 (스크립트 스와이프 자체는 기록됨을 증명).

결과: teacher 룰(이슈 04)이 YouTube에서 구조적으로 침묵 — 체크포인트 노출
19회 중 YouTube 포그라운드 0회. 유저 1일 체감("유튜브에선 안 떴다")과 일치.

## Why it matters

- Shorts는 무지성 스크롤의 대표 표면인데 v0 개입 루프가 완전히 비어 있다.
- 더 아프게는 GT 경로: teacher 룰이 ADR-0010의 dense 라벨원인데, YouTube에선
  라벨도 생산 불가 → self-annotation·student 학습 데이터에서 YouTube가 빠진다.

## Options (결정 필요 — grill 후보)

1. **AS eventTypes 확장 탐사** — `TYPE_WINDOW_CONTENT_CHANGED` 등 YouTube가
   실제로 방출하는 이벤트를 조사해 스크롤 프록시로 쓸 수 있는지 실험.
   주의: 수집 범위가 넓어지므로 고지 문구 동반 갱신(이슈 08 교훈), 이벤트량
   증가로 필터·배터리 검토 필요. 화면 내용 미조회 원칙은 유지 가능.
2. **센서 단독 시그니처로 커버** — 제품 최종 방향(권한-경량 X)의 선취.
   브라우저·인스타에서 얻는 (X, teacher) 쌍으로 학습한 판별이 앱 불가지론적
   행동 상태(CONTEXT.md 무지성 스크롤 정의)로 YouTube에 일반화되는지 replay로
   백테스트. YouTube 전용 GT 부재는 EMA 프로브(베타)로 보완.
3. **YouTube용 별도 프록시** — 미디어 세션/오디오 포커스 등 간접 신호. 탐사
   비용 대비 불확실.

옵션 1·2는 상충하지 않는다 — 1로 GT 공백을 메우고 2로 최종 경로를 검증하는
병행이 자연스러움. 단, 1은 코드+고지 변경이라 재설치(24h 시계 리셋)를 수반.

## Evidence

- 로그: `awaker-8bd1f935…`(통제 실험 YouTube, scroll 0), `awaker-23bc0212…`
  (대조 Chrome, scroll 20). 이슈 04 2026-07-19 코멘트.

## Blocked by

- 없음 — 단, 방향 결정(위 옵션)은 유저 그릴 필요.
