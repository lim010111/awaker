package com.awaker.detection

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.awaker.AppGraph
import com.awaker.core.CandidateApps

/**
 * 베타 한정 AS 스크롤 운동학 수집기 (이슈 04). 후보 앱의 스크롤·화면 갱신류
 * 이벤트만 받는다(res/xml/accessibility_service_config.xml — 화면 내용 조회
 * 없음, canRetrieveWindowContent=false). 출시 빌드에서는 이 경로 전체가 제거된다
 * (ADR-0004 약속; 판별 입력 X는 권한-경량 센서뿐, 터치/스크롤은 GT 전용 — ADR-0010).
 *
 * TYPE_VIEW_SCROLLED 외 타입은 이슈 09 갈래 A의 탐사(진단용 임시): YouTube가
 * 실제 방출하는 이벤트를 실측하기 위해 메타데이터(타입·패키지·시각)만
 * rate cap을 걸어 기록한다.
 */
class ScrollCaptureService : AccessibilityService() {

    // AS 콜백은 메인 스레드 단일 — 동기화 불요.
    private val probe = AsEventProbe()

    override fun onServiceConnected() {
        // 정적 XML의 packageNames는 폴백 — 설치된 브라우저까지 런타임 확장 (이슈 08).
        serviceInfo = serviceInfo.apply {
            packageNames = CandidateApps.resolve(packageManager).toTypedArray()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // eventTime은 uptimeMillis 클럭 — 공통 타임라인(elapsedRealtimeNanos)으로 변환.
        val tNs = SystemClock.elapsedRealtimeNanos() -
            (SystemClock.uptimeMillis() - event.eventTime) * 1_000_000

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            AppGraph.detection.onScroll(tNs, pkg, event.scrollDeltaX, event.scrollDeltaY)
            return
        }
        val typeName = AccessibilityEvent.eventTypeToString(event.eventType)
        if (probe.shouldRecord(event.eventTime, pkg, typeName)) {
            AppGraph.recording.onAsEvent(tNs, pkg, typeName)
        }
    }

    override fun onInterrupt() = Unit
}
