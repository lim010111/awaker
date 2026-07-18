package com.awaker.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/**
 * UsageStatsManager 이벤트를 증분 폴링해 "지금 포그라운드로 보이는 패키지"를
 * 유지한다 (ADR-0004 — AccessibilityService 없이 포그라운드 관측).
 */
class ForegroundAppSource(private val usageStatsManager: UsageStatsManager) {

    private var lastQueryEnd = 0L
    private var lastForeground: String? = null

    fun currentForeground(now: Long): String? {
        // 첫 폴링은 직전 1분을 훑어 현재 상태를 복원하고, 이후엔 겹침 1초의 증분 조회.
        val begin = if (lastQueryEnd == 0L) now - 60_000 else lastQueryEnd - 1_000
        val events = usageStatsManager.queryEvents(begin, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastForeground = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED ->
                    if (event.packageName == lastForeground) lastForeground = null
            }
        }
        lastQueryEnd = now
        return lastForeground
    }
}
