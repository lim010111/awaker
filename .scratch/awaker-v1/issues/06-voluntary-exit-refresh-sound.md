# 06 — 자발 종료 + 환기 사운드: face-down 검증 루프

Status: ready-for-human

## What to build

체크포인트에서 **자발 종료**를 선택하면: 자이로가 face-down을 검증 → **환기 사운드** 재생(번들 1~2개면 충분 — 전체 5~10개 풀·사용자 MP3(SAF)·수면 모드는 v1 범위로 이연) → 세션 종료 + 점진적 가림 즉시 리셋 (CONTEXT.md 자발 종료·환기 사운드 정의).

사운드 정지는 *기본 15분 타이머* 또는 *face-up 30초 유지* 중 먼저 오는 쪽. 자발 종료를 선택하고도 face-down을 하지 않으면 검증 실패 — 세션은 리셋되지 않는다(선택이 아니라 **검증된 행동**이 종료 조건).

## Acceptance criteria

- [x] 자발 종료 선택 후 face-down이 감지되면 환기 사운드가 재생된다
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

**2026-07-19 (agent, 저녁)**: 유저가 사운드 경로 첫 실행 — "팝업 닫고 폰 잠금
누르니까 백색소음 들리긴 하는데". 소음이 재생됐다는 것은 검증이 성공했다는 뜻
(z≤−7 2초 유지 없이는 `NoisePlayer.start()` 경로가 없음) — 잠금 직후 폰을
엎어두는 자연스러운 동작이 검증 조건을 충족한 것으로 추정. 다음 기기 연결 때
로그(`exit_verify true` + `sound start` + `VOLUNTARY_EXIT`)로 확정 후 AC1 판단.

**UX 발견**: 유저가 "팝업이 떴을 때 엎어야 하나, 닫고 잠금을 눌러야 하나"를
몰랐음 — 자발 종료 선택 직후 해야 할 행동(30초 내 엎기)을 알려주는 안내가
시트 어디에도 없다. v1 전에 선택 직후 마이크로카피(예: "폰을 엎으면 환기
사운드가 시작돼요") 추가 검토.

**2026-07-20 (agent)**: AC1 판정 — **통과**. 기기 로그 회수(day2)로 어제 저녁
사운드 경로 실행 확정, 2건 모두 완전한 체인:

- `awaker-7f24eb88…` (sbrowser.beta, 20:45 KST 종료): checkpoint shown →
  choice `exit` → `exit_verify verified:true` → `sound start` → session end
  `VOLUNTARY_EXIT`.
- `awaker-a24d8694…` (sbrowser.beta, 21:45 KST 종료): 동일 체인.

관찰 2건 (AC3·AC4는 미체크 유지):

- **AC3 (정지 선착)**: `sound stop` 레코드가 전 파일 0건 —
  `RecordingController.onSound`가 "파일이 이미 닫혔으면 조용히 무시" 설계라
  VOLUNTARY_EXIT로 세션 파일이 닫힌 뒤 오는 정지 이벤트는 구조적으로 기록
  불가. 로그로는 판정할 수 없다 — 유저 체감(15분쯤 뒤 멈췄는지 / 폰을 뒤집자
  멈췄는지) 또는 stop 로그 경로 보완이 필요.
- **AC4 (세션 종료 + 가림 리셋 기록)**: 세션 종료(VOLUNTARY_EXIT)는 기록 ✓.
  가림 리셋은 전용 레코드가 없어(extensionCount는 메모리 상태) 직접 기록으로
  판정 불가 — 다음 checkpoint shown의 `ordinal:0`으로만 간접 관측 가능.
- 보너스: 두 choice 모두 Room `checkpoint_events` row(sessionId·choice=exit)와
  로그 라인 일치 — choice 경합(이슈 10 PR 표면화 항목) 미발현 사례 2건.
