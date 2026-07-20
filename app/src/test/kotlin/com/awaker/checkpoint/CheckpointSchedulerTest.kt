package com.awaker.checkpoint

import com.awaker.checkpoint.CheckpointScheduler.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointSchedulerTest {

    private val dwell = 90_000L
    private val extension = 60_000L
    private val s0 = "session-0"
    private val s1 = "session-1"

    private fun scheduler() = CheckpointScheduler()

    /** rule 양성 유지 상태로 dwell을 채워 첫 표시까지 진행시킨다. */
    private fun driveToShow(s: CheckpointScheduler, from: Long = 0L, sessionId: String = s0): Effect.Show {
        assertTrue(s.onTick(from, sessionId, rulePositive = true).filterIsInstance<Effect.Show>().isEmpty())
        val effects = s.onTick(from + dwell, sessionId, rulePositive = true)
        return effects.filterIsInstance<Effect.Show>().single()
    }

    @Test
    fun `dwell must be sustained before show`() {
        val s = scheduler()
        assertTrue(s.onTick(0, s0, rulePositive = true).isEmpty())
        assertTrue(s.onTick(50_000, s0, rulePositive = true).isEmpty())
        val show = s.onTick(dwell, s0, rulePositive = true).single() as Effect.Show
        assertEquals(0, show.ordinal)
        assertEquals(0.25f, show.heightFraction)
    }

    @Test
    fun `rule exit during arming cancels the dwell`() {
        val s = scheduler()
        s.onTick(0, s0, rulePositive = true)
        s.onTick(60_000, s0, rulePositive = false) // 룰 해제 → 무장 해제
        s.onTick(70_000, s0, rulePositive = true) // 다시 양성 — dwell 재시작
        assertTrue(s.onTick(70_000 + dwell - 1, s0, rulePositive = true).isEmpty())
        assertTrue(
            s.onTick(70_000 + dwell, s0, rulePositive = true).single() is Effect.Show,
        )
    }

    @Test
    fun `extend snoozes then reshows taller while rule stays positive`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)

        // 연장 창이 열려 있는 동안엔 재표시 없음 (N1 판정은 독립적으로 나올 수 있음)
        assertTrue(
            s.onTick(dwell + 10_000 + extension - 1, s0, true)
                .filterIsInstance<Effect.Show>().isEmpty(),
        )

        val effects = s.onTick(dwell + 10_000 + extension, s0, true)
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
        val effects = s.onTick(dwell + 10_000 + extension, s0, rulePositive = false)
        assertTrue(effects.filterIsInstance<Effect.Show>().isEmpty())
        // 이후 다시 양성이 돼도 dwell을 새로 채워야 한다
        assertTrue(s.onTick(300_000, s0, true).isEmpty())
        assertTrue(s.onTick(300_000 + dwell, s0, true).single() is Effect.Show)
    }

    @Test
    fun `session end dismisses resets counter and height`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)
        s.onTick(dwell + 10_000 + extension, s0, true) // ordinal 1 표시 중

        val effects = s.onTick(dwell + 200_000, activeSessionId = null, rulePositive = false)
        assertTrue(effects.any { it is Effect.Dismiss })

        // 새 세션 — 카운터·가림 리셋 확인 (AC)
        val show = driveToShow(s, from = 500_000)
        assertEquals(0, show.ordinal)
        assertEquals(0.25f, show.heightFraction)
    }

    @Test
    fun `direct candidate switch dismisses sheet and resets occlusion`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)
        s.onTick(dwell + 10_000 + extension, s0, true) // ordinal 1 표시 중

        // 후보→후보 직행 전환 — 세션은 계속 활성이지만 시트는 내려간다 (ADR-0014)
        val effects = s.onTick(dwell + 170_000, s1, rulePositive = false)
        assertTrue(effects.any { it is Effect.Dismiss })

        // 새 활성 세션에서 가림은 처음부터
        val show = driveToShow(s, from = 500_000, sessionId = s1)
        assertEquals(0, show.ordinal)
        assertEquals(0.25f, show.heightFraction)
    }

    @Test
    fun `arming restarts across a candidate switch`() {
        val s = scheduler()
        s.onTick(0, s0, rulePositive = true)
        // dwell 도중 전환 — 무장은 새 세션에서 처음부터 다시 채워야 한다
        s.onTick(60_000, s1, rulePositive = true)
        assertTrue(s.onTick(60_000 + dwell - 1, s1, rulePositive = true).isEmpty())
        assertTrue(s.onTick(60_000 + dwell, s1, rulePositive = true).single() is Effect.Show)
    }

    @Test
    fun `launcher round trip resets occlusion for the resumed session`() {
        val s = scheduler()
        driveToShow(s)
        s.onExtend(dwell + 10_000)
        s.onTick(dwell + 10_000 + extension, s0, true) // ordinal 1 표시 중

        s.onTick(dwell + 170_000, activeSessionId = null, rulePositive = false) // 런처로 이탈
        // 유예 안 복귀(Resumed) — 같은 세션 id지만 상태는 새로 시작한다 (ADR-0014)
        val show = driveToShow(s, from = dwell + 200_000)
        assertEquals(0, show.ordinal)
        assertEquals(0.25f, show.heightFraction)
    }

    @Test
    fun `n1 left true when session leaves within a minute of show`() {
        val s = scheduler()
        val show = driveToShow(s)
        val effects = s.onTick(show.atMs + 30_000, activeSessionId = null, rulePositive = false)
        val n1 = effects.filterIsInstance<Effect.RecordN1>().single()
        assertTrue(n1.left)
        assertEquals(show.atMs, n1.shownAtMs)
    }

    @Test
    fun `n1 left false when session survives the minute`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onExtend(show.atMs + 10_000) // 연장해도 N1 판정은 독립
        val effects = s.onTick(show.atMs + 60_000, s0, rulePositive = true)
        val n1 = effects.filterIsInstance<Effect.RecordN1>().single()
        assertTrue(!n1.left)
    }

    @Test
    fun `n1 fires once per show`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onTick(show.atMs + 60_000, s0, true)
        val again = s.onTick(show.atMs + 120_000, s0, true)
        assertTrue(again.filterIsInstance<Effect.RecordN1>().isEmpty())
    }

    @Test
    fun `pending n1 survives a candidate switch and judges the full window`() {
        val s = scheduler()
        val show = driveToShow(s)
        // 후보→후보 전환은 이탈이 아니다 — 판정은 리셋을 가로질러 계속 (ADR-0014 예외)
        val atSwitch = s.onTick(show.atMs + 30_000, s1, rulePositive = false)
        assertTrue(atSwitch.filterIsInstance<Effect.RecordN1>().isEmpty())

        val effects = s.onTick(show.atMs + 60_000, s1, rulePositive = false)
        val n1 = effects.filterIsInstance<Effect.RecordN1>().single()
        assertTrue(!n1.left)
        assertEquals(show.atMs, n1.shownAtMs)
    }

    @Test
    fun `pending n1 records left when all candidates are abandoned after a switch`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onTick(show.atMs + 20_000, s1, rulePositive = false) // 후보→후보 — 판정 유지
        val effects = s.onTick(show.atMs + 40_000, activeSessionId = null, rulePositive = false)
        val n1 = effects.filterIsInstance<Effect.RecordN1>().single()
        assertTrue(n1.left)
    }

    @Test
    fun `exit choice snoozes so verification loop is not interrupted by a reshow`() {
        val s = scheduler()
        val show = driveToShow(s)
        s.onExitChosen(show.atMs + 5_000)
        assertTrue(s.onTick(show.atMs + 6_000, s0, true).isEmpty())
        // 검증 유예(연장과 동일 창) 뒤 여전히 양성이면 재표시 — ordinal은 그대로
        val effects = s.onTick(show.atMs + 5_000 + extension, s0, true)
        val reshow = effects.filterIsInstance<Effect.Show>().single()
        assertEquals(0, reshow.ordinal)
    }
}
