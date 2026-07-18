package com.awaker.exit

import com.awaker.exit.ExitVerifier.Event
import com.awaker.exit.ExitVerifier.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitVerifierTest {

    private val faceDown = -9.5f
    private val faceUp = 9.5f
    private val sideways = 0.5f

    private fun verifier() = ExitVerifier().also { it.begin(0) }

    /** fromMs부터 stepMs 간격으로 z를 공급하고 마지막 이벤트를 반환. */
    private fun feed(v: ExitVerifier, fromMs: Long, toMs: Long, z: Float, stepMs: Long = 100): Event? {
        var last: Event? = null
        var t = fromMs
        while (t <= toMs) {
            v.onSample(t, z)?.let { last = it }
            t += stepMs
        }
        return last
    }

    @Test
    fun `sustained face-down verifies`() {
        val v = verifier()
        assertNull(feed(v, 0, 1_900, faceDown)) // 2초 유지 전엔 침묵
        assertEquals(Event.Verified, v.onSample(2_000, faceDown))
        assertEquals(Phase.PLAYING, v.phase)
    }

    @Test
    fun `brief face-down does not verify and hold restarts`() {
        val v = verifier()
        feed(v, 0, 1_000, faceDown)
        v.onSample(1_100, sideways) // 뒤집다 말았다 — hold 리셋
        assertNull(feed(v, 1_200, 3_000, faceDown))
        assertEquals(Event.Verified, v.onSample(3_200 + 100, faceDown))
    }

    @Test
    fun `no face-down within timeout fails and session is untouched`() {
        val v = verifier()
        val last = feed(v, 0, 30_100, sideways)
        assertEquals(Event.Failed, last)
        assertEquals(Phase.DONE, v.phase)
        // 이후 샘플은 무시된다
        assertNull(v.onSample(31_000, faceDown))
    }

    @Test
    fun `sound stops on 15 minute timer`() {
        val v = verifier()
        feed(v, 0, 2_000, faceDown) // verified at 2s
        assertEquals(Phase.PLAYING, v.phase)
        assertNull(v.onSample(2_000 + 15 * 60_000 - 1, faceDown))
        assertEquals(Event.StopSound("timer"), v.onSample(2_000 + 15 * 60_000, faceDown))
        assertEquals(Phase.DONE, v.phase)
    }

    @Test
    fun `sound stops when face-up held 30s before the timer`() {
        val v = verifier()
        feed(v, 0, 2_000, faceDown)
        assertNull(feed(v, 10_000, 39_900, faceUp))
        assertEquals(Event.StopSound("face_up"), v.onSample(40_000, faceUp))
    }

    @Test
    fun `brief face-up does not stop the sound`() {
        val v = verifier()
        feed(v, 0, 2_000, faceDown)
        feed(v, 10_000, 20_000, faceUp) // 10초 face-up — 부족
        v.onSample(20_100, faceDown) // 다시 뒤집음 — hold 리셋
        assertNull(feed(v, 20_200, 45_000, faceDown))
        assertEquals(Phase.PLAYING, v.phase)
    }

    @Test
    fun `verified fires exactly once`() {
        val v = verifier()
        feed(v, 0, 2_000, faceDown)
        assertNull(feed(v, 2_100, 5_000, faceDown))
    }
}
