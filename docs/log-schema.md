# 센서 로그 스키마 v1

세션당 1개, append-only JSONL 파일: `<externalFilesDir>/logs/awaker-<sessionId>.jsonl`

존재 이유(ADR-0011): **"로그만으로 디바이스 없이 판별을 오프라인 재현 가능"** —
센서 raw 전체 + 포그라운드/세션 이벤트가 공통 타임라인에 정렬돼야 하고, 이후
이슈 04의 AS 스크롤 이벤트·teacher 룰 전이도 같은 타임라인에 얹힌다. 이 파일이
스키마의 단일 기준이며, 오프라인 replay 하네스(이슈 07)가 이 스키마의 집행자다.

## 공통 타임라인

- 모든 레코드의 `t`는 `SystemClock.elapsedRealtimeNanos()` 클럭의 나노초.
  센서 이벤트(`SensorEvent.timestamp`)는 같은 클럭이라 무변환으로 기록한다.
- 벽시계(ms) 소스 이벤트(UsageStats 폴링, 세션 판정)는 헤더의 앵커 쌍
  (`wallMs` ↔ `elapsedNs`)으로 변환한다: `t = elapsedNs + (eventWallMs − wallMs) × 10⁶`.
- 파서는 모르는 `type`을 무시해야 한다(전방 호환) — 이슈 04~06이 타입을 추가한다.

## 레코드 타입

첫 줄은 반드시 `header`. 이후 순서는 기록 시점 순(같은 파일 내 t 단조 비보장 —
서로 다른 스레드가 쓰므로 파서가 t로 정렬).

| type | 필드 | 기록 조건 |
|------|------|-----------|
| `header` | `v`(스키마 버전, 현재 1), `sessionId`, `pkg`, `wallMs`, `elapsedNs`, `model`, `sdk`, `app` | 파일 첫 줄 |
| `gyro` | `t`, `x`, `y`, `z` (rad/s) | 세션 활성(후보 앱 포그라운드) 중 50–100Hz |
| `accel` | `t`, `x`, `y`, `z` (m/s²) | 상동 |
| `light` | `t`, `lux` | 상동, on-change |
| `screen` | `t`, `on` | 변화 시, 파일 열려 있는 동안 |
| `battery` | `t`, `pct`, `charging` | 60초 주기, 파일 열려 있는 동안 |
| `foreground` | `t`, `pkg`(null = 미확인/홈) | 변화 시, 파일 열려 있는 동안 |
| `session` | `t`, `event`(`start`/`resume`/`end`), `sessionId`, `pkg`, `reason?`, `awayMs?`, `endedAtWallMs?` | 세션 경계 |
| `ema_probe` | **예약** — `t`, `probe`(`rule_positive`/`random`), `answer` | 기록 주체 없음. 베타 확장 빌드의 순간-EMA 타당성 프로브(ADR-0010) 자리 |

이슈 04 추가 예정: `scroll`(AS 운동학 raw), `rule`(teacher 룰 enter/exit + 판정 메트릭)
이슈 05/06 추가 예정: `checkpoint`, `n1`, `exit_verify`, `sound`

## 샘플링 정책 (이슈 03 AC)

- **세션 활성 중에만** IMU/조도 샘플링. 후보 앱이 포그라운드를 떠나면(유예 포함)
  샘플링 정지, 복귀 시 재개. 세션 밖에서는 리스너 등록 자체가 없다(배터리).
- 유예 중에도 파일은 열려 있어 `screen`/`battery`/`foreground`/`session` 이벤트는
  계속 기록된다 — 종료 판정(5분 룰)까지의 문맥 보존.

## Export

- 인앱 "로그" 카드에서 공유 시트(FileProvider) 또는
  `adb pull /sdcard/Android/data/com.awaker/files/logs/` (1차 코호트 = 본인 기기).
