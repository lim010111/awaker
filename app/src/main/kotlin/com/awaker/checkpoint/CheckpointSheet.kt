package com.awaker.checkpoint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 체크포인트 바텀 시트 (이슈 05). 멈춤을 강요하지 않는다 — 사실 인용 메시지 +
 * 선택지 딱 둘 (CONTEXT.md 체크포인트 철학).
 */
@Composable
fun CheckpointSheet(
    message: String,
    onExtend: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onExtend, modifier = Modifier.weight(1f)) {
                    Text("1분 더 보기")
                }
                Button(onClick = onExit, modifier = Modifier.weight(1f)) {
                    Text("여기서 멈추기")
                }
            }
        }
    }
}
