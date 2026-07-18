# 03 — 센서 raw 로깅 + 수동 export: replay 가능한 공통 타임라인

Status: ready-for-human

## What to build

세션이 활성인 동안만 센서 융합 입력의 raw를 녹화한다: 자이로/가속도 3축(50–100Hz), 조도, 화면 상태/배터리, 그리고 02의 세션·포그라운드 이벤트 — 전부 **공통 타임라인에 정렬**된 append-only 파일(ADR-0012: DB에 샘플당 insert 금지). 세션 밖에서는 샘플링을 완전히 정지한다(배터리).

이 로그의 존재 이유는 ADR-0011의 acceptance criteria — **"로그만으로 디바이스 없이 판별을 오프라인 재현 가능"** — 이다. 스키마에는 버전 필드와, 베타 확장 빌드에서 쓸 EMA 프로브 이벤트 타입 자리를 미리 예약한다(ADR-0010, 지금은 기록 주체 없음). 반출은 adb/공유 시트 수동 export로 충분하다(1차 코호트 = 본인).

## Acceptance criteria

- [ ] 세션 중에만 기록되고 세션 밖에서는 샘플링이 정지한다
- [ ] export한 로그를 노트북에서 파싱해 세션 하나의 센서 타임라인을 플롯할 수 있다
- [x] 스키마에 버전 필드와 EMA 프로브 이벤트 타입이 예약돼 있다
- [ ] 샘플링 활성 세션의 배터리 소모 실측치(시간당 %)가 이슈 코멘트에 기록된다

## Blocked by

- 02-candidate-app-session-detection.md — 세션 게이트가 있어야 기록 시작/정지가 성립

## Comments

**2026-07-18 (agent)**: 코드 완성 — 스키마 문서(`docs/log-schema.md`, v=1 + `ema_probe`
예약), 세션당 append-only JSONL(`JsonlFileSink`), `RecordingController`(세션 활성
중에만 센서 가동 — 유예/화면꺼짐/세션밖 리스너 0, 단위 테스트 8건), 공통 타임라인
(elapsedRealtimeNanos + 헤더 벽시계 앵커), 인앱 로그 카드(공유 시트) + adb pull 경로.
AC 2·4(실기기 로그 플롯, 배터리 실측)와 AC 1의 실기기 확인은 PR "실기기 검증 대기" 참조.
