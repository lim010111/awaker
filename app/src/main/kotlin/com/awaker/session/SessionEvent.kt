package com.awaker.session

/** [SessionTracker]가 세션 경계를 판정할 때 내보내는 이벤트. */
sealed interface SessionEvent {
    val sessionId: String
    val packageName: String

    /** 후보 앱이 포그라운드에 떴고 살아 있는 세션이 없어 새 세션이 시작됨. */
    data class Started(
        override val sessionId: String,
        override val packageName: String,
        val at: Long,
    ) : SessionEvent

    /** away 유예 창이 열려 있는 동안 같은 후보 앱으로 복귀 — 같은 세션 유지. */
    data class Resumed(
        override val sessionId: String,
        override val packageName: String,
        val at: Long,
        val awayMs: Long,
    ) : SessionEvent

    /**
     * 세션 종료. [endedAt]은 후보 앱이 포그라운드를 떠난 시각(논리적 종료 경계),
     * [decidedAt]은 5분 룰 만료로 종료가 확정된 시각.
     */
    data class Ended(
        override val sessionId: String,
        override val packageName: String,
        val endedAt: Long,
        val decidedAt: Long,
        val reason: EndReason,
    ) : SessionEvent
}

enum class EndReason {
    /** 5분 이상 포그라운드를 떠남 (CONTEXT.md 세션 정의). */
    AWAY_TIMEOUT,

    /** 자발 종료 검증 성공 (이슈 06에서 연결). */
    VOLUNTARY_EXIT,

    /** 감시 서비스 정지 — 세션을 열린 채 남기지 않기 위한 정리. */
    TRACKER_STOPPED,
}
