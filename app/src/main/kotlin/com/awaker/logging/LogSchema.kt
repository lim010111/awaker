package com.awaker.logging

/**
 * 센서 로그 JSONL 레코드 빌더 — 스키마의 단일 구현. 문서: docs/log-schema.md,
 * 오프라인 집행자: replay 하네스(이슈 07).
 *
 * `ema_probe` 타입은 예약만 되어 있고 기록 주체가 없다(ADR-0010 — 베타 확장
 * 빌드의 순간-EMA 프로브 자리).
 */
object LogSchema {
    const val VERSION = 1

    fun header(
        sessionId: String,
        pkg: String,
        wallMs: Long,
        elapsedNs: Long,
        model: String,
        sdk: Int,
        app: String,
    ): String = buildString {
        append("{\"type\":\"header\",\"v\":").append(VERSION)
        append(",\"sessionId\":\"").append(escape(sessionId))
        append("\",\"pkg\":\"").append(escape(pkg))
        append("\",\"wallMs\":").append(wallMs)
        append(",\"elapsedNs\":").append(elapsedNs)
        append(",\"model\":\"").append(escape(model))
        append("\",\"sdk\":").append(sdk)
        append(",\"app\":\"").append(escape(app))
        append("\"}")
    }

    fun imu(type: String, tNs: Long, x: Float, y: Float, z: Float): String =
        "{\"type\":\"$type\",\"t\":$tNs,\"x\":$x,\"y\":$y,\"z\":$z}"

    fun light(tNs: Long, lux: Float): String =
        "{\"type\":\"light\",\"t\":$tNs,\"lux\":$lux}"

    fun screen(tNs: Long, on: Boolean): String =
        "{\"type\":\"screen\",\"t\":$tNs,\"on\":$on}"

    fun battery(tNs: Long, pct: Int, charging: Boolean): String =
        "{\"type\":\"battery\",\"t\":$tNs,\"pct\":$pct,\"charging\":$charging}"

    fun foreground(tNs: Long, pkg: String?): String =
        if (pkg == null) "{\"type\":\"foreground\",\"t\":$tNs,\"pkg\":null}"
        else "{\"type\":\"foreground\",\"t\":$tNs,\"pkg\":\"${escape(pkg)}\"}"

    fun session(
        tNs: Long,
        event: String,
        sessionId: String,
        pkg: String,
        reason: String? = null,
        awayMs: Long? = null,
        endedAtWallMs: Long? = null,
    ): String = buildString {
        append("{\"type\":\"session\",\"t\":").append(tNs)
        append(",\"event\":\"").append(event)
        append("\",\"sessionId\":\"").append(escape(sessionId))
        append("\",\"pkg\":\"").append(escape(pkg)).append('"')
        if (reason != null) append(",\"reason\":\"").append(escape(reason)).append('"')
        if (awayMs != null) append(",\"awayMs\":").append(awayMs)
        if (endedAtWallMs != null) append(",\"endedAtWallMs\":").append(endedAtWallMs)
        append('}')
    }

    const val TYPE_GYRO = "gyro"
    const val TYPE_ACCEL = "accel"

    private fun escape(s: String): String =
        buildString(s.length) {
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
}
