package com.awaker.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val grace = 5 * 60_000L

    private fun tracker(): SessionTracker {
        var next = 0
        return SessionTracker(
            candidatePackages = setOf(youtube, instagram),
            awayGraceMs = grace,
            newSessionId = { "s${next++}" },
        )
    }

    @Test
    fun `candidate foreground starts a session`() {
        val t = tracker()
        val events = t.onForeground(youtube, at = 1_000)
        assertEquals(listOf<SessionEvent>(SessionEvent.Started("s0", youtube, 1_000)), events)
        assertEquals(youtube, t.activeCandidate)
    }

    @Test
    fun `non-candidate app never starts a session`() {
        val t = tracker()
        assertTrue(t.onForeground("com.android.chrome", at = 1_000).isEmpty())
        assertTrue(t.onTick(at = 1_000 + grace * 2).isEmpty())
    }

    @Test
    fun `staying in the same app emits nothing`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        assertTrue(t.onForeground(youtube, at = 5_000).isEmpty())
    }

    @Test
    fun `short leave and return keeps the same session`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        assertTrue(t.onForeground("launcher", at = 60_000).isEmpty())
        val events = t.onForeground(youtube, at = 60_000 + grace - 1)
        assertEquals(
            listOf<SessionEvent>(SessionEvent.Resumed("s0", youtube, 60_000 + grace - 1, awayMs = grace - 1)),
            events,
        )
    }

    @Test
    fun `leaving for the grace period ends the session at the leave timestamp`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        t.onForeground(null, at = 100_000)
        val decidedAt = 100_000 + grace
        val events = t.onTick(at = decidedAt)
        assertEquals(
            listOf<SessionEvent>(
                SessionEvent.Ended("s0", youtube, endedAt = 100_000, decidedAt = decidedAt, reason = EndReason.AWAY_TIMEOUT),
            ),
            events,
        )
    }

    @Test
    fun `return after expiry starts a new session`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        t.onForeground(null, at = 100_000)
        val returnAt = 100_000 + grace + 1
        val events = t.onForeground(youtube, at = returnAt)
        assertEquals(
            listOf(
                SessionEvent.Ended("s0", youtube, endedAt = 100_000, decidedAt = returnAt, reason = EndReason.AWAY_TIMEOUT),
                SessionEvent.Started("s1", youtube, returnAt),
            ),
            events,
        )
    }

    @Test
    fun `switching between candidates runs independent per-app sessions`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        val switch = t.onForeground(instagram, at = 10_000)
        assertEquals(listOf<SessionEvent>(SessionEvent.Started("s1", instagram, 10_000)), switch)

        // 유튜브 유예가 열려 있는 동안 복귀 — 같은 세션.
        val back = t.onForeground(youtube, at = 10_000 + grace - 1)
        assertEquals(
            listOf<SessionEvent>(SessionEvent.Resumed("s0", youtube, 10_000 + grace - 1, awayMs = grace - 1)),
            back,
        )

        // 인스타그램은 유예 만료로 종료.
        val expiry = t.onTick(at = 10_000 + grace - 1 + grace)
        assertEquals(1, expiry.size)
        val ended = expiry.single() as SessionEvent.Ended
        assertEquals("s1", ended.sessionId)
        assertEquals(10_000 + grace - 1, ended.endedAt)
    }

    @Test
    fun `screen off reported as null starts the away clock`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        t.onForeground(null, at = 30_000)
        val events = t.onForeground(null, at = 30_000 + grace)
        assertEquals(1, events.size)
        assertEquals(EndReason.AWAY_TIMEOUT, (events.single() as SessionEvent.Ended).reason)
    }

    @Test
    fun `endNow closes immediately with the given reason`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        val ended = t.endNow(youtube, at = 42_000, reason = EndReason.VOLUNTARY_EXIT)
        assertEquals(EndReason.VOLUNTARY_EXIT, ended?.reason)
        assertEquals(42_000L, ended?.endedAt)
        // 포그라운드를 한 번 떠난 뒤의 재진입은 새 세션 (자발 종료 억제 — 이슈 06).
        t.onForeground(null, at = 45_000)
        val events = t.onForeground(youtube, at = 50_000)
        assertEquals(listOf<SessionEvent>(SessionEvent.Started("s1", youtube, 50_000)), events)
    }

    @Test
    fun `voluntary exit suppresses restart until the app leaves foreground once`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        t.endNow(youtube, at = 10_000, reason = EndReason.VOLUNTARY_EXIT)
        // face-down 중에도 앱은 여전히 포그라운드 — 새 세션이 열리면 안 된다
        assertTrue(t.onForeground(youtube, at = 15_000).isEmpty())
        assertTrue(t.onForeground(youtube, at = 20_000).isEmpty())
        // 포그라운드를 한 번 떠나면 억제 해제 — 다음 진입은 새 세션
        t.onForeground(null, at = 25_000)
        assertEquals(
            listOf<SessionEvent>(SessionEvent.Started("s1", youtube, 30_000)),
            t.onForeground(youtube, at = 30_000),
        )
    }

    @Test
    fun `endAll closes every live session including ones in grace`() {
        val t = tracker()
        t.onForeground(youtube, at = 0)
        t.onForeground(instagram, at = 10_000)
        val events = t.endAll(at = 20_000, reason = EndReason.TRACKER_STOPPED)
        assertEquals(2, events.size)
        assertTrue(events.all { (it as SessionEvent.Ended).reason == EndReason.TRACKER_STOPPED })
    }
}
