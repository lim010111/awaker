# 08 — 브라우저 후보 앱 동적 확장: 설치된 브라우저 자동 탐지

Status: ready-for-human

## What to build

브라우저에서도 무지성 스크롤을 잡는다. 브라우저는 종류가 많고 사용자마다 다르므로
하드코딩 목록 대신 **OS에 묻는다**: Android에서 브라우저란 `https` VIEW 인텐트를
범용(호스트 무제한)으로 처리하는 앱이다. `PackageManager.queryIntentActivities`로
설치된 브라우저를 런타임에 탐지해 `Tunables.DEFAULT_CANDIDATE_PACKAGES`와 합친다
(CONTEXT.md 후보 앱 정의 "기본 셋 + 사용자 추가/제외"의 탐지 메커니즘 — 사용자
조정 UI는 베타 확장에서).

AS 스크롤 수집 필터도 같은 셋을 봐야 하므로, 정적 XML(`packageNames`)은 폴백으로
두고 `onServiceConnected`에서 `serviceInfo`를 런타임 갱신한다. Android 11+ 패키지
가시성 제한 때문에 매니페스트 `<queries>` 선언이 필요하다.

주의: 도메인 특정 App Link 앱(예: YouTube가 youtube.com 링크 처리)이 딸려오지
않도록 프로브 URL은 범용 호스트(`https://example.com`)로 쿼리한다 — 호스트 제한
없는 진짜 브라우저만 매칭된다.

셋 해석은 서비스 시작 시 1회 — 서비스 기동 후 설치된 브라우저는 서비스 재시작
시 반영 (프로토타입 허용 범위).

## Acceptance criteria

- [ ] 설치된 브라우저가 후보 셋에 자동 포함된다 — `queryIntentActivities` 기반, 하드코딩 없음
- [ ] AS 스크롤 수집 필터가 런타임에 같은 셋으로 갱신된다 (정적 XML은 폴백)
- [ ] 실기기: 본인 브라우저(Samsung Internet/Chrome 등)에서 세션 시작 + 센서/scroll 라인이 로그에 기록된다
- [ ] 실기기: 브라우저 무지성 스크롤 시 teacher 룰 전이가 기록된다 — 읽기(정독) 세션과의 오탐 체감을 이슈 코멘트에 기록

## Blocked by

- #02 (세션 상태 머신), #04 (AS 수집) — 둘 다 머지 완료, 즉시 시작 가능

## Comments

**2026-07-19 (agent)**: merge-gate finding(codex, uphold) 수용 — 접근성 서비스
고지 문구(strings.xml)가 구 수집 범위(3개 앱)만 말하고 있었음. "이 기기에 설치된
브라우저" 포함으로 갱신. 교훈: 수집 범위가 바뀌면 권한 고지 문구도 같은 변경에서
함께 움직여야 한다.

**2026-07-19 (agent)**: 그릴 세션으로 확장 경로 문서화 — ADR-0013 신설 +
CONTEXT.md 후보 앱 항목 보강. 확정된 결정: (1) 자동 탐지는 OS가 역할을
정의하는 브라우저만, 그 외 앱은 기본 셋 하드코딩 → 사용자 선택 UI(베타),
(2) 사용자 제외가 자동 포함보다 항상 우선, (3) teacher 룰 임계는 단일 Config
유지 — 부류별 분화가 맞는 방향이라는 인식 아래의 데이터 부재 보류이며,
재검토 트리거는 이 이슈 AC의 정독 오탐 체감 기록 + self-annotation 실증.
