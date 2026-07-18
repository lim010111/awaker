package com.awaker.data

import com.awaker.session.EndReason
import com.awaker.session.SessionEvent
import kotlinx.coroutines.flow.Flow

/** [SessionTracker] 이벤트를 DB 기록으로 변환하는 유일한 통로. */
class SessionRepository(private val dao: SessionDao) {

    suspend fun apply(events: List<SessionEvent>) {
        for (event in events) when (event) {
            is SessionEvent.Started ->
                dao.insert(SessionRecord(event.sessionId, event.packageName, startedAt = event.at))
            is SessionEvent.Resumed -> Unit // 세션 경계 변화 없음
            is SessionEvent.Ended ->
                dao.markEnded(event.sessionId, event.endedAt, event.decidedAt, event.reason.name)
        }
    }

    suspend fun closeDangling(now: Long) = dao.closeDangling(now, EndReason.TRACKER_STOPPED.name)

    fun recentSessions(limit: Int = 100): Flow<List<SessionRecord>> = dao.recentSessions(limit)
}
