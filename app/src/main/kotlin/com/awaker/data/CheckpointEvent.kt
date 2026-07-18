package com.awaker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 체크포인트 표시 1회 = 1행 (이슈 05). choice와 N1 결과는 사후 갱신된다.
 * 북극성 N1(ADR-0007) 집계의 원천.
 */
@Entity(tableName = "checkpoint_events")
data class CheckpointEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val shownAtWall: Long,
    /** 세션 내 몇 번째 표시인지 (0부터). */
    val ordinal: Int,
    val heightPct: Int,
    /** extend / exit — 선택 전엔 null. */
    val choice: String? = null,
    /** 표시 후 1분 이내 후보 앱 이탈 여부 — 판정 전엔 null. */
    val leftWithinMinute: Boolean? = null,
)

data class N1Aggregate(val shown: Int, val leftCount: Int)

@Dao
interface CheckpointDao {
    @Insert
    suspend fun insert(event: CheckpointEvent): Long

    @Query("UPDATE checkpoint_events SET choice = :choice WHERE id = :id")
    suspend fun setChoice(id: Long, choice: String)

    @Query("UPDATE checkpoint_events SET leftWithinMinute = :left WHERE id = :id")
    suspend fun setN1(id: Long, left: Boolean)

    @Query(
        "SELECT COUNT(*) AS shown, " +
            "COALESCE(SUM(CASE WHEN leftWithinMinute = 1 THEN 1 ELSE 0 END), 0) AS leftCount " +
            "FROM checkpoint_events WHERE leftWithinMinute IS NOT NULL",
    )
    fun n1Aggregate(): Flow<N1Aggregate>
}
