package com.awaker.core

/**
 * 체감/판정 변수의 기본값 모음. 제품 원칙: 기본값만 제공하고 사용자 조정을
 * 허용한다 — 프로토타입에선 상수로 두고, 조정 UI는 베타 확장에서 결정.
 */
object Tunables {
    /** 세션 종료 판정 — 후보 앱이 포그라운드를 떠나 있어야 하는 최소 시간 (CONTEXT.md 세션 정의). */
    const val SESSION_AWAY_GRACE_MS: Long = 5 * 60_000L

    /** 화면 켜짐 상태의 포그라운드 앱 폴링 주기. */
    const val FOREGROUND_POLL_MS: Long = 5_000L

    /** 화면 꺼짐 상태의 폴링 주기 — away 만료 판정만 필요하므로 느리게. */
    const val SCREEN_OFF_POLL_MS: Long = 60_000L

    /** 프로토타입 하드코딩 후보 앱 셋 (이슈 02 — 기본셋 최소 2~3개). */
    val DEFAULT_CANDIDATE_PACKAGES: Set<String> = setOf(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.twitter.android", // X
    )
}
