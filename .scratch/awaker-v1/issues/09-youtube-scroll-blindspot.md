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

**2026-07-20 (agent, 갈래 A 실기기 판정)**: 통합 APK(ddb9a2f) 설치 후 실측 —
**YouTube에서 AS 경로가 닫혀 있지 않다. `TYPE_WINDOW_CONTENT_CHANGED`가
스크롤과 강하게 상관, 주요 오탐 우려원(자동재생)은 침묵.**

- **스와이프 상관** (adb 스크립트, 스와이프는 AS 이벤트를 발생시킴 — Chrome
  대조 전례): 홈피드 플링 8회 → content-changed **35건/8.5s** (~4/s),
  Shorts 스와이프 6회 → **26건/15.4s**. scroll 레코드는 여전히 0 (1일차와
  동일 — TYPE_VIEW_SCROLLED 무방출 재확인). `TYPE_VIEW_SELECTED` 1건이
  Shorts 탭 진입 순간과 일치 — 표면 전환 마커로 쓸 수 있는 부수 신호.
- **아이들 대조군** (무입력): 홈피드 23s → content-changed **1건**, **Shorts
  자동재생 20s → 0건**. 비디오 재생 자체는 content-changed를 방출하지 않음 —
  "스크롤 프록시"의 1차 오탐 우려가 이 데이터셋에선 부재.
- **대조 표면 동시 관측**: Chrome scroll 317 + content-changed 170,
  sbrowser scroll 283 + content-changed 245 (같은 세션 파일) — scroll이 있는
  표면에서도 as_event가 나란히 찍혀 교차 검증 가능.
- **rate cap 실효**: 최소 간격 정확히 200ms(캡이 실제로 자름), 지속 플링
  165s 부하에서 1초 피크 5건(=캡 상한), 세션 최대 253건 — 홍수·배터리 폭주
  양상 없음. 배터리 회귀는 측정 기간 보조망으로 계속 관찰.
- **판정**: "아무것도 안 찍힘"이 아니라 반대 결과 — teacher 룰의 YouTube
  프록시 후보로 content-changed 빈도 신호가 성립할 가능성. **존속(정식
  프록시 채택, 별도 이슈/ADR) vs 제거는 사람 결정 대기.** 단, 이벤트에
  스크롤 방향·크기 정보는 없어(메타데이터만) 운동학 기반 룰의 직접 대체는
  아니고 빈도 기반 근사가 될 것. 로그: `day2/parity`(시나리오 A)·
  `parity-b`(B)·`idle-c`(대조군) — `~/.claude/jobs/f803f8ba/tmp/logs/`.

**2026-07-20 (agent, 갈래 B 지표 보강 — merge-gate finding 수용)**: 리뷰 지적
"sampled 마스크가 분모를 평가 대상 신호(gyro 유무)로 조건화 — 센서 드롭아웃
실패를 통계에서 지울 수 있다"를 데이터로 검증하고 지표를 커버리지/분류 2단으로
분리.

- **커버리지 (전체 윈도우 대비 gyro 보유)**: browser 1,071 중 301(28.1%) ·
  Instagram 492 중 119(24.2%) · YouTube 627 중 329(52.5%).
- **미샘플의 원인 분해**: IMU는 활성 세션 파일에만 기록된다
  (`RecordingController.onImu` → active 라우팅) — gyro 부재의 1차 설명은 "이
  세션이 활성이 아니던 구간"(인터리브·away). foreground 레코드(전 파일 기록)로
  검증: **자기 pkg가 포그라운드인데 gyro가 없는 윈도우 = browser 0/770 ·
  Instagram 0/373 · YouTube 1/298**(5s 폴 경계 잡음 수준). 활성 사용 중 센서
  드롭아웃은 관측되지 않음 — 우려한 "커버리지 실패 은폐"는 이 데이터셋에 없다.
- **지표 라벨 정정**: 아래 코멘트의 precision/recall은 **gyro-eligible(세션
  활성 + 센서 on) 윈도우 조건부**로 읽을 것 — 전체 타임라인 커버리지가 아니다.
  단, 세션 비활성 윈도우는 개입(체크포인트) 대상 자체가 아니므로 조건부 분모가
  런타임 의미론과 정합한다 — 남는 위험은 활성 중 드롭아웃뿐이고 그게 위
  0·0·1건.
- 검증 스크립트는 스크래치 일회성(`coverage_check.py`) — 하네스 승격 시
  stale-양성 보정과 함께 올릴 것.

