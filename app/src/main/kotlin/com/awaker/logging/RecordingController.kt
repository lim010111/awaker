package com.awaker.logging

import com.awaker.session.SessionEvent

/**
 * 세션 경계에 따라 로그 파일을 열고 닫고, 세션 활성(후보 앱 포그라운드) 중에만
 * 센서 샘플링을 돌리는 오케스트레이터 (이슈 03의 핵심, 순수 Kotlin).
 *
 * - 세션 시작 → 파일 열기(header) / 세션 종료 → 파일 닫기
 * - 활성 세션이 있을 때만 [SensorSource]가 돈다 — 유예(away)·화면 꺼짐·세션 밖
 *   에서는 리스너 등록 자체가 없다(배터리)
 * - 유예 중에도 파일은 열려 있어 screen/battery/foreground/session은 계속 기록
 *
 * 스레드성: 세션/포그라운드/화면/배터리 입력은 서비스 폴링 스레드에서, 센서
 * 콜백은 센서 스레드에서 들어온다. sink 구현이 스레드 안전하고, 활성 sink
 * 참조는 @Volatile로 공유한다.
 */
class RecordingController(
    private val time: TimeSource,
    private val sensors: SensorSource,
    private val sinkFactory: (sessionId: String, pkg: String) -> SessionLogSink,
    private val headerFor: (sessionId: String, pkg: String, wallMs: Long, elapsedNs: Long) -> String,
) : SensorSink {

    private class Open(val sessionId: String, val pkg: String, val sink: SessionLogSink)

    // 폴링 스레드가 넣고 빼지만, 체크포인트 선택(메인 스레드)도 조회한다.
    private val open = java.util.concurrent.ConcurrentHashMap<String, Open>()

    @Volatile
    private var active: Open? = null
    private var sensorsRunning = false
    private var lastForegroundLogged: String? = null

    // 공통 타임라인 앵커 — 벽시계 소스 이벤트를 elapsedNs로 변환.
    private val anchorWallMs = time.wallMs()
    private val anchorElapsedNs = time.elapsedNs()

    fun wallToNs(wallMs: Long): Long = anchorElapsedNs + (wallMs - anchorWallMs) * 1_000_000

    val hasOpenSinks: Boolean
        get() = open.isNotEmpty()

    /** [SessionTracker]가 내보낸 세션 경계 이벤트를 반영한다. */
    fun onSessionEvents(events: List<SessionEvent>) {
        for (event in events) when (event) {
            is SessionEvent.Started -> {
                val sink = sinkFactory(event.sessionId, event.packageName)
                sink.writeLine(headerFor(event.sessionId, event.packageName, time.wallMs(), time.elapsedNs()))
                sink.writeLine(
                    LogSchema.session(wallToNs(event.at), "start", event.sessionId, event.packageName),
                )
                open[event.sessionId] = Open(event.sessionId, event.packageName, sink)
            }
            is SessionEvent.Resumed ->
                open[event.sessionId]?.sink?.writeLine(
                    LogSchema.session(
                        wallToNs(event.at), "resume", event.sessionId, event.packageName,
                        awayMs = event.awayMs,
                    ),
                )
            is SessionEvent.Ended -> {
                open.remove(event.sessionId)?.let { record ->
                    record.sink.writeLine(
                        LogSchema.session(
                            wallToNs(event.decidedAt), "end", event.sessionId, event.packageName,
                            reason = event.reason.name, endedAtWallMs = event.endedAt,
                        ),
                    )
                    record.sink.close()
                    if (active === record) active = null
                }
            }
        }
        syncSensors()
    }

    /**
     * 폴링 관측 후 호출 — 현재 포그라운드 앱(raw)과, 그것이 후보 앱이면 해당
     * 세션 id. 활성 세션 전환과 foreground 라인 기록을 담당한다.
     */
    fun onForeground(pkg: String?, activeSessionId: String?, wallMs: Long) {
        active = activeSessionId?.let { open[it] }
        if (pkg != lastForegroundLogged) {
            lastForegroundLogged = pkg
            writeAll(LogSchema.foreground(wallToNs(wallMs), pkg))
        }
        syncSensors()
    }

    fun onScreen(on: Boolean, wallMs: Long) = writeAll(LogSchema.screen(wallToNs(wallMs), on))

    /** AS 스크롤 raw — 활성 세션 파일에만 기록 (이슈 04). */
    fun onScroll(tNs: Long, pkg: String, dx: Int, dy: Int) {
        active?.sink?.writeLine(LogSchema.scroll(tNs, pkg, dx, dy))
    }

    /** teacher 룰 전이 — 활성 세션 파일에만 기록 (이슈 04). */
    fun onRule(tNs: Long, state: String, metrics: com.awaker.detection.TeacherRule.Metrics, reason: String?) {
        active?.sink?.writeLine(
            LogSchema.rule(tNs, state, metrics.flings, metrics.spanMs, metrics.medianGapMs, metrics.maxGapMs, reason),
        )
    }

    /** 체크포인트 표시/선택 — 해당 세션 파일에 기록 (이슈 05). */
    fun onCheckpoint(
        sessionId: String,
        wallMs: Long,
        event: String,
        ordinal: Int,
        heightPct: Int,
        choice: String? = null,
    ) {
        open[sessionId]?.sink?.writeLine(
            LogSchema.checkpoint(wallToNs(wallMs), event, ordinal, heightPct, choice),
        )
    }

    /** 북극성 N1 판정 — away 유예 중에도 파일이 열려 있어 기록 가능 (이슈 05). */
    fun onN1(sessionId: String, wallMs: Long, shownAtElapsedMs: Long, left: Boolean) {
        open[sessionId]?.sink?.writeLine(
            LogSchema.n1(wallToNs(wallMs), shownAtElapsedMs * 1_000_000, left),
        )
    }

    /** 자발 종료 검증 결과 (이슈 06). */
    fun onExitVerify(sessionId: String, wallMs: Long, verified: Boolean) {
        open[sessionId]?.sink?.writeLine(LogSchema.exitVerify(wallToNs(wallMs), verified))
    }

    /** 환기 사운드 시작/정지 (이슈 06) — 파일이 이미 닫혔으면 조용히 무시. */
    fun onSound(sessionId: String, wallMs: Long, event: String, reason: String? = null) {
        open[sessionId]?.sink?.writeLine(LogSchema.sound(wallToNs(wallMs), event, reason))
    }

    fun onBattery(pct: Int, charging: Boolean, wallMs: Long) =
        writeAll(LogSchema.battery(wallToNs(wallMs), pct, charging))

    /** 서비스 정지 등으로 모든 기록을 정리한다 (세션 end 라인은 tracker 이벤트가 담당). */
    fun closeAll() {
        active = null
        syncSensors()
        for (record in open.values) record.sink.close()
        open.clear()
    }

    // ---- SensorSink (센서 스레드에서 호출) ----

    override fun onImu(type: String, tNs: Long, x: Float, y: Float, z: Float) {
        active?.sink?.writeLine(LogSchema.imu(type, tNs, x, y, z))
    }

    override fun onLight(tNs: Long, lux: Float) {
        active?.sink?.writeLine(LogSchema.light(tNs, lux))
    }

    private fun writeAll(line: String) {
        for (record in open.values) record.sink.writeLine(line)
    }

    private fun syncSensors() {
        val shouldRun = active != null
        if (shouldRun && !sensorsRunning) {
            sensors.start(this)
            sensorsRunning = true
        } else if (!shouldRun && sensorsRunning) {
            sensors.stop()
            sensorsRunning = false
        }
    }
}
