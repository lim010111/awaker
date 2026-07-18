package com.awaker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.awaker.core.Tunables
import com.awaker.data.AppDatabase
import com.awaker.data.SessionRepository
import com.awaker.session.EndReason
import com.awaker.session.SessionTracker
import com.awaker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 상주 포그라운드 서비스 (이슈 02). UsageStats를 폴링해 [SessionTracker]에
 * 공급하고, 세션 경계 이벤트를 DB에 기록한다. 배터리·생존성이 이 슬라이스의
 * 숨은 절반 — 화면 꺼짐 중엔 UsageStats 조회 없이 만료 판정만 느리게 돈다.
 */
class TrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracker = SessionTracker()
    private lateinit var foregroundSource: ForegroundAppSource
    private lateinit var repository: SessionRepository

    @Volatile
    private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON -> screenOn = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        foregroundSource = ForegroundAppSource(getSystemService(UsageStatsManager::class.java))
        repository = SessionRepository(AppDatabase.get(this).sessionDao())

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )

        running.value = true
        scope.launch {
            repository.closeDangling(System.currentTimeMillis())
            pollLoop()
        }
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val events =
                if (screenOn) tracker.onForeground(foregroundSource.currentForeground(now), now)
                else tracker.onForeground(null, now)
            repository.apply(events)
            delay(if (screenOn) Tunables.FOREGROUND_POLL_MS else Tunables.SCREEN_OFF_POLL_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        // 서비스가 정상 정지되면 열린 세션을 닫아 기록을 깨끗하게 남긴다.
        val events = tracker.endAll(System.currentTimeMillis(), EndReason.TRACKER_STOPPED)
        runBlocking { repository.apply(events) }
        unregisterReceiver(screenReceiver)
        scope.cancel()
        running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "상주 감시", NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Awaker 감시 중")
            .setContentText("후보 앱 세션을 관찰하고 있어요")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "tracker"
        private const val NOTIFICATION_ID = 1

        private val running = MutableStateFlow(false)

        /** 인앱 화면의 서비스 상태 표시용. */
        val isRunning: StateFlow<Boolean> = running

        fun start(context: Context) =
            context.startForegroundService(Intent(context, TrackerService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, TrackerService::class.java))
    }
}
