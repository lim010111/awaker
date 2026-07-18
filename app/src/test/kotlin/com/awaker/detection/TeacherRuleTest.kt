package com.awaker.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherRuleTest {

    private fun rule() = TeacherRule()

    /** t0부터 gapMs 간격 fling을 count개 흘려보낸다. 마지막 전이를 반환. */
    private fun feed(rule: TeacherRule, t0: Long, gapMs: Long, count: Int): TeacherRule.Transition? {
        var last: TeacherRule.Transition? = null
        for (i in 0 until count) rule.onScroll(t0 + i * gapMs)?.let { last = it }
        return last
    }

    @Test
    fun `reflexive 2s cadence enters positive`() {
        val r = rule()
        // 2초 간격 × 20 = span 38s, median 2s, pause 없음 → 진입
        val transition = feed(r, t0 = 0, gapMs = 2_000, count = 20)
        assertTrue(transition is TeacherRule.Transition.Enter)
        assertTrue(r.isPositive)
        val metrics = (transition as TeacherRule.Transition.Enter).metrics
        assertEquals(2_000, metrics.medianGapMs)
    }

    @Test
    fun `reading with long pauses never enters`() {
        val r = rule()
        // 읽다가 넘기는 패턴: 25초 간격 — median이 임계 초과
        assertNull(feed(r, t0 = 0, gapMs = 25_000, count = 10))
        assertFalse(r.isPositive)
    }

    @Test
    fun `mixed cadence with one meaningful pause does not enter`() {
        val r = rule()
        // 빠른 케이던스 후 25초 멈춤(읽기) 후 다시 빠른 케이던스: max gap이 진입 차단
        feed(r, t0 = 0, gapMs = 2_000, count = 8) // span 14s — 아직 span 미달
        feed(r, t0 = 14_000 + 25_000, gapMs = 2_000, count = 10)
        assertFalse(r.isPositive)
    }

    @Test
    fun `burst within debounce window is one fling`() {
        val r = rule()
        // 100ms 간격 연속 이벤트 30개 = fling 1개 취급 → 진입 불가
        for (i in 0 until 30) r.onScroll(i * 100L)
        assertFalse(r.isPositive)
    }

    @Test
    fun `short frenzy without span does not enter`() {
        val r = rule()
        // 1초 간격 × 12 = span 11s < 30s → 진입 불가 (순간 폭주 배제)
        assertNull(feed(r, t0 = 0, gapMs = 1_000, count = 12))
        assertFalse(r.isPositive)
    }

    @Test
    fun `silence after positive exits with reason`() {
        val r = rule()
        feed(r, t0 = 0, gapMs = 2_000, count = 20)
        assertTrue(r.isPositive)
        val exit = r.onTick(38_000 + 31_000)
        assertTrue(exit is TeacherRule.Transition.Exit)
        assertEquals("silence", (exit as TeacherRule.Transition.Exit).reason)
        assertFalse(r.isPositive)
    }

    @Test
    fun `cadence collapse exits while still scrolling occasionally`() {
        val r = rule()
        feed(r, t0 = 0, gapMs = 2_000, count = 20) // 진입, 마지막 fling 38s
        assertTrue(r.isPositive)
        // 이후 뜸한 스크롤 — 침묵 임계는 안 넘기며 윈도우 fling 수가 4 미만으로 붕괴
        var exit: TeacherRule.Transition? = null
        for (t in longArrayOf(62_000, 86_000, 110_000)) r.onScroll(t)?.let { exit = it }
        assertTrue(exit is TeacherRule.Transition.Exit)
        assertEquals("cadence_collapse", (exit as TeacherRule.Transition.Exit).reason)
    }

    @Test
    fun `re-entry works after exit`() {
        val r = rule()
        feed(r, t0 = 0, gapMs = 2_000, count = 20)
        r.onTick(38_000 + 31_000) // silence exit
        assertFalse(r.isPositive)
        val again = feed(r, t0 = 100_000, gapMs = 2_000, count = 20)
        assertTrue(again is TeacherRule.Transition.Enter)
    }

    @Test
    fun `reset returns exit only when positive`() {
        val r = rule()
        assertNull(r.reset(1_000))
        feed(r, t0 = 0, gapMs = 2_000, count = 20)
        val exit = r.reset(50_000)
        assertTrue(exit is TeacherRule.Transition.Exit)
        assertEquals("reset", (exit as TeacherRule.Transition.Exit).reason)
        assertFalse(r.isPositive)
    }
}
