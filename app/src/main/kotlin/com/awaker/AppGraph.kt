package com.awaker

import android.app.Application
import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import com.awaker.checkpoint.CheckpointCoordinator
import com.awaker.data.AppDatabase
import com.awaker.data.CheckpointDao
import com.awaker.data.SessionRepository
import com.awaker.detection.DetectionPipeline
import com.awaker.detection.TeacherRule
import com.awaker.exit.ExitFlowController
import java.util.concurrent.atomic.AtomicReference
import com.awaker.logging.JsonlFileSink
import com.awaker.logging.LogSchema
import com.awaker.logging.RecordingController
import com.awaker.logging.SensorCapture
import com.awaker.logging.TimeSource
import java.io.File

/**
 * 프로세스 전역 객체 그래프 — TrackerService와 ScrollCaptureService(AS)가 같은
 * RecordingController/DetectionPipeline을 공유해야 해서 Application 수준으로 올렸다.
 */
object AppGraph {
    lateinit var repository: SessionRepository
        private set
    lateinit var recording: RecordingController
        private set
    lateinit var detection: DetectionPipeline
        private set
    lateinit var checkpoint: CheckpointCoordinator
        private set
    lateinit var checkpointDao: CheckpointDao
        private set
    lateinit var exitFlow: ExitFlowController
        private set

    /** 자발 종료 검증 성공 → 폴링 루프가 다음 tick에 세션을 즉시 종료한다 (이슈 06). */
    val pendingVoluntaryExit = AtomicReference<String?>(null)

    fun init(app: Application) {
        val db = AppDatabase.get(app)
        repository = SessionRepository(db.sessionDao())
        checkpointDao = db.checkpointDao()
        recording = buildRecording(app)
        detection = DetectionPipeline(TeacherRule(), recording)
        exitFlow = ExitFlowController(
            sensorManager = app.getSystemService(SensorManager::class.java),
            recording = recording,
            requestSessionEnd = { pkg -> pendingVoluntaryExit.set(pkg) },
        )
        checkpoint = CheckpointCoordinator(
            app, checkpointDao, recording, detection,
            onExitChosen = { sessionId, pkg -> exitFlow.begin(sessionId, pkg) },
        )
    }

    private fun buildRecording(context: Context): RecordingController {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return RecordingController(
            time = object : TimeSource {
                override fun wallMs() = System.currentTimeMillis()
                override fun elapsedNs() = SystemClock.elapsedRealtimeNanos()
            },
            sensors = SensorCapture(context.getSystemService(SensorManager::class.java)),
            sinkFactory = { sessionId, _ ->
                JsonlFileSink(File(context.getExternalFilesDir(null), "logs/awaker-$sessionId.jsonl"))
            },
            headerFor = { sessionId, pkg, wallMs, elapsedNs ->
                LogSchema.header(
                    sessionId, pkg, wallMs, elapsedNs,
                    model = Build.MODEL, sdk = Build.VERSION.SDK_INT, app = appVersion,
                )
            },
        )
    }
}
