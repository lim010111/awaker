package com.awaker.detection

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.awaker.AppGraph

/**
 * 베타 한정 AS 스크롤 운동학 수집기 (이슈 04). 후보 앱의 TYPE_VIEW_SCROLLED만
 * 받는다(res/xml/accessibility_service_config.xml — 화면 내용 조회 없음,
 * canRetrieveWindowContent=false). 출시 빌드에서는 이 경로 전체가 제거된다
 * (ADR-0004 약속; 판별 입력 X는 권한-경량 센서뿐, 터치/스크롤은 GT 전용 — ADR-0010).
 */
class ScrollCaptureService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val pkg = event.packageName?.toString() ?: return

        // eventTime은 uptimeMillis 클럭 — 공통 타임라인(elapsedRealtimeNanos)으로 변환.
        val tNs = SystemClock.elapsedRealtimeNanos() -
            (SystemClock.uptimeMillis() - event.eventTime) * 1_000_000
        AppGraph.detection.onScroll(tNs, pkg, event.scrollDeltaX, event.scrollDeltaY)
    }

    override fun onInterrupt() = Unit
}
