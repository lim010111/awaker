# v1은 Android-only로 출시한다

본 제품의 핵심 메커니즘(다른 앱 포그라운드 감지, 후보 앱 위 오버레이, 백그라운드 자이로/터치 수집)은 iOS에서 *원천적으로 막혀 있거나* Apple-통제 Screen Time API의 좁은 범위로 깎인다. iOS를 동시 지원하려면 *다른 제품*이 되거나(daily review 형태), 양쪽이 iOS 수준으로 깎임. 핵심 가설 — "센서로 무지성 스크롤을 잡고 자각시키면 사람들이 멈춘다" — 을 검증하기 *전에* iOS 제약을 안고 가는 건 가설 검증 비용에 끼워팔린 엔지니어링 부담이라 본 결정.

## Considered Options

- **Android + iOS 동시** — iOS 한계로 인해 *동시 출시 부담만 늘고 결과는 결국 별개 트랙*. 비추.
- **Android-first, iOS는 별도 트랙** — 본 결정과 사실상 동일하지만, "iOS도 곧 한다"는 *암묵적 약속*이 의사결정을 왜곡할 수 있어 명시적으로 *Android-only*로 못박음.

## Consequences

- iOS 진입은 *별도 ADR* 에서 진입 조건(DAU/잔존률 등)을 정한 뒤에만 검토. v1~v1.x 동안엔 의사결정 부담 0.
- 권한 키트가 *Android 전용*으로 굳어짐: UsageStatsManager, 자이로/가속도(상시), SYSTEM_ALERT_WINDOW 또는 알림, (검토 중) AccessibilityService, 포그라운드 서비스, Health Connect.
- 한국 외 시장은 iOS 비중이 더 큰 곳도 많음 — 글로벌 확장 시 *iOS 제약은 시장 진입의 첫 장벽*이 됨. 이걸 인지하고 출발.
- iOS 제약은 향후에도 완화될 가능성이 낮다고 전제 — 미리 짊어질 이유 없음.
