package com.awaker.checkpoint

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.awaker.data.CheckpointDao
import com.awaker.data.CheckpointEvent
import com.awaker.detection.DetectionPipeline
import com.awaker.logging.LogSchema
import com.awaker.logging.RecordingController
import com.awaker.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 체크포인트 글루 (이슈 05): 순수 [CheckpointScheduler]의 효과를 오버레이 표시,
 * Room 기록(N1 집계), 세션 로그 라인으로 집행한다. 폴링 스레드에서 tick을 받고
 * 오버레이 조작만 메인 스레드로 넘긴다.
 */
class CheckpointCoordinator(
    context: Context,
    private val dao: CheckpointDao,
    private val recording: RecordingController,
    private val detection: DetectionPipeline,
    private val scheduler: CheckpointScheduler = CheckpointScheduler(),
) {
    private val overlay = OverlayController(context)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Session(val id: String, val pkg: String, val startWallMs: Long)

    @Volatile
    private var session: Session? = null

    @Volatile
    private var currentEventId: Long? = null

    private var lastShow: CheckpointScheduler.Effect.Show? = null

    /** TrackerService 폴링 루프가 세션 경계를 알려준다. */
    fun onSessionEvents(events: List<SessionEvent>) {
        for (event in events) when (event) {
            is SessionEvent.Started -> session = Session(event.sessionId, event.packageName, event.at)
            is SessionEvent.Ended -> if (session?.id == event.sessionId) session = null
            is SessionEvent.Resumed -> Unit
        }
    }

    /** 폴링 tick — elapsedMs는 단조, wallMs는 벽시계 (로그 앵커용). */
    fun onTick(elapsedMs: Long, wallMs: Long, sessionActive: Boolean) {
        val effects = synchronized(scheduler) {
            scheduler.onTick(elapsedMs, sessionActive, detection.isPositive)
        }
        for (effect in effects) when (effect) {
            is CheckpointScheduler.Effect.Show -> handleShow(effect, wallMs)
            is CheckpointScheduler.Effect.RecordN1 -> handleN1(effect, wallMs)
            CheckpointScheduler.Effect.Dismiss -> main.post { overlay.hide() }
        }
    }

    private fun handleShow(effect: CheckpointScheduler.Effect.Show, wallMs: Long) {
        val current = session ?: return
        lastShow = effect
        val elapsedMinutes = (wallMs - current.startWallMs) / 60_000
        val message = CheckpointTemplates.render(elapsedMinutes, effect.ordinal)
        val heightPct = (effect.heightFraction * 100).toInt()

        recording.onCheckpoint(current.id, wallMs, "shown", effect.ordinal, heightPct)
        scope.launch {
            currentEventId = dao.insert(
                CheckpointEvent(
                    sessionId = current.id,
                    shownAtWall = wallMs,
                    ordinal = effect.ordinal,
                    heightPct = heightPct,
                ),
            )
        }
        main.post {
            overlay.show(effect.heightFraction) {
                CheckpointSheet(
                    message = message,
                    onExtend = { onChoice("extend") },
                    onExit = { onChoice("exit") },
                )
            }
        }
    }

    private fun handleN1(effect: CheckpointScheduler.Effect.RecordN1, wallMs: Long) {
        session?.let { recording.onN1(it.id, wallMs, shownAtElapsedMs = effect.shownAtMs, left = effect.left) }
        currentEventId?.let { id -> scope.launch { dao.setN1(id, effect.left) } }
    }

    private fun onChoice(choice: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        val wallMs = System.currentTimeMillis()
        synchronized(scheduler) {
            if (choice == "extend") scheduler.onExtend(now) else scheduler.onExitChosen(now)
        }
        val show = lastShow
        session?.let {
            recording.onCheckpoint(
                it.id, wallMs, "choice",
                ordinal = show?.ordinal ?: 0,
                heightPct = ((show?.heightFraction ?: 0f) * 100).toInt(),
                choice = choice,
            )
        }
        currentEventId?.let { id -> scope.launch { dao.setChoice(id, choice) } }
        main.post { overlay.hide() }
        // 자발 종료의 face-down 검증 루프는 이슈 06에서 이 지점에 연결된다.
    }
}
