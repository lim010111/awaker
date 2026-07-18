package com.awaker.exit

/**
 * 자발 종료 검증 루프 (이슈 06, 순수 Kotlin). 선택이 아니라 **검증된 행동**이
 * 종료 조건이다: 자발 종료를 골라도 face-down을 하지 않으면 세션은 리셋되지
 * 않는다 (CONTEXT.md 자발 종료 정의).
 *
 * - 검증: 가속도 z축이 faceDownZ 이하(화면이 바닥을 향함)로 holdMs 유지 → 성공.
 *   verifyTimeoutMs 안에 못 하면 실패.
 * - 성공 → 환기 사운드 재생. 정지는 *기본 15분 타이머* 또는 *face-up 30초 유지*
 *   중 먼저 오는 쪽 (사운드 풀·사용자 타이머 선택·수면 모드는 v1 범위로 이연).
 *
 * 시간은 단조 ms, 판정은 전부 가속도 샘플 콜백으로 구동된다 (~50Hz면 충분).
 */
class ExitVerifier(private val config: Config = Config()) {

    data class Config(
        val verifyTimeoutMs: Long = 30_000,
        val faceDownZ: Float = -7f,
        val faceDownHoldMs: Long = 2_000,
        val soundTimerMs: Long = 15 * 60_000,
        val faceUpZ: Float = 7f,
        val faceUpHoldMs: Long = 30_000,
    )

    sealed interface Event {
        /** face-down 검증 성공 — 세션 종료 + 가림 리셋 + 사운드 시작. */
        data object Verified : Event

        /** 제한 시간 내 face-down 없음 — 세션 유지, 아무 일도 일어나지 않는다. */
        data object Failed : Event

        /** 사운드 정지. reason = timer | face_up. */
        data class StopSound(val reason: String) : Event
    }

    enum class Phase { IDLE, VERIFYING, PLAYING, DONE }

    var phase: Phase = Phase.IDLE
        private set

    private var verifyStartedAt = 0L
    private var playStartedAt = 0L
    private var faceDownSince: Long? = null
    private var faceUpSince: Long? = null

    /** 체크포인트에서 자발 종료를 선택한 순간 호출. */
    fun begin(nowMs: Long) {
        phase = Phase.VERIFYING
        verifyStartedAt = nowMs
        faceDownSince = null
        faceUpSince = null
    }

    /** 가속도 z축(m/s²) 샘플. 상태 전이가 생기면 이벤트를 반환한다. */
    fun onSample(nowMs: Long, z: Float): Event? = when (phase) {
        Phase.IDLE, Phase.DONE -> null
        Phase.VERIFYING -> verifying(nowMs, z)
        Phase.PLAYING -> playing(nowMs, z)
    }

    private fun verifying(nowMs: Long, z: Float): Event? {
        if (z <= config.faceDownZ) {
            val since = faceDownSince ?: nowMs.also { faceDownSince = it }
            if (nowMs - since >= config.faceDownHoldMs) {
                phase = Phase.PLAYING
                playStartedAt = nowMs
                faceUpSince = null
                return Event.Verified
            }
        } else {
            faceDownSince = null
            if (nowMs - verifyStartedAt >= config.verifyTimeoutMs) {
                phase = Phase.DONE
                return Event.Failed
            }
        }
        return null
    }

    private fun playing(nowMs: Long, z: Float): Event? {
        if (nowMs - playStartedAt >= config.soundTimerMs) {
            phase = Phase.DONE
            return Event.StopSound("timer")
        }
        if (z >= config.faceUpZ) {
            val since = faceUpSince ?: nowMs.also { faceUpSince = it }
            if (nowMs - since >= config.faceUpHoldMs) {
                phase = Phase.DONE
                return Event.StopSound("face_up")
            }
        } else {
            faceUpSince = null
        }
        return null
    }
}
