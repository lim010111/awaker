package com.awaker.exit

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.awaker.logging.RecordingController

/**
 * 자발 종료 글루 (이슈 06): 체크포인트의 "여기서 멈추기" 선택 → 자체 가속도
 * 리스너로 [ExitVerifier]를 구동하고, 검증 성공 시 사운드 재생 + 세션 즉시 종료
 * 요청. RecordingController와 독립된 리스너를 쓰는 이유: 세션이 끝난 뒤에도
 * (사운드 정지 판정까지) 계속 감지해야 하기 때문. 수명은 최장 15분로 유계.
 */
class ExitFlowController(
    private val sensorManager: SensorManager,
    private val recording: RecordingController,
    private val requestSessionEnd: (pkg: String) -> Unit,
    private val verifier: ExitVerifier = ExitVerifier(),
    private val player: NoisePlayer = NoisePlayer(),
) {
    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null
    private var sessionId: String? = null
    private var pkg: String? = null

    /** 체크포인트에서 자발 종료 선택 시 호출 (메인 스레드). */
    @Synchronized
    fun begin(sessionId: String, pkg: String) {
        if (thread != null) return // 이미 진행 중인 검증 루프가 있다
        this.sessionId = sessionId
        this.pkg = pkg

        val handlerThread = HandlerThread("awaker-exit").apply { start() }
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nowMs = event.timestamp / 1_000_000
                verifier.onSample(nowMs, event.values[2])?.let(::handle)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(
                sensorListener, it, SensorManager.SENSOR_DELAY_UI, Handler(handlerThread.looper),
            )
        } ?: run { // 가속도계가 없으면 검증 자체가 불가 — 즉시 실패 처리
            handlerThread.quitSafely()
            return
        }
        thread = handlerThread
        listener = sensorListener
        verifier.begin(android.os.SystemClock.elapsedRealtime())
    }

    private fun handle(event: ExitVerifier.Event) {
        val wallMs = System.currentTimeMillis()
        val id = sessionId ?: return
        when (event) {
            ExitVerifier.Event.Verified -> {
                // 순서 중요: 로그 라인을 먼저 (세션 종료가 파일을 닫기 전에)
                recording.onExitVerify(id, wallMs, verified = true)
                recording.onSound(id, wallMs, "start")
                player.start()
                pkg?.let(requestSessionEnd)
            }
            ExitVerifier.Event.Failed -> {
                recording.onExitVerify(id, wallMs, verified = false)
                teardown()
            }
            is ExitVerifier.Event.StopSound -> {
                player.stop()
                // 세션 파일은 이미 닫혔을 수 있다 — 열려 있을 때만 남는 best-effort 기록
                recording.onSound(id, wallMs, "stop", event.reason)
                teardown()
            }
        }
    }

    @Synchronized
    private fun teardown() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
        thread?.quitSafely()
        thread = null
        sessionId = null
        pkg = null
    }
}
