package com.awaker.checkpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointTemplatesTest {

    @Test
    fun `minutes are bucketed not raw`() {
        assertEquals("5분이 안 되게", CheckpointTemplates.minutesBucket(3))
        assertEquals("5분에서 10분쯤", CheckpointTemplates.minutesBucket(5))
        assertEquals("10분에서 30분쯤", CheckpointTemplates.minutesBucket(29))
        assertEquals("30분에서 1시간쯤", CheckpointTemplates.minutesBucket(59))
        assertEquals("1시간 넘게", CheckpointTemplates.minutesBucket(61))
    }

    @Test
    fun `slots are filled with measured values`() {
        val message = CheckpointTemplates.render(elapsedMinutes = 12, ordinal = 0)
        assertTrue(message.contains("10분에서 30분쯤"))
        assertTrue(message.contains("1번째"))
        assertFalse(message.contains("{bucket}"))
        assertFalse(message.contains("{nth}"))
    }

    @Test
    fun `pool rotates by ordinal and every template renders clean`() {
        val rendered = (0..7).map { CheckpointTemplates.render(45, it) }
        assertTrue(rendered.distinct().size >= 4) // 풀 순환
        rendered.forEach {
            assertFalse(it.contains("{"))
            assertFalse(it.contains("}"))
        }
    }
}
