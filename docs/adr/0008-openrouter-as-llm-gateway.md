# LLM 게이트웨이로 OpenRouter 채택

ADR-0005에서 결정한 클라우드 LLM 호출을 *각 provider API에 직결*하지 않고 **OpenRouter**를 게이트웨이로 사용한다. 우리가 이 ADR을 쓰기 직전 후보로 검토했던 *자체 LLMClient 추상화 레이어*가 OpenRouter 자체로 흡수되며, ADR-0005의 silent-fallback 약속이 *provider 레벨 라우팅*으로 한 층 더 단단해진다.

## 채택 이유

1. **벤더 추상화 내장** — 모델 교체가 *config 한 줄*. v2에서 비용/품질 데이터 보고 라우팅 재구성 시 코드 변경 없음.
2. **자동 provider fallback** — Anthropic 다운이면 Google로, Google 다운이면 등으로 즉시 라우팅. ADR-0005의 "[[local-template-pool]] 로 silent fallback" 이 트리거되기 *전* 한 층 더 보호.
3. **통합 빌링** — ADR-0005의 일 8회/사용자 캡 운영을 단일 대시보드에서.
4. **Latency 영향 미미** — 추가 50~200ms는 prefetch 90s 윈도우(ADR-0001) 안에서 무시 가능.

## 기본 라우팅 — 미정 (사용자 별도 확정)

`openrouterteam/skills@openrouter-models` 스킬로 후보 모델 평가 후 본 ADR 업데이트 예정. 평가 축:

- **Korean short-output 톤 변주 품질** (메시지 1~3 문장의 자연스러움 + 톤 일관성)
- **비용** (1K tokens 단가 × 일 8회 × MAU 시뮬레이션)
- **Provider 데이터 정책** (학습 데이터화 회피, 로깅 정책)

## 출시 전 확인 (블로커)

- [ ] OpenRouter prompts/completions logging *기본 off* 또는 *opt-out 가능*
- [ ] DPA(Data Processing Agreement) 가능 여부 + 데이터 거주 지역
- [ ] 라우팅 후보 *모든 provider*가 paid tier (free tier = 학습 데이터화 위험)
- [ ] OpenRouter의 *provider 데이터 정책 필터*로 학습용 사용 안 하는 provider만 허용 설정

위 항목 통과해야 v1 출시.

## Considered Options

- **Provider 직결 (Anthropic / OpenAI / Google SDK)** — *자체 추상화*를 구현해야 v2 다중 벤더 가능. OpenRouter가 이미 제공하는 기능을 다시 만드는 셈.
- **HyperCLOVA X 단일 직결** — 한국어 품질 강하나 단일 벤더 락인 + 가격 부담. v2 검토.
- **자체 추상화 + 다중 벤더 SDK** — 통제 ↑이지만 v1 범위 과대.

## Consequences

- 데이터 경로에 hop 두 개 추가: *Awaker 자체 백엔드* (TS, OpenRouter SDK 호스트) + *OpenRouter 게이트웨이*. ADR-0005 업데이트로 반영. 백엔드 자체 결정(인증/로깅/호스팅)은 [[ADR-0009]] 에서 별도 grill.
- 모델 교체가 *코드 변경 없이* 가능 → A/B 테스트·실시간 fallback 운영 자유도 ↑.
- OpenRouter 게이트웨이가 *새로운 단일 의존점*. 그 아래 provider fallback은 OpenRouter가 책임지지만, *OpenRouter 자체가 다운되면 모두 다운* → [[local-template-pool]] fallback이 그 시점 작동.
- 가격은 underlying provider 대비 ~5% 프리미엄 — ADR-0005의 일 8회 캡 시뮬레이션에 반영.
