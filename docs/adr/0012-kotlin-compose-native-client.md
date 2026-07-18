# v1 클라이언트는 Kotlin + Jetpack Compose 네이티브로 구현한다

프로토타입 착수 전 스택 결정. 이 제품의 코드 무게중심은 플랫폼 계층에 있다 — AccessibilityService(teacher 룰·수집), 포그라운드 서비스 + 50–100Hz 센서 샘플링, UsageStatsManager 폴링, face-down 감지는 어떤 스택을 골라도 Kotlin으로 작성해야 한다. 크로스플랫폼 스택을 고르면 그 위에 *bridge 계층 유지비*만 추가된다.

결정타는 핵심 UI의 위치다: 체크포인트 바텀 시트는 자기 앱 화면이 아니라 **다른 앱 위 SYSTEM_ALERT_WINDOW 오버레이**다. Flutter는 오버레이 창마다 별도 엔진 인스턴스가 필요해(상주 메모리 + 라이프사이클·터치 버그가 잦은 영역) UI 프레임워크의 장점이 적용되어야 할 가장 중요한 표면에서 오히려 최약점이 된다. [[progressive-occlusion]]의 "시트가 매끄럽게 자라는" 애니메이션을 그 위에 얹을 수는 없다.

## Considered Options

- **Flutter** — 실질 이점은 인앱 화면(온보딩·설정·통계) 생산성뿐인데 그 면적이 전체의 ~20%. 크로스플랫폼 이점은 ADR-0002(Android-only) + iOS에서 제품 성립 자체가 불가하므로 무효. 오버레이·센서 스트리밍이 구조적 약점. 비채택.
- **Kotlin + XML 뷰** — Compose 대비 이점 없음. 비채택.

## Consequences

- 앱: Kotlin + Jetpack Compose, 프로토타입 minSdk 29 출발(본인 기기 기준, 베타 확장 시 재검토).
- 저장 기본값(튜닝 가능): 이벤트/세션은 Room, 50–100Hz 센서 raw는 append-only 파일(JSONL/binary) — DB에 샘플당 insert는 과부하.
- 오프라인 replay/분석 환경(ADR-0011)은 Python — 앱 스택과 독립.
- iOS 진입(별도 ADR 조건부, ADR-0002)이 열리더라도 코드 공유는 없음 — 그 시점의 제품 형태가 다르므로 감수.
