package com.awaker.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awaker.core.Permissions
import com.awaker.data.SessionRecord
import com.awaker.data.SessionRepository
import com.awaker.service.TrackerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 권한 부여 상태 + 서비스 토글 + 세션 이력 (이슈 02 AC). */
@Composable
fun HomeScreen(repository: SessionRepository) {
    val context = LocalContext.current

    // 설정 화면에 다녀오면 권한 상태가 바뀌므로 ON_RESUME마다 다시 읽는다.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usageGranted = remember(refresh) { Permissions.hasUsageAccess(context) }
    val notificationsGranted = remember(refresh) { Permissions.notificationsEnabled(context) }
    val batteryExempt = remember(refresh) { Permissions.ignoresBatteryOptimizations(context) }
    val scrollCaptureEnabled = remember(refresh) { Permissions.scrollCaptureEnabled(context) }
    val overlayGranted = remember(refresh) { Permissions.canDrawOverlays(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val serviceRunning by TrackerService.isRunning.collectAsStateWithLifecycle()
    val sessions by repository.recentSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Awaker",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("권한", style = MaterialTheme.typography.titleMedium)
                    PermissionRow("사용 정보 접근 (필수)", usageGranted) {
                        context.startActivity(Permissions.usageAccessSettingsIntent())
                    }
                    PermissionRow("알림 (필수)", notificationsGranted) {
                        if (Permissions.needsNotificationRuntimePermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(Permissions.notificationSettingsIntent(context))
                        }
                    }
                    PermissionRow("배터리 최적화 제외 (상주 권장)", batteryExempt) {
                        context.startActivity(
                            Permissions.requestIgnoreBatteryOptimizationsIntent(context),
                        )
                    }
                    PermissionRow("접근성 — 스크롤 수집 (베타 한정)", scrollCaptureEnabled) {
                        context.startActivity(Permissions.accessibilitySettingsIntent())
                    }
                    PermissionRow("다른 앱 위에 표시 (체크포인트)", overlayGranted) {
                        context.startActivity(Permissions.overlaySettingsIntent(context))
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("감시 서비스", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (serviceRunning) "실행 중" else "정지됨",
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = usageGranted,
                            onClick = {
                                if (serviceRunning) TrackerService.stop(context)
                                else TrackerService.start(context)
                            },
                        ) {
                            Text(if (serviceRunning) "중지" else "시작")
                        }
                    }
                    if (!usageGranted) {
                        Text(
                            "사용 정보 접근 권한이 있어야 시작할 수 있어요",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item { N1Card() }

        item { LogsCard(refresh) }

        item { Text("세션 이력", style = MaterialTheme.typography.titleMedium) }

        if (sessions.isEmpty()) {
            item { Text("아직 기록된 세션이 없어요", style = MaterialTheme.typography.bodyMedium) }
        }
        items(sessions, key = { it.sessionId }) { session ->
            SessionRow(session)
            HorizontalDivider()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (granted) "✅" else "❌")
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
        if (!granted) TextButton(onClick = onRequest) { Text("설정") }
    }
}

@Composable
private fun SessionRow(session: SessionRecord) {
    val context = LocalContext.current
    val label = remember(session.packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(session.packageName, 0)).toString()
        }.getOrDefault(session.packageName)
    }
    val timeFormat = remember { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }

    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        val duration = session.endedAt?.let {
            DateUtils.formatElapsedTime((it - session.startedAt) / 1000)
        }
        Text(
            buildString {
                append(timeFormat.format(Date(session.startedAt)))
                if (duration != null) append(" · $duration") else append(" · 진행 중")
                session.endReason?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
