# 09 — YouTube AS-scroll 공백: 대표 무지성 표면이 teacher 룰 사각지대

Status: ready-for-agent

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

## Decision (2026-07-19 grill): 옵션 1+2 병행

두 갈래는 서로 다른 질문에 답한다 — A는 "teacher(GT 수집)가 YouTube를 커버
가능한가", B는 "student(출시 형태)가 YouTube를 커버 가능한가". A 성공 시
YouTube GT로 B를 더 잘 훈련시키고, A 실패해도 B가 되면 제품은 산다. B는
APK·기기 비용 0이라 병행의 추가 비용이 사실상 없다. 옵션 3(별도 프록시)은
탐사 비용 대비 불확실로 보류.

**갈래 A — AS eventTypes 확장 탐사** (이 이슈의 PR, APK 변경):

- `ScrollCaptureService` 수신 타입을 넓혀(`TYPE_WINDOW_CONTENT_CHANGED` 등)
  YouTube가 실제로 방출하는 이벤트를 실측. 기록은 이벤트 **메타데이터만**
  (타입·패키지·시각 — `canRetrieveWindowContent=false` 유지, 화면 내용
  미조회 원칙 불변). 새 로그 레코드 타입 + 타입별 rate cap(홍수·배터리 방지).
- **접근성 고지 문구가 수집 범위와 함께 갱신**되어야 한다 (이슈 08 교훈,
  ce39a4d 전례).
- 탐사는 진단용 임시 코드 — 결과를 보고 존속(정식 프록시 채택 시 별도
  이슈/ADR) 또는 제거를 결정한다.
- 판정: YouTube 홈피드·Shorts 사용 중 새 레코드에 스크롤과 상관된 이벤트가
  찍히는가. **"아무것도 안 찍힘"도 유효한 결과** — AS 경로 불가의 증거로
  이 갈래를 확실히 닫는다.

**갈래 B — 센서 단독 일반화 백테스트** (오프라인 분석, APK 무변경):

- `gyro_heuristic.py`(이슈 07 채점 하네스)를 브라우저·인스타 세션의 teacher
  GT로 채점 → 같은 휴리스틱을 YouTube 실사용 세션(1일차 9세션/104분, 48Hz
  IMU 확보됨)에 적용해 발화 양상 검토.
- **제약**: adb 스크립트 스와이프는 폰이 물리적으로 안 움직여 IMU 시그니처가
  없다 — 갈래 B 검증은 실사용 로그로만 가능(1일차분 확보, 이후 측정 기간
  로그로 보강).
- 결과는 이 이슈 코멘트에 기록. YouTube 전용 GT 부재는 EMA 프로브(베타)로
  보완하는 기존 경로(ADR-0010) 유지.

## Evidence

- 로그: `awaker-8bd1f935…`(통제 실험 YouTube, scroll 0), `awaker-23bc0212…`
  (대조 Chrome, scroll 20). 이슈 04 2026-07-19 코멘트.

## Blocked by

- 없음 — 방향 확정 완료, 구현 대기. 재설치는 이슈 10 수정과 통합 APK로 1회.

## Comments

**2026-07-19 (agent, grill)**: 유저와 방향 그릴 완료 — 병행 확정. 갈래 A는
이 이슈의 PR로 APK에, 갈래 B는 오프라인 백테스트로 이 이슈 코멘트에.
구현은 별도 세션에서 진행. 설치 순서(구 APK 24h 증거 회수 → 통합 APK 설치
→ adb 검증)는 이슈 02 코멘트 참조.
