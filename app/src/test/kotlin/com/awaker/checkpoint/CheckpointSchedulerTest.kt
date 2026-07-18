package com.awaker.checkpoint

import com.awaker.checkpoint.CheckpointScheduler.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointSchedulerTest {

    private val dwell = 90_000L
    private val extension = 60_000L

    private fun scheduler() = CheckpointScheduler()

    /** rule 양성 유지 상태로 dwell을 채워 첫 표시까지 진행시킨다. */
    private fun driveToShow(s: CheckpointScheduler, from: Long = 0L): Effect.Show {
        assertTrue(s.onTick(from, sessionActive = true, rulePositive = true).isEmpty())
        val effects = s.onTick(from + dwell, sessionActive = true, rulePositive = true)
        return effects.filterIsInstance<Effect.Show>().single()
    }

    @Test
    fun `dwell must be sustained before show`() {
        val s = scheduler()
        assertTrue(s.onTick(0, true, rulePositive = true).isEmpty())
        assertTrue(s.onTick(50_000, true, rulePositive = true).isEmpty())
        val show = s.onTick(dwell, true, rulePositive = true).single() as Effect.Show
        assertEquals(0, show.ordinal)
        assertEquals(0.25f, show.heightFraction)
    }

    @Test
    fun `rule exit during arming cancels the dwell`() {
        val s = scheduler()
        s.onTick(0, true, rulePositive = true)
        s.onTick(60_000, true, rulePositive = false) // 룰 해제 → 무장 해제
        s.onTick(70_000, true, rulePositive = true) // 다시 양성 — dwell 재시작
        assertTrue(s.onTick(70_000 + dwell - 1, true, rulePositive = true).isEmpty())
        assertTrue(
            s.onTick(70_000 + dwell, true, rulePositive = true).single() is Effect.Show,
        )
    }

    @Test
    fun `extend snoozes then reshows taller while rule stays positive`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)

        // 연장 창이 열려 있는 동안엔 재표시 없음 (N1 판정은 독립적으로 나올 수 있음)
        assertTrue(
            s.onTick(dwell + 10_000 + extension - 1, true, true)
                .filterIsInstance<Effect.Show>().isEmpty(),
        )

        val effects = s.onTick(dwell + 10_000 + extension, true, true)
        val show = effects.filterIsInstance<Effect.Show>().single()
        assertEquals(1, show.ordinal)
        assertEquals(0.40f, show.heightFraction)
    }

    @Test
    fun `height grows by step and caps at 70 percent`() {
        val s = scheduler()
        assertEquals(0.25f, s.heightFor(0))
        assertEquals(0.40f, s.heightFor(1))
        assertEquals(0.55f, s.heightFor(2))
        assertEquals(0.70f, s.heightFor(3))
        assertEquals(0.70f, s.heightFor(10)) // cap — 상단 30%는 항상 후보 앱
    }

    @Test
    fun `snooze without positive rule goes idle instead of reshowing`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)
        val effects = s.onTick(dwell + 10_000 + extension, true, rulePositive = false)
        assertTrue(effects.filterIsInstance<Effect.Show>().isEmpty())
        // 이후 다시 양성이 돼도 dwell을 새로 채워야 한다
        assertTrue(s.onTick(300_000, true, true).isEmpty())
        assertTrue(s.onTick(300_000 + dwell, true, true).single() is Effect.Show)
    }

    @Test
    fun `session end dismisses resets counter and height`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)
        s.onTick(dwell + 10_000 + extension, true, true) // ordinal 1 표시 중

        val effects = s.onTick(dwell + 200_000, sessionActive = false, rulePositive = false)
        assertTrue(effects.any { it is Effect.Dismiss })

        // 새 세션 — 카운터·가림 리셋 확인 (AC)
        val show = driveToShow(s, from = 500_000)
        assertEquals(0, show.ordinal)
        assertEquals(0.25f, show.heightFraction)
    }

    @Test
    fun `n1 left true when session leaves within a minute of show`() {
        val s = scheduler()
        val show = driveToShow(s)
        val effects = s.onTick(show.atMs + 30_000, sessionActive = false, rulePositive = false)
        val n1 = effects.filterIsInstance<Effect.RecordN1>().single()
        assertTrue(n1.left)
        assertEquals(show.atMs, n1.shownAtMs)
    }

    @Test
    fun `n1 left false when session survives the minute`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onExtend(show.atMs + 10_000) // 연장해도 N1 판정은 독립
        val effects = s.onTick(show.atMs + 60_000, sessionActive = true, rulePositive = true)
        val n1 = effects.filterIsInstance<Effect.RecordN1>().single()
        assertTrue(!n1.left)
    }

    @Test
    fun `n1 fires once per show`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onTick(show.atMs + 60_000, true, true)
        val again = s.onTick(show.atMs + 120_000, true, true)
        assertTrue(again.filterIsInstance<Effect.RecordN1>().isEmpty())
    }

    @Test
    fun `exit choice snoozes so verification loop is not interrupted by a reshow`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onExitChosen(show.atMs + 5_000)
        assertTrue(s.onTick(show.atMs + 6_000, true, true).isEmpty())
        // 검증 유예(연장과 동일 창) 뒤 여전히 양성이면 재표시 — ordinal은 그대로
        val effects = s.onTick(show.atMs + 5_000 + extension, true, true)
        val reshow = effects.filterIsInstance<Effect.Show>().single()
        assertEquals(0, reshow.ordinal)
    }
}
