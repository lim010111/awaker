package com.awaker.detection

import java.util.ArrayDeque

/**
 * Teacher 룰 v0 — AS 스크롤 운동학 기반 무지성 스크롤 판정 (순수 Kotlin).
 * ADR-0010의 dense 라벨원이자 ADR-0011의 프로토타입 라이브 트리거.
 *
 * 판정 직관(CONTEXT.md 예시 대화): "2초 간격으로 반사적으로 넘기고 멈춤이
 * 없으면 무지성. 속도 변화가 있고 멈춰서 읽는 구간이 있으면 아님."
 *
 * v0 수식 (초안 — 임계는 본인 데이터 self-annotation으로 1차 튜닝, [Config]):
 *
 * fling = 스크롤 이벤트를 debounceMs로 뭉친 제스처. 최근 windowMs 안의 fling
 * 타임스탬프 f₁…fₙ, 간격 gᵢ = fᵢ₊₁ − fᵢ 에 대해
 *
 * 양성 진입 (모두 충족):
 *   n ≥ minFlings                    — 표본 충분
 *   span = fₙ − f₁ ≥ minSpanMs      — 지속된 행동 (순간 폭주 배제)
 *   median(g) ≤ maxMedianGapMs      — 반사적 케이던스
 *   max(g) ≤ maxEntryPauseMs        — 의미 있는 멈춤 없음
 *
 * 양성 해제 (하나라도):
 *   now − fₙ > exitSilenceMs        — 진짜 멈춤 (읽기/시청/이탈)
 *   window 내 n < exitMinFlings     — 케이던스 붕괴
 *   reset() — 세션 종료 (로그에는 세션 end가 곧 암묵적 해제)
 *
 * 시간은 단조 ms(elapsedRealtime). 판정에 X(권한-경량 센서)가 아닌 AS 신호를
 * 쓰는 것은 베타 한정 — 출시 경로에서 제거(ADR-0004).
 */
class TeacherRule(private val config: Config = Config()) {

    data class Config(
        val windowMs: Long = 60_000,
        val debounceMs: Long = 500,
        val minFlings: Int = 8,
        val minSpanMs: Long = 30_000,
        val maxMedianGapMs: Long = 8_000,
        val maxEntryPauseMs: Long = 20_000,
        val exitSilenceMs: Long = 30_000,
        val exitMinFlings: Int = 4,
    )

    /** 판정 시점의 윈도우 운동학 스냅샷 — rule 로그 라인과 replay 대조에 쓰인다. */
    data class Metrics(
        val flings: Int,
        val spanMs: Long,
        val medianGapMs: Long,
        val maxGapMs: Long,
    )

    sealed interface Transition {
        val atMs: Long
        val metrics: Metrics

        data class Enter(override val atMs: Long, override val metrics: Metrics) : Transition
        data class Exit(
            override val atMs: Long,
            override val metrics: Metrics,
            val reason: String,
        ) : Transition
    }

    private val flings = ArrayDeque<Long>()
    private var lastScrollMs = Long.MIN_VALUE

    var isPositive: Boolean = false
        private set

    /** AS 스크롤 이벤트 (debounce 전 raw). 전이가 생기면 반환. */
    fun onScroll(atMs: Long): Transition? {
        if (lastScrollMs == Long.MIN_VALUE || atMs - lastScrollMs >= config.debounceMs) {
            flings.addLast(atMs)
        }
        lastScrollMs = atMs
        return evaluate(atMs)
    }

    /** 주기 평가 — 침묵에 의한 해제는 스크롤 없이도 판정돼야 한다. */
    fun onTick(atMs: Long): Transition? = evaluate(atMs)

    /** 세션 종료 등 외부 리셋. 양성이었다면 Exit을 반환한다. */
    fun reset(atMs: Long): Transition? {
        val wasPositive = isPositive
        val metrics = snapshot()
        flings.clear()
        lastScrollMs = Long.MIN_VALUE
        isPositive = false
        return if (wasPositive) Transition.Exit(atMs, metrics, reason = "reset") else null
    }

    private fun evaluate(nowMs: Long): Transition? {
        while (flings.isNotEmpty() && nowMs - flings.first() > config.windowMs) flings.removeFirst()
        val metrics = snapshot()

        if (!isPositive) {
            val enter = metrics.flings >= config.minFlings &&
                metrics.spanMs >= config.minSpanMs &&
                metrics.medianGapMs <= config.maxMedianGapMs &&
                metrics.maxGapMs <= config.maxEntryPauseMs
            if (enter) {
                isPositive = true
                return Transition.Enter(nowMs, metrics)
            }
            return null
        }

        val silence = if (flings.isEmpty()) Long.MAX_VALUE else nowMs - flings.last()
        val reason = when {
            silence > config.exitSilenceMs -> "silence"
            metrics.flings < config.exitMinFlings -> "cadence_collapse"
            else -> null
        }
        if (reason != null) {
            isPositive = false
            return Transition.Exit(nowMs, metrics, reason)
        }
        return null
    }

    private fun snapshot(): Metrics {
        val list = flings.toLongArray()
        if (list.size < 2) return Metrics(list.size, 0, 0, 0)
        val gaps = LongArray(list.size - 1) { list[it + 1] - list[it] }
        gaps.sort()
        val median = gaps[gaps.size / 2]
        return Metrics(
            flings = list.size,
            spanMs = list.last() - list.first(),
            medianGapMs = median,
            maxGapMs = gaps.last(),
        )
    }
}
