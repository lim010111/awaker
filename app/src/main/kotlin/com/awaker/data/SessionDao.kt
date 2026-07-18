package com.awaker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SessionRecord)

    @Query(
        "UPDATE sessions SET endedAt = :endedAt, endDecidedAt = :decidedAt, endReason = :reason " +
            "WHERE sessionId = :sessionId",
    )
    suspend fun markEnded(sessionId: String, endedAt: Long, decidedAt: Long, reason: String)

    /** 서비스가 죽으며 열린 채 남은 세션 정리 (재시작 시 호출). */
    @Query(
        "UPDATE sessions SET endedAt = :now, endDecidedAt = :now, endReason = :reason " +
            "WHERE endedAt IS NULL",
    )
    suspend fun closeDangling(now: Long, reason: String)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<SessionRecord>>
}
