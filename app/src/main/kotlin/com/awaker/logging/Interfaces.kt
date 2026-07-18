package com.awaker.logging

/** 열린 세션 로그 파일 하나. 구현은 스레드 안전해야 한다. */
interface SessionLogSink {
    fun writeLine(line: String)
    fun close()
}

/** IMU/조도 샘플 공급원 — 실제 구현은 [SensorCapture], 테스트는 페이크. */
interface SensorSource {
    fun start(sink: SensorSink)
    fun stop()
}

/** [SensorSource]가 샘플을 흘려보내는 곳. tNs는 elapsedRealtimeNanos 클럭. */
interface SensorSink {
    fun onImu(type: String, tNs: Long, x: Float, y: Float, z: Float)
    fun onLight(tNs: Long, lux: Float)
}

/** 벽시계/단조 시계 쌍 — 공통 타임라인 앵커용 (테스트에서 페이크). */
interface TimeSource {
    fun wallMs(): Long
    fun elapsedNs(): Long
}
