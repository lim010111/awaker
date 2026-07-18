package com.awaker.checkpoint

/**
 * 로컬 템플릿 풀 stub (이슈 05) — LLM 호출 없이 소수 문장 + 메시지 슬롯 주입.
 * ADR-0001의 prefetch 구조는 이 선택 지점(표시 직전 렌더)으로 뼈대만 유지하고,
 * 신뢰도 곡선 기반 prefetch는 미검증(ADR-0011). 실측 수치(누적 분 버킷, N번째
 * 연장)는 시스템이 주입한다 — LLM 환각으로부터 사실 수치를 분리하는 슬롯 원칙
 * (CONTEXT.md 메시지 슬롯)의 stub 버전.
 */
object CheckpointTemplates {

    /** 누적 분 → 구간 라벨 (CONTEXT.md 버킷팅 — raw 수치 대신 구간). */
    fun minutesBucket(elapsedMinutes: Long): String = when {
        elapsedMinutes < 5 -> "5분이 안 되게"
        elapsedMinutes < 10 -> "5분에서 10분쯤"
        elapsedMinutes < 30 -> "10분에서 30분쯤"
        elapsedMinutes < 60 -> "30분에서 1시간쯤"
        else -> "1시간 넘게"
    }

    private val pool = listOf(
        "이 세션에서 {bucket} 보고 있어요. {nth}번째 체크인이에요.",
        "{bucket} 스크롤했어요. 지금 잠깐, 계속 볼지 정해볼까요?",
        "여기까지 {bucket}. 손이 저절로 넘기고 있진 않았나요?",
        "{nth}번째 인사예요. 벌써 {bucket} 지났어요.",
    )

    /** ordinal은 0부터 (세션 내 몇 번째 표시인지). */
    fun render(elapsedMinutes: Long, ordinal: Int): String =
        pool[ordinal % pool.size]
            .replace("{bucket}", minutesBucket(elapsedMinutes))
            .replace("{nth}", (ordinal + 1).toString())
}
