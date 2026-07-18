package com.awaker.logging

import com.awaker.session.EndReason
import com.awaker.session.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingControllerTest {

    private class FakeSink : SessionLogSink {
        val lines = mutableListOf<String>()
        var closed = false
        override fun writeLine(line: String) { lines.add(line) }
        override fun close() { closed = true }
    }

    private class FakeSensors : SensorSource {
        var running = false
        var sink: SensorSink? = null
        override fun start(sink: SensorSink) { running = true; this.sink = sink }
        override fun stop() { running = false; sink = null }
    }

    private class FakeTime : TimeSource {
        override fun wallMs() = 1_000_000L
        override fun elapsedNs() = 5_000_000_000L
    }

    private val youtube = "com.google.android.youtube"
    private val sinks = mutableMapOf<String, FakeSink>()
    private val sensors = FakeSensors()
    private val controller = RecordingController(
        time = FakeTime(),
        sensors = sensors,
        sinkFactory = { sessionId, _ -> FakeSink().also { sinks[sessionId] = it } },
        headerFor = { sessionId, pkg, wallMs, elapsedNs ->
            LogSchema.header(sessionId, pkg, wallMs, elapsedNs, "test", 35, "test")
        },
    )

    private fun start(id: String = "s0", at: Long = 1_000_000L) {
        controller.onSessionEvents(listOf(SessionEvent.Started(id, youtube, at)))
        controller.onForeground(youtube, id, at)
    }

    @Test
    fun `session start opens sink with header and start line then runs sensors`() {
        start()
        val sink = sinks.getValue("s0")
        assertTrue(sink.lines[0].contains("\"type\":\"header\""))
        assertTrue(sink.lines[1].contains("\"event\":\"start\""))
        assertTrue(sensors.running)
    }

    @Test
    fun `wall clock events are anchored onto the elapsed timeline`() {
        start(at = 1_000_000L) // 앵커와 같은 벽시계 → t = anchor elapsedNs
        val sink = sinks.getValue("s0")
        assertTrue(sink.lines[1].contains("\"t\":5000000000"))
    }

    @Test
    fun `leaving the candidate stops sensors but keeps the file open`() {
        start()
        controller.onForeground("com.android.launcher", null, 1_060_000L)
        assertFalse(sensors.running)
        assertFalse(sinks.getValue("s0").closed)
        // 이탈은 foreground 라인으로 남는다.
        assertTrue(sinks.getValue("s0").lines.last().contains("com.android.launcher"))
    }

    @Test
    fun `imu samples reach only the active session sink`() {
        start()
        sensors.sink!!.onImu(LogSchema.TYPE_GYRO, 10L, 0.1f, 0.2f, 0.3f)
        assertTrue(sinks.getValue("s0").lines.last().contains("\"type\":\"gyro\""))

        controller.onForeground(null, null, 1_060_000L)
        assertFalse(sensors.running)
        val countAfterStop = sinks.getValue("s0").lines.size

        // 정지 후 늦게 도착한 샘플은 버려진다.
        controller.onImu(LogSchema.TYPE_ACCEL, 11L, 1f, 1f, 1f)
        assertEquals(countAfterStop, sinks.getValue("s0").lines.size)
    }

    @Test
    fun `session end writes end line closes sink and stops sensors`() {
        start()
        controller.onSessionEvents(
            listOf(
                SessionEvent.Ended("s0", youtube, endedAt = 1_060_000L, decidedAt = 1_360_000L, reason = EndReason.AWAY_TIMEOUT),
            ),
        )
        val sink = sinks.getValue("s0")
        assertTrue(sink.closed)
        assertTrue(sink.lines.last().contains("\"event\":\"end\""))
        assertTrue(sink.lines.last().contains("AWAY_TIMEOUT"))
        assertFalse(sensors.running)
    }

    @Test
    fun `screen and battery lines fan out to every open sink`() {
        start(id = "s0")
        controller.onSessionEvents(listOf(SessionEvent.Started("s1", "com.instagram.android", 1_010_000L)))
        controller.onScreen(false, 1_020_000L)
        controller.onBattery(90, charging = true, wallMs = 1_030_000L)
        for (id in listOf("s0", "s1")) {
            val texts = sinks.getValue(id).lines.joinToString("\n")
            assertTrue(texts.contains("\"type\":\"screen\""))
            assertTrue(texts.contains("\"type\":\"battery\""))
        }
    }

    @Test
    fun `resume writes resume line with away duration`() {
        start()
        controller.onForeground(null, null, 1_060_000L)
        controller.onSessionEvents(
            listOf(SessionEvent.Resumed("s0", youtube, at = 1_120_000L, awayMs = 60_000L)),
        )
        controller.onForeground(youtube, "s0", 1_120_000L)
        val sink = sinks.getValue("s0")
        assertTrue(sink.lines.any { it.contains("\"event\":\"resume\"") && it.contains("\"awayMs\":60000") })
        assertTrue(sensors.running)
    }

    @Test
    fun `closeAll closes everything and stops sensors`() {
        start()
        controller.closeAll()
        assertTrue(sinks.getValue("s0").closed)
        assertFalse(sensors.running)
        assertFalse(controller.hasOpenSinks)
    }
}
