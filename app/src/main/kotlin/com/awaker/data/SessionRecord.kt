package com.awaker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 세션 시작/종료 기록 (이슈 02). 진행 중인 세션은 endedAt이 null. */
@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey val sessionId: String,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** 5분 룰 만료로 종료가 확정된 시각 — endedAt(포그라운드 이탈 시각)과 구분. */
    val endDecidedAt: Long? = null,
    val endReason: String? = null,
)
