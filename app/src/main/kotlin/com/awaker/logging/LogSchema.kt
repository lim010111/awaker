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

    /** AS 스크롤 운동학 raw (이슈 04, 베타 한정 — ADR-0004). dx/dy는 프레임워크가 못 주면 -1. */
    fun scroll(tNs: Long, pkg: String, dx: Int, dy: Int): String =
        "{\"type\":\"scroll\",\"t\":$tNs,\"pkg\":\"${escape(pkg)}\",\"dx\":$dx,\"dy\":$dy}"

    /** teacher 룰 전이 (이슈 04) — 05 체크포인트 발동 입력이자 07 replay 대조 기준. */
    fun rule(
        tNs: Long,
        state: String,
        flings: Int,
        spanMs: Long,
        medianGapMs: Long,
        maxGapMs: Long,
        reason: String? = null,
    ): String = buildString {
        append("{\"type\":\"rule\",\"t\":").append(tNs)
        append(",\"state\":\"").append(state)
        append("\",\"flings\":").append(flings)
        append(",\"spanMs\":").append(spanMs)
        append(",\"medianGapMs\":").append(medianGapMs)
        append(",\"maxGapMs\":").append(maxGapMs)
        if (reason != null) append(",\"reason\":\"").append(escape(reason)).append('"')
        append('}')
    }

    /** 체크포인트 표시/선택 (이슈 05). event=shown|choice, choice=extend|exit. */
    fun checkpoint(
        tNs: Long,
        event: String,
        ordinal: Int,
        heightPct: Int,
        choice: String? = null,
    ): String = buildString {
        append("{\"type\":\"checkpoint\",\"t\":").append(tNs)
        append(",\"event\":\"").append(event)
        append("\",\"ordinal\":").append(ordinal)
        append(",\"heightPct\":").append(heightPct)
        if (choice != null) append(",\"choice\":\"").append(escape(choice)).append('"')
        append('}')
    }

    /** 북극성 N1 판정 (이슈 05, ADR-0007) — 표시 후 1분 이내 후보 앱 이탈 여부. */
    fun n1(tNs: Long, shownTNs: Long, left: Boolean): String =
        "{\"type\":\"n1\",\"t\":$tNs,\"shownT\":$shownTNs,\"left\":$left}"

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
