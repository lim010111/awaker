package com.awaker.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** 세션 로그 파일 목록 + 공유 export (이슈 03 — 수동 export로 충분, 1차 코호트 = 본인). */
@Composable
fun LogsCard(refresh: Int) {
    val context = LocalContext.current
    val files = remember(refresh) {
        File(context.getExternalFilesDir(null), "logs")
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(20)
            ?: emptyList()
    }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("센서 로그", style = MaterialTheme.typography.titleMedium)
            if (files.isEmpty()) {
                Text("아직 로그 파일이 없어요", style = MaterialTheme.typography.bodyMedium)
            }
            for (file in files) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { share(context, file) }) { Text("공유") }
                }
            }
            Text(
                "adb: adb pull ${context.getExternalFilesDir(null)}/logs/",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "로그 공유"))
}
