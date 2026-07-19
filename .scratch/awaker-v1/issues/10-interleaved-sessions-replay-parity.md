# 10 — 세션 인터리브 시 replay 재현성 붕괴: 전역 룰 상태 vs 세션별 로그 파일

Status: ready-for-agent

## Problem

앱별 세션은 5분 유예 동안 열려 있으므로 앱을 오가면 세션 파일이 동시에 여러
개 열린다(1일차 실측 최대 3개). 그런데:

- `TeacherRule`은 **전역 1인스턴스** — fling 윈도우가 앱 전환을 가로질러
  누적된다 (`DetectionPipeline`은 세션 전무일 때만 리셋).
- scroll/rule 라인은 **활성 세션 파일에만** 기록된다
  (`RecordingController.onScroll/onRule` → `active` 단일 라우팅).
- 활성 전환은 5s 포그라운드 폴 지연을 탄다 — YouTube 세션 파일 안에 Chrome
  scroll이 실존(오귀속).

결과: 단일 세션 파일은 기기 룰 입력의 **부분집합**만 담는다. 세션이 인터리브된
구간에서는 파일 단위 replay가 구조적으로 재현 불가 — ADR-0011 "로그만으로
디바이스 없이 판별 재현" AC가 깨진다. 1일차 대조: 3건 중 2건 불일치
(이슈 07 2026-07-19 코멘트 — Chrome 기기-만 enter 1, Instagram replay-만 enter 3).

## Decision (2026-07-19 grill — ADR-0014)

**옵션 1 확정 + 경계 통일**: 세션-스코프 상태 전부를 "활성 세션 변경"(폴링
tick 기준, null 포함) 단일 경계에서 리셋·재겨냥한다. 근거·트레이드오프·비채택
옵션은 ADR-0014. 그릴에서 코드로 확정한 사실:

- 기기 룰은 이미 후보→비후보에서 리셋된다(`sessionActive` =
  `tracker.activeSessionId != null`, TrackerService). 남은 격차는
  **후보→후보 직행 전환** 하나.
- replay는 처음부터 이 의미론(`replay.py` — 파일 앱이 포그라운드 아니면
  리셋)이므로 **replay 코드는 무변경**.
- 체크포인트 오귀속은 별도 버그: `CheckpointCoordinator`가 `Resumed -> Unit`
  — 겨냥이 "마지막 Started 세션"에 고정.
- 시트·가림 카운터도 룰과 같은 사각지대(`CheckpointScheduler`는
  `!sessionActive`에서만 Dismiss·리셋).

## 구현 범위 (이슈당 PR 1개 규약)

1. **룰 리셋 경계 확장** — `DetectionPipeline`이 직전 활성 세션 id를 기억,
   달라지면(null 포함) `rule.reset`. 리셋은 현행대로 무음(로그 라인 없음 —
   replay가 foreground 라인으로 동일 경계를 유도).
2. **체크포인트·N1 재겨냥** — `CheckpointCoordinator`가 열린 세션
   `Map<sessionId, Session>`을 유지: Started 추가+겨냥, Resumed 조회+겨냥
   (원래 `startWallMs` 보존 — "N분째" 메시지가 세션 시작 기준 유지), Ended
   제거+해제.
3. **시트·가림 리셋 경계 확장** — 활성 세션이 *바뀌면*(다른 후보로의 전환
   포함) 표시 중 시트 Dismiss + extensionCount 리셋. 후보→비후보의 기존
   동작은 불변.
4. **N1 예외 유지** — `pendingN1ShownAt`은 위 리셋에서 제외 (후보 앱 전체
   이탈 기준, ADR-0007 정의 그대로).

## 검증 계획

- **단위 테스트**: 리셋 경계(후보→후보 직행/런처 경유/복귀), 재겨냥(Resumed
  후 shown·choice·N1이 새 활성 세션에 기록), 시트 Dismiss·ordinal 리셋,
  N1 예외. 기존 테스트 갱신 포함.
- **파리티 재검증(합격 기준)**: 새 APK 설치 직후 adb 스크립트로 1일차 불일치
  패턴 재현 — Chrome↔YouTube↔sbrowser 직행 전환 + 각 앱 스와이프로 인터리브
  세션 3개 생성 → 로그 회수 → 파일별 replay 대조 **전 세션 일치**.
- **도구 회귀**: 1일차 일치 건(`awaker-3e2e888c…`)이 여전히 일치하는지 재확인.
- **보조망**: 이후 측정 기간의 실사용 로그로 재대조 (예상 밖 패턴 탐지).

