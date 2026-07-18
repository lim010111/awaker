package com.awaker.session

import com.awaker.core.Tunables
import java.util.UUID

/**
 * 후보 앱 세션 상태 머신 (이슈 02의 핵심, 순수 Kotlin — 시각은 전부 인자로 받아
 * JVM 단위 테스트 가능).
 *
 * 세션 정의(CONTEXT.md): 같은 후보 앱에서의 연속 사용 구간. 후보 앱이 5분 이상
 * 포그라운드를 떠나면 종료. 5분 안에 복귀하면 같은 세션이 이어진다. 후보 앱별로
 * 독립 세션이므로, 한 앱이 포그라운드인 동안 다른 앱 세션이 유예 중일 수 있다.
 *
 * 스레드 안전하지 않다 — 호출자가 단일 스레드(서비스 폴링 루프)에서 사용한다.
 */
class SessionTracker(
    private val candidatePackages: Set<String> = Tunables.DEFAULT_CANDIDATE_PACKAGES,
    private val awayGraceMs: Long = Tunables.SESSION_AWAY_GRACE_MS,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    private class Live(
        val sessionId: String,
        val startedAt: Long,
        var awaySince: Long?,
    )

    private val live = LinkedHashMap<String, Live>()
    private var foreground: String? = null

    // 자발 종료된 앱은 포그라운드를 한 번 떠나기 전까지 새 세션을 열지 않는다
    // (face-down 중에도 앱은 포그라운드라 즉시 재시작되는 것을 막는다 — 이슈 06).
    private val suppressed = mutableSetOf<String>()

    /** 현재 포그라운드인 후보 앱 (없으면 null). */
    val activeCandidate: String?
        get() = foreground

    /** 현재 포그라운드 후보 앱의 세션 id (없으면 null) — 로깅 활성화 게이트용. */
    val activeSessionId: String?
        get() = foreground?.let { live[it]?.sessionId }

    /**
     * 폴링이 관측한 현재 포그라운드 앱을 보고한다. 화면 꺼짐 등 "후보 앱 아님"은
     * null 또는 비후보 패키지로 보고하면 된다. 만료 판정도 함께 수행하므로 별도
     * tick 없이 이 메서드만 주기 호출해도 된다.
     */
    fun onForeground(packageName: String?, at: Long): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        expireOverdue(at, events)
        suppressed.retainAll { it == packageName } // 포그라운드를 떠난 앱은 억제 해제

        val candidate = packageName?.takeIf { it in candidatePackages }

        // 이전 포그라운드 후보가 밀려났으면 away 유예 시작.
        foreground?.let { prev ->
            if (prev != candidate) live[prev]?.let { if (it.awaySince == null) it.awaySince = at }
        }
        foreground = candidate

        if (candidate != null && candidate !in suppressed) {
            val existing = live[candidate]
            when {
                existing == null -> {
                    val id = newSessionId()
                    live[candidate] = Live(id, at, awaySince = null)
                    events += SessionEvent.Started(id, candidate, at)
                }
                existing.awaySince != null -> {
                    events += SessionEvent.Resumed(
                        existing.sessionId, candidate, at, awayMs = at - existing.awaySince!!,
                    )
                    existing.awaySince = null
                }
                // 이미 포그라운드였던 세션이 이어지는 중 — 이벤트 없음.
            }
        }
        return events
    }

    /** 포그라운드 관측 없이 만료 판정만 수행한다 (화면 꺼짐 중 주기 호출용). */
    fun onTick(at: Long): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        expireOverdue(at, events)
        return events
    }

    /**
     * 유예를 기다리지 않고 세션을 즉시 종료한다 — 자발 종료 검증 성공(이슈 06)
     * 또는 서비스 정지 정리용.
     */
    fun endNow(packageName: String, at: Long, reason: EndReason): SessionEvent.Ended? {
        val session = live.remove(packageName) ?: return null
        if (foreground == packageName) foreground = null
        if (reason == EndReason.VOLUNTARY_EXIT) suppressed.add(packageName)
        return SessionEvent.Ended(session.sessionId, packageName, endedAt = at, decidedAt = at, reason = reason)
    }

    /** 살아 있는 모든 세션을 즉시 종료한다 (서비스 정지 시). */
    fun endAll(at: Long, reason: EndReason): List<SessionEvent> =
        live.keys.toList().mapNotNull { endNow(it, at, reason) }

    private fun expireOverdue(at: Long, into: MutableList<SessionEvent>) {
        val iter = live.entries.iterator()
        while (iter.hasNext()) {
            val (pkg, session) = iter.next()
            val awaySince = session.awaySince ?: continue
            if (at - awaySince >= awayGraceMs) {
                iter.remove()
                into += SessionEvent.Ended(
                    session.sessionId, pkg,
                    endedAt = awaySince, decidedAt = at, reason = EndReason.AWAY_TIMEOUT,
                )
            }
        }
    }
}
