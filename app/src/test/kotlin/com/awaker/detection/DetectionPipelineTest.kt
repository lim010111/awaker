package com.awaker.detection

import com.awaker.logging.LogSchema
import com.awaker.logging.RecordingController
import com.awaker.logging.SensorSink
import com.awaker.logging.SensorSource
import com.awaker.logging.SessionLogSink
import com.awaker.logging.TimeSource
import com.awaker.session.SessionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 룰 리셋 경계 (이슈 10, ADR-0014) — 활성 세션이 바뀌면(후보→후보 직행, 이탈,
 * 복귀 재진입) 룰 윈도우가 무음으로 비워지는지 검증한다.
 */
class DetectionPipelineTest {

    private class FakeSink : SessionLogSink {
        val lines = mutableListOf<String>()
        override fun writeLine(line: String) { lines.add(line) }
        override fun close() = Unit
    }

    private class FakeSensors : SensorSource {
        override fun start(sink: SensorSink) = Unit
        override fun stop() = Unit
    }

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"
    private val sinks = mutableMapOf<String, FakeSink>()
    private val recording = RecordingController(
        time = object : TimeSource {
            override fun wallMs() = 1_000_000L
            override fun elapsedNs() = 0L
        },
        sensors = FakeSensors(),
        sinkFactory = { sessionId, _ -> FakeSink().also { sinks[sessionId] = it } },
        headerFor = { sessionId, pkg, wallMs, elapsedNs ->
            LogSchema.header(sessionId, pkg, wallMs, elapsedNs, "test", 35, "test")
        },
    )
    private val pipeline = DetectionPipeline(TeacherRule(), recording)

    private fun startSession(id: String, pkg: String, atMs: Long) {
        recording.onSessionEvents(listOf(SessionEvent.Started(id, pkg, atMs)))
        recording.onForeground(pkg, id, atMs)
        pipeline.onTick(atMs, id)
    }

    /** 4초 간격 플링 9회 — minFlings·minSpanMs를 채워 양성 진입. */
    private fun driveToPositive(pkg: String, fromMs: Long): Long {
        var at = fromMs
        repeat(9) {
            pipeline.onScroll(at * 1_000_000, pkg, 0, -100)
            at += 4_000
        }
        assertTrue(pipeline.isPositive)
        return at - 4_000 // 마지막 플링 시각
    }

    private fun ruleLines(sessionId: String) =
        sinks.getValue(sessionId).lines.filter { it.contains("\"type\":\"rule\"") }

    @Test
    fun `direct candidate switch resets the rule silently`() {
        startSession("s0", youtube, atMs = 0)
        val last = driveToPositive(youtube, fromMs = 5_000)
        assertTrue(ruleLines("s0").any { it.contains("\"state\":\"enter\"") })

        // 후보→후보 직행 전환 — 같은 tick에 새 세션 시작 + 활성 전환
        startSession("s1", instagram, atMs = last + 3_000)

        assertFalse(pipeline.isPositive)
        // 리셋은 무음 — 어느 파일에도 exit 라인이 없다 (replay가 foreground로 경계 유도)
        assertTrue(ruleLines("s0").none { it.contains("\"state\":\"exit\"") })
        assertTrue(ruleLines("s1").isEmpty())
    }

    @Test
    fun `leaving all candidates resets the rule silently`() {
        startSession("s0", youtube, atMs = 0)
        val last = driveToPositive(youtube, fromMs = 5_000)

        recording.onForeground("com.android.launcher", null, last + 3_000)
        pipeline.onTick(last + 3_000, null)

        assertFalse(pipeline.isPositive)
        assertTrue(ruleLines("s0").none { it.contains("\"state\":\"exit\"") })
    }

    @Test
    fun `resume starts accumulation fresh instead of inheriting the old window`() {
        startSession("s0", youtube, atMs = 0)
        val last = driveToPositive(youtube, fromMs = 5_000) // 5s..37s 플링 9회

        // 런처 경유 이탈 → 유예 안 복귀 (같은 세션 id)
        pipeline.onTick(last + 3_000, null)
        recording.onForeground(youtube, "s0", last + 7_000)
        pipeline.onTick(last + 7_000, "s0")
        assertFalse(pipeline.isPositive)

        // 복귀 후 플링 4회 — 이전 윈도우가 남아 있었다면 양성이 됐을 밀도
        var at = last + 8_000
        repeat(4) {
            pipeline.onScroll(at * 1_000_000, youtube, 0, -100)
            at += 4_000
        }
        assertFalse(pipeline.isPositive)
    }

    @Test
    fun `unchanged active session keeps rule state across ticks`() {
        startSession("s0", youtube, atMs = 0)
        val last = driveToPositive(youtube, fromMs = 5_000)

        pipeline.onTick(last + 5_000, "s0")
        pipeline.onTick(last + 10_000, "s0")
        assertTrue(pipeline.isPositive)
    }
}
