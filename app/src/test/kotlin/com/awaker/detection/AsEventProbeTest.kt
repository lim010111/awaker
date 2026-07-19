package com.awaker.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsEventProbeTest {

    private val probe = AsEventProbe(minIntervalMs = 200)
    private val youtube = "com.google.android.youtube"
    private val contentChanged = "TYPE_WINDOW_CONTENT_CHANGED"

    @Test
    fun `first event always records`() {
        assertTrue(probe.shouldRecord(1_000, youtube, contentChanged))
    }

    @Test
    fun `events inside the cap window are suppressed then allowed again`() {
        assertTrue(probe.shouldRecord(1_000, youtube, contentChanged))
        assertFalse(probe.shouldRecord(1_100, youtube, contentChanged))
        assertFalse(probe.shouldRecord(1_199, youtube, contentChanged))
        assertTrue(probe.shouldRecord(1_200, youtube, contentChanged))
    }

    @Test
    fun `cap is independent per event type`() {
        assertTrue(probe.shouldRecord(1_000, youtube, contentChanged))
        assertTrue(probe.shouldRecord(1_050, youtube, "TYPE_WINDOW_STATE_CHANGED"))
    }

    @Test
    fun `cap is independent per package`() {
        assertTrue(probe.shouldRecord(1_000, youtube, contentChanged))
        assertTrue(probe.shouldRecord(1_050, "com.instagram.android", contentChanged))
    }

    @Test
    fun `suppressed event does not push the cap window forward`() {
        assertTrue(probe.shouldRecord(1_000, youtube, contentChanged))
        assertFalse(probe.shouldRecord(1_150, youtube, contentChanged))
        // 캡 창은 마지막 '기록된' 이벤트 기준 — 1_000 + 200 = 1_200부터 허용
        assertTrue(probe.shouldRecord(1_200, youtube, contentChanged))
    }
}
