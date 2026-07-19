# 06 — 자발 종료 + 환기 사운드: face-down 검증 루프

Status: ready-for-human

## What to build

체크포인트에서 **자발 종료**를 선택하면: 자이로가 face-down을 검증 → **환기 사운드** 재생(번들 1~2개면 충분 — 전체 5~10개 풀·사용자 MP3(SAF)·수면 모드는 v1 범위로 이연) → 세션 종료 + 점진적 가림 즉시 리셋 (CONTEXT.md 자발 종료·환기 사운드 정의).

사운드 정지는 *기본 15분 타이머* 또는 *face-up 30초 유지* 중 먼저 오는 쪽. 자발 종료를 선택하고도 face-down을 하지 않으면 검증 실패 — 세션은 리셋되지 않는다(선택이 아니라 **검증된 행동**이 종료 조건).

## Acceptance criteria

- [ ] 자발 종료 선택 후 face-down이 감지되면 환기 사운드가 재생된다
- [x] face-down을 하지 않으면 세션·가림이 리셋되지 않는다
- [ ] 15분 타이머 또는 face-up 30초 유지 중 먼저 오는 쪽에서 사운드가 정지한다
- [ ] 검증 성공 시 세션 종료 + 가림 리셋 이벤트가 기록된다

## Blocked by

- 05-checkpoint-overlay-extension-n1.md — 체크포인트 선택지에서 진입

## Comments

**2026-07-18 (agent)**: 코드 완성 — `ExitVerifier`(순수 상태 머신: face-down z≤-7
2초 유지 → 검증 성공 / 30초 내 미수행 → 실패·세션 유지, 정지는 15분 타이머 ∨
face-up 30초 중 선착 — 단위 테스트 7건), `NoisePlayer`(핑크 노이즈 런타임 합성 —
번들 에셋 없이 CONTEXT.md 풀의 노이즈 항목 충당, 풀 확장·사용자 MP3·수면 모드는
v1 이연), `ExitFlowController`(자체 가속도 리스너 — 세션 종료 후에도 정지 판정
지속), 검증 성공 시 세션 즉시 종료(VOLUNTARY_EXIT) + 가림 리셋. 종료된 앱이
포그라운드를 한 번 떠나기 전 재시작하지 않도록 `SessionTracker` 억제 추가(테스트).
`exit_verify`/`sound` 로그 타입. AC 4개 전부 실기기 검증 대상 — PR 목록 참조.

**2026-07-19 (agent)**: 실기기 1일차 — 자발 종료 선택 2회 모두 face-down 미수행
→ `exit_verify verified:false` 기록, 세션은 리셋되지 않고 이후 AWAY_TIMEOUT으로
정상 종료(전 세션 31/31 AWAY_TIMEOUT과 정합) — AC2 체크. 사운드 경로(AC1·3·4)는
미실행(`sound` 레코드 0건) — 자발 종료 후 실제로 폰을 엎는 시나리오가 필요.
