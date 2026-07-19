package com.awaker.checkpoint

import com.awaker.session.EndReason
import com.awaker.session.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAimTest {

    private val aim = SessionAim()

    private fun started(id: String, pkg: String = "com.google.android.youtube", at: Long = 1_000L) =
        SessionEvent.Started(id, pkg, at)

    private fun resumed(id: String, pkg: String = "com.google.android.youtube", at: Long = 9_000L) =
        SessionEvent.Resumed(id, pkg, at, awayMs = 3_000L)

    private fun ended(id: String, pkg: String = "com.google.android.youtube") =
        SessionEvent.Ended(id, pkg, endedAt = 20_000L, decidedAt = 320_000L, reason = EndReason.AWAY_TIMEOUT)

    @Test
    fun `started aims at the new session`() {
        aim.onSessionEvents(listOf(started("s0", at = 1_000L)))
        assertEquals("s0", aim.current?.id)
        assertEquals(1_000L, aim.current?.startWallMs)
    }

    @Test
    fun `resumed re-aims and preserves the original start`() {
        // s0 시작 → s1로 직행 전환(새 세션) → 유예 안에 s0 복귀
        aim.onSessionEvents(listOf(started("s0", at = 1_000L)))
        aim.onSessionEvents(listOf(started("s1", pkg = "com.instagram.android", at = 5_000L)))
        assertEquals("s1", aim.current?.id)

        aim.onSessionEvents(listOf(resumed("s0", at = 9_000L)))
        assertEquals("s0", aim.current?.id)
        // "N분째" 메시지 기준 — 원래 세션 시작 시각이 남아야 한다
        assertEquals(1_000L, aim.current?.startWallMs)
    }

    @Test
    fun `ended clears the aim only when it points at the ended session`() {
        aim.onSessionEvents(listOf(started("s0", at = 1_000L)))
        aim.onSessionEvents(listOf(started("s1", pkg = "com.instagram.android", at = 5_000L)))

        // 유예 만료로 s0가 끝나도 겨냥(s1)은 그대로
        aim.onSessionEvents(listOf(ended("s0")))
        assertEquals("s1", aim.current?.id)

        aim.onSessionEvents(listOf(ended("s1", pkg = "com.instagram.android")))
        assertNull(aim.current)
    }

    @Test
    fun `ended session cannot be re-aimed by a late resume`() {
        aim.onSessionEvents(listOf(started("s0", at = 1_000L)))
        aim.onSessionEvents(listOf(ended("s0")))
        aim.onSessionEvents(listOf(resumed("s0")))
        assertNull(aim.current)
    }
}
