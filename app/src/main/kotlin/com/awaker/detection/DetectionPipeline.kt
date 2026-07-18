package com.awaker.detection

import com.awaker.logging.RecordingController

/**
 * AS 스크롤 스트림과 teacher 룰을 공통 타임라인 로그에 잇는 글루 (이슈 04).
 * AS 콜백 스레드와 서비스 폴링 스레드가 같이 부르므로 메서드 단위로 동기화한다.
 *
 * 룰 전이 이벤트가 05 체크포인트의 발동 입력이자 07 replay의 대조 기준.
 */
class DetectionPipeline(
    private val rule: TeacherRule,
    private val recording: RecordingController,
) {
    /** AS가 관측한 스크롤 raw. tNs는 elapsedRealtimeNanos 클럭. */
    @Synchronized
    fun onScroll(tNs: Long, pkg: String, dx: Int, dy: Int) {
        recording.onScroll(tNs, pkg, dx, dy)
        emit(rule.onScroll(tNs / 1_000_000))
    }

    /** 폴링 주기 평가. 세션이 비활성이면 룰을 리셋한다(로그의 세션 end가 암묵적 해제). */
    @Synchronized
    fun onTick(elapsedMs: Long, sessionActive: Boolean) {
        if (!sessionActive) {
            rule.reset(elapsedMs) // 전이는 버린다 — 세션 파일이 이미 닫혔거나 닫히는 중
            return
        }
        emit(rule.onTick(elapsedMs))
    }

    val isPositive: Boolean
        @Synchronized get() = rule.isPositive

    private fun emit(transition: TeacherRule.Transition?) {
        when (transition) {
            is TeacherRule.Transition.Enter ->
                recording.onRule(transition.atMs * 1_000_000, "enter", transition.metrics, reason = null)
            is TeacherRule.Transition.Exit ->
                recording.onRule(transition.atMs * 1_000_000, "exit", transition.metrics, transition.reason)
            null -> Unit
        }
    }
}
