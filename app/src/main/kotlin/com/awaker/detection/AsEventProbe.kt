package com.awaker.detection

/**
 * AS 이벤트 타입 탐사의 rate cap (이슈 09 갈래 A, 순수 Kotlin — 진단용 임시).
 * YouTube가 TYPE_VIEW_SCROLLED 대신 실제로 방출하는 이벤트를 실측하기 위해
 * 수신 타입을 넓히면서, 타입별·패키지별 최소 간격으로 기록을 제한해 홍수·배터리
 * 소모를 막는다. 실측 결과를 보고 존속(정식 프록시 채택 시 별도 이슈/ADR) 또는
 * 제거를 결정한다.
 */
class AsEventProbe(private val minIntervalMs: Long = MIN_INTERVAL_MS) {

    private val lastAt = HashMap<String, Long>()

    /** 이 이벤트를 기록할지 판정한다. atMs는 단조 ms — 캡 창 계산에만 쓴다. */
    fun shouldRecord(atMs: Long, pkg: String, eventType: String): Boolean {
        val key = "$pkg|$eventType"
        val last = lastAt[key]
        if (last != null && atMs - last < minIntervalMs) return false
        lastAt[key] = atMs
        return true
    }

    companion object {
        /** 타입·패키지당 최대 5회/s — 플링 케이던스(~0.5–2s)와의 상관을 보기에 충분. */
        const val MIN_INTERVAL_MS: Long = 200
    }
}
