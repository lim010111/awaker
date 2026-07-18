# 01 — 문서 위생 정리: 낡은 기획 산출물의 오염원 제거

Status: ready-for-agent

## What to build

설계 확정(ADR-0001~0012) 이전의 산출물이 현행 철학과 충돌한 채 레포에 남아 에이전트/독자를 오염시킨다. 이를 정리한다:

- `project_overview.md` — "5걸음 걷기/스쿼트 강제" 등 강제 개입 시나리오가 그대로 있음. 체크포인트는 강제하지 않는다는 현행 철학(CONTEXT.md, ADR-0003의 E1)과 정면 충돌. 역사 기록으로서 가치는 있으므로 삭제 대신 상단에 낡음 경고 헤더를 붙이고 현행 문서(CONTEXT.md + ADR)로 안내한다.
- `.query.md` — X/y 정의 혼란 메모. ADR-0010이 해소했으므로 삭제.

## Acceptance criteria

- [x] `project_overview.md` 상단에 "역사 문서 — 강제 개입 시나리오는 폐기됨, 현행은 CONTEXT.md + docs/adr/" 취지의 경고 블록이 있다
- [x] `.query.md`가 삭제됐다
- [x] CONTEXT.md·ADR 본문은 수정하지 않았다 (이 이슈는 낡은 산출물만 건드림)

## Blocked by

None - can start immediately

## Comments

**2026-07-18 (agent)**: 구현 완료 — `project_overview.md` 상단 경고 블록 추가,
`.query.md` 삭제. CONTEXT.md·ADR은 건드리지 않음 (diff로 확인 가능).
