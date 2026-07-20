package com.awaker.checkpoint

import com.awaker.session.SessionEvent

/**
 * 체크포인트·N1 기록의 겨냥 세션 (순수 Kotlin, ADR-0014). 열린 세션을 추적하고
 * 겨냥이 활성 세션을 따라가게 한다: Started 추가+겨냥, Resumed 조회+겨냥,
 * Ended 제거+해제. Resumed가 기존 [Session]을 그대로 겨냥하므로 원래
 * startWallMs가 보존된다 — "N분째" 메시지가 세션 시작 기준을 유지.
 */
class SessionAim {

    class Session(val id: String, val pkg: String, val startWallMs: Long)

    private val open = LinkedHashMap<String, Session>()

    /** 폴링 스레드가 갱신하고 시트 선택(메인 스레드)이 읽는다. */
    @Volatile
    var current: Session? = null
        private set

    fun onSessionEvents(events: List<SessionEvent>) {
        for (event in events) when (event) {
            is SessionEvent.Started -> {
                val session = Session(event.sessionId, event.packageName, event.at)
                open[event.sessionId] = session
                current = session
            }
            is SessionEvent.Resumed -> open[event.sessionId]?.let { current = it }
            is SessionEvent.Ended -> {
                open.remove(event.sessionId)
                if (current?.id == event.sessionId) current = null
            }
        }
    }
}
