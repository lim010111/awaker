package com.awaker.checkpoint

/**
 * 체크포인트 발동 상태 머신 (이슈 05의 핵심, 순수 Kotlin).
 *
 * - 발동: teacher 룰 양성이 dwell(초안 90s) 동안 유지되면 표시 (CONTEXT.md
 *   "신뢰도 × 체류" — 프로토타입에선 룰 양성이 신뢰도 게이트를 대행, ADR-0011)
 * - 체류 연장: 시트 닫힘 + 카운터 증가 + extensionMs(초안 60s) 후 룰이 여전히
 *   양성이면 더 높은 시트로 재표시 (ADR-0003 점진적 가림: 25% → +15%p → 70% cap)
 * - 자발 종료: 이 슬라이스에선 선택 기록 + 시트 닫힘까지만 (검증 루프는 이슈 06)
 * - 세션 종료/이탈: 카운터·가림 리셋 (AC), 표시 중이면 Dismiss
 * - 북극성 N1: 표시 후 1분 이내 후보 앱 이탈 여부 (ADR-0007) — 선택과 무관하게
 *   표시마다 한 번 판정
 *
 * 시간은 단조 ms. 폴링(5s) tick 구동이라 dwell/N1 판정에 최대 폴링 주기의
 * 지연이 있다 — 튜닝 변수 대비 무시 가능.
 */
class CheckpointScheduler(private val config: Config = Config()) {

    data class Config(
        val dwellMs: Long = 90_000,
        val extensionMs: Long = 60_000,
        val n1WindowMs: Long = 60_000,
        val baseHeightFraction: Float = 0.25f,
        val heightStepPerExtension: Float = 0.15f,
        val maxHeightFraction: Float = 0.70f, // 상단 30%는 항상 후보 앱이 보인다 (ADR-0003)
    )

    sealed interface Effect {
        /** 시트를 띄워라. [ordinal]은 이번 세션 몇 번째 표시인지(0부터). */
        data class Show(val atMs: Long, val ordinal: Int, val heightFraction: Float) : Effect

        /** 북극성 N1 판정 결과를 기록하라. */
        data class RecordN1(val shownAtMs: Long, val left: Boolean) : Effect

        /** 세션이 끝났으니 표시 중인 시트를 내려라. */
        data object Dismiss : Effect
    }

    private sealed interface Phase {
        data object Idle : Phase
        data class Arming(val since: Long) : Phase
        data class Showing(val shownAt: Long) : Phase
        data class Snoozed(val until: Long) : Phase
    }

    private var phase: Phase = Phase.Idle
    private var extensionCount = 0
    private var pendingN1ShownAt: Long? = null

    val isShowing: Boolean
        get() = phase is Phase.Showing

    fun heightFor(extensions: Int): Float =
        (config.baseHeightFraction + config.heightStepPerExtension * extensions)
            .coerceAtMost(config.maxHeightFraction)

    fun onTick(nowMs: Long, sessionActive: Boolean, rulePositive: Boolean): List<Effect> {
        val effects = mutableListOf<Effect>()

        pendingN1ShownAt?.let { shownAt ->
            if (!sessionActive) {
                effects += Effect.RecordN1(shownAt, left = true)
                pendingN1ShownAt = null
            } else if (nowMs - shownAt >= config.n1WindowMs) {
                effects += Effect.RecordN1(shownAt, left = false)
                pendingN1ShownAt = null
            }
        }

        if (!sessionActive) {
            if (phase is Phase.Showing) effects += Effect.Dismiss
            phase = Phase.Idle
            extensionCount = 0 // 세션 단위 리셋 (CONTEXT.md 점진적 가림)
            return effects
        }

        when (val p = phase) {
            is Phase.Idle -> if (rulePositive) phase = Phase.Arming(nowMs)
            is Phase.Arming ->
                if (!rulePositive) {
                    phase = Phase.Idle
                } else if (nowMs - p.since >= config.dwellMs) {
                    effects += show(nowMs)
                }
            is Phase.Showing -> Unit // 사용자의 선택을 기다린다 — 룰 해제로 닫지 않음
            is Phase.Snoozed ->
                if (nowMs >= p.until) {
                    // 연장 시간이 끝났다. 여전히 양성이면 즉시 재표시, 아니면 새로 발동 대기.
                    if (rulePositive) effects += show(nowMs) else phase = Phase.Idle
                }
        }
        return effects
    }

    /** 사용자가 "체류 연장"을 선택 — 시트는 호출자가 내린다. */
    fun onExtend(nowMs: Long) {
        if (phase !is Phase.Showing) return
        extensionCount++
        phase = Phase.Snoozed(until = nowMs + config.extensionMs)
    }

    /** 사용자가 "자발 종료"를 선택 — 검증 루프(이슈 06) 동안 재발동을 막는다. */
    fun onExitChosen(nowMs: Long) {
        if (phase !is Phase.Showing) return
        phase = Phase.Snoozed(until = nowMs + config.extensionMs)
    }

    private fun show(nowMs: Long): Effect.Show {
        phase = Phase.Showing(nowMs)
        pendingN1ShownAt = nowMs
        return Effect.Show(nowMs, ordinal = extensionCount, heightFraction = heightFor(extensionCount))
    }
}
