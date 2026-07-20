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
 * rate cap을 걸어 기록한다. 수신 마스크는 텍스트류 제외 전수 — 후보 신호를
 * 미리 좁히지 않기 위해서다 (2026-07-20 grill). 승격 시 채택 신호만 남기고
 * 도로 좁힌다.
 */
class ScrollCaptureService : AccessibilityService() {

    // AS 콜백은 메인 스레드 단일 — 동기화 불요.
    private val probe = AsEventProbe()

    override fun onServiceConnected() {
        // 정적 XML의 packageNames·eventTypes는 폴백 — 패키지는 설치된 브라우저까지
        // (이슈 08), 수신 타입은 텍스트류 제외 전수로 런타임 확장 (이슈 09 탐사).
        serviceInfo = serviceInfo.apply {
            packageNames = CandidateApps.resolve(packageManager).toTypedArray()
            eventTypes = PROBE_EVENT_TYPES
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

    companion object {
        /**
         * 탐사 수신 마스크: 전수에서 *내용(텍스트)을 담는 것이 본질인 타입*만 제외.
         * 기록은 어차피 메타데이터(타입·패키지·시각)뿐이지만, 이 타입들은 수신
         * 자체를 하지 않는다 — 최소 수집 원칙 (accessibility_service_config.xml).
         */
        val PROBE_EVENT_TYPES: Int = AccessibilityEvent.TYPES_ALL_MASK and
            (AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY or
                AccessibilityEvent.TYPE_ANNOUNCEMENT or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT).inv()
    }
}
