package com.awaker.logging

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/**
 * SensorManager 기반 IMU/조도 캡처. SENSOR_DELAY_GAME(~50Hz)으로 자이로/가속도,
 * on-change로 조도. 전용 HandlerThread에서 콜백을 받아 폴링 루프와 분리한다.
 * SensorEvent.timestamp는 elapsedRealtimeNanos 클럭 — 공통 타임라인에 무변환 기록.
 */
class SensorCapture(private val sensorManager: SensorManager) : SensorSource {

    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    override fun start(sink: SensorSink) {
        if (thread != null) return
        val handlerThread = HandlerThread("awaker-sensors").apply { start() }
        val handler = Handler(handlerThread.looper)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> sink.onImu(
                        LogSchema.TYPE_GYRO, event.timestamp,
                        event.values[0], event.values[1], event.values[2],
                    )
                    Sensor.TYPE_ACCELEROMETER -> sink.onImu(
                        LogSchema.TYPE_ACCEL, event.timestamp,
                        event.values[0], event.values[1], event.values[2],
                    )
                    Sensor.TYPE_LIGHT -> sink.onLight(event.timestamp, event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        listOf(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER).forEach { type ->
            sensorManager.getDefaultSensor(type)?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }

        thread = handlerThread
        listener = sensorListener
    }

    override fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
        thread?.quitSafely()
        thread = null
    }
}