**2026-07-19 (agent, 갈래 B 백테스트)**: `gyro_heuristic.py`(v0 상수 그대로)를
1일차 로그 31개 전수로 오프라인 실행. GT는 세션 파일별 replay 전이로 산출
(10s 윈도우, 이슈 07 하네스 재사용).

- **채점 하네스 교란 발견·보정**: replay는 away 중 무음 리셋(전이 미기록)이라
  `teacher_window_labels`가 away 구간을 stale-양성으로 유지 — 센서도 away에
  꺼지므로 FN이 부풀었다(보정 전 recall 17%/8%). **gyro 샘플이 있는 윈도우만
  채점**하도록 보정한 수치가 아래.
- **GT 표면 채점**: browser 15세션 301윈도우 — 일치율 63.1%, precision
  72.0%, recall 56.2%. Instagram 7세션 119윈도우 — 일치율 49.6%, precision
  74.2%, recall 30.7%. 발화하면 대체로 맞지만(precision ~72–74%) 놓침이 많고,
  세션 간 편차가 크다(인스타 2세션은 teacher+ 28윈도우를 전부 미탐 —
  자세/파지 상태 의존 추정).
- **YouTube 발화 양상** (GT 부재, 9세션 104분): 양성 0–18.8%/세션, 총 83윈도우
  발화. **센서 경로는 YouTube에서 구조적으로 침묵하지 않는다** — AS 경로
  (scroll 0–4건)와 대비. 단, 발화가 무지성과 상관인지는 GT가 없어 검증 불가
  (EMA 프로브 보완 경로 유지, ADR-0010).
- **트리거 counterfactual**: 최장 연속 양성 run이 전 세션에서 50s(YouTube)·
  70s(browser) — 현행 dwell 90s 의미론을 10s 윈도우 연속 run으로 근사하면
  **어느 표면에서도 체크포인트 0회**. v0 상수의 choppy한 윈도우 판정이 원인
  (단, 윈도우 독립 판정 ↔ 기기 연속 상태 머신은 단순 환산 불가 — 근사임).
- **한계**: ① v0 상수 미검증 초안 그대로 ② 1일차 로그는 이슈 10 수정 전
  기기 산출물이나 GT는 replay 재실행이라 replay 의미론 기준 자기일관
  ③ adb 스와이프는 IMU 시그니처가 없어 실사용 로그만 사용(본문 제약)
  ④ 단일 기기·단일 일자.
- **시사점**: 갈래 B의 질문("student가 YouTube를 커버 가능한가")에 대한 1차
  답은 "신호는 있다, v0 상수로는 부족". 다음 단계 후보: 상수 튜닝 + 윈도우
  스무딩(hysteresis) 후 재채점, 이후 측정 기간 로그로 보강. 채점 스크립트는
  이슈 07 하네스 위 일회성(스크래치) — 재사용 시 stale-양성 보정을 하네스에
  올리는 것 고려.

**2026-07-19 (agent, 갈래 A 구현)**: 탐사 코드 완료 (PR
`issue/09-as-eventtypes-probe`). 수신 타입을 `typeViewScrolled` +
`typeWindowContentChanged`·`typeWindowStateChanged`·`typeViewSelected`로 확장
— 텍스트를 담는 타입(typeViewTextChanged 등)은 수신 자체를 배제해 최소 수집
유지. 새 레코드 `as_event`(`t`·`pkg`·`et` 타입명 — 메타데이터만,
`canRetrieveWindowContent=false` 불변), 타입·패키지당 200ms rate cap
(`AsEventProbe`, 순수 Kotlin + 단위 테스트 5개). 접근성 고지 문구 동반 갱신
("스크롤·화면 갱신 등 이벤트 발생 사실(종류·시각)만 기록"). replay 파서는
unknown 타입 무시(전방 호환)라 무변경. 판정은 실기기: YouTube 홈피드·Shorts
사용 중 `as_event`에 스크롤 상관 이벤트가 찍히는가 — "아무것도 안 찍힘"도
유효한 결과(AS 경로 불가 증거로 갈래 닫음).

**2026-07-19 (agent, grill)**: 유저와 방향 그릴 완료 — 병행 확정. 갈래 A는
이 이슈의 PR로 APK에, 갈래 B는 오프라인 백테스트로 이 이슈 코멘트에.
구현은 별도 세션에서 진행. 설치 순서(구 APK 24h 증거 회수 → 통합 APK 설치
→ adb 검증)는 이슈 02 코멘트 참조.