## Evidence

- `awaker-7c8fdba6…`(YouTube 헤더) 내 Chrome scroll 라인.
- 이슈 07 코멘트의 대조 3건. 전 레코드 전수 스캔: 파일 간 중복 0 — 스트림이
  분할(파티션)됨을 확인.

**사용자 가시 효과 — 체크포인트 오귀속 (2026-07-19 저녁 추가)**: replay만의
문제가 아니다. 실례 `awaker-82d56acf…`(16:23 유튜브 세션): 유저는 190281s부터
삼성브라우저에 있었고(포그라운드 라인·sbrowser 세션 resume 일치) 190316s에
브라우저 플링 14회로 rule enter — 그런데 190411s/190476s 체크포인트 shown이
**유튜브 세션 파일에** 기록됐다(스케줄러가 마지막 Started 세션을 겨냥한 것으로
추정, Resume은 겨냥을 안 옮김). N1 판정도 엉뚱한 세션 기준으로 계산된다.
유저 체감 "유튜브에서도 팝업이 떴다"의 실체가 이것 — 전역 룰 잔여 상태 +
체크포인트 세션 귀속 혼선(+오버레이가 선택 전까지 앱 전환을 넘어 화면에 잔류).
옵션 결정 시 체크포인트/N1의 겨냥 세션 규칙도 함께 정리할 것.

## Blocked by

- 없음 — 방향 확정 완료, 구현 대기.

## Comments

**2026-07-19 (agent, merge-gate finding 수용)**: 리뷰 finding(choice·자발 종료가
실행 시점 `aim.current`를 참조 — 경계 Dismiss가 화면에 반영되기 전 탭이 들어오면
새 활성 세션에 귀속될 수 있음)을 유저 결정으로 수용. `handleShow`에서 겨냥
세션을 `shownSession`으로 붙잡아 `onChoice`(choice 기록·exit 플로우)가 그걸
쓰도록 수정 — choice는 "보였던 체크포인트의 세션"에 귀속. N1(`handleN1`)은
본문 규칙대로 활성 세션(`aim.current`) 유지. Coordinator는 Android 글루라 JVM
오라클 불가 — 실기기 파리티 검증에 포함.

**2026-07-19 (agent, implementation)**: 구현 범위 4항목 완료 (PR
`issue/10-active-session-single-boundary`). ① `DetectionPipeline`이 직전 활성
세션 id를 기억, 변경 시(null 포함) 무음 `rule.reset`. ② 재겨냥은 순수 클래스
`SessionAim`으로 추출 (Coordinator가 Android 글루라 JVM 테스트 불가 →
Map+겨냥 로직을 빼서 단위 테스트 가능하게; Coordinator가 보유·위임) — Started
추가+겨냥, Resumed 조회+겨냥(원래 startWallMs 보존), Ended 제거+해제.
③ `CheckpointScheduler`가 activeSessionId를 직접 받아 변경 경계에서 Dismiss +
extensionCount 리셋. ④ `pendingN1ShownAt`은 변경 경계에서 제외 — 후보 전체
이탈(null)에서만 left=true. 단위 테스트 23개(신규 12: 직행/런처 경유/복귀 리셋,
재겨냥, N1 예외) 전부 통과. replay 파이썬 무변경 확인 — pytest 18개 통과 +
1일차 일치 건(`awaker-3e2e888c…`) 재실행 일치 유지. 부수 발견: 구 동작은 세션
시작 직전(≤5s) 플링이 새 세션 룰 윈도우로 새어 들어갔는데, 단일 경계 리셋이
이것도 replay 의미론(세션 시작 = 빈 윈도우)에 맞춘다. 남은 것: 실기기 adb
인터리브 파리티 재검증 (PR 본문 대기 목록).

**2026-07-19 (agent, grill)**: 유저와 방향 그릴 완료 — 옵션 1 확정 + 세션-스코프
상태 단일 경계 통일(ADR-0014 기록, CONTEXT.md에 [[활성 세션]] 용어 추가).
유저 결정: "일관적이게 하나의 앱을 단위로." 구현·검증 범위는 본문 갱신.
구현은 별도 세션에서 진행 — 재설치는 이슈 09 탐사와 통합 APK로 1회
(순서: 구 APK 24h 증거 회수 → 설치 → adb 파리티 검증, 이슈 02 코멘트 참조).
