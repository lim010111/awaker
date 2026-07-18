package com.awaker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awaker.AppGraph
import com.awaker.data.N1Aggregate

/** 북극성 N1 집계 (이슈 05, ADR-0007) — 체크포인트 표시 후 1분 이내 이탈률. */
@Composable
fun N1Card() {
    val aggregate by AppGraph.checkpointDao.n1Aggregate()
        .collectAsStateWithLifecycle(initialValue = N1Aggregate(0, 0))

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("북극성 N1 — 1분 내 이탈률", style = MaterialTheme.typography.titleMedium)
            if (aggregate.shown == 0) {
                Text("아직 체크포인트 표시가 없어요", style = MaterialTheme.typography.bodyMedium)
            } else {
                val rate = aggregate.leftCount * 100 / aggregate.shown
                Text(
                    "$rate%  (표시 ${aggregate.shown}회 · 이탈 ${aggregate.leftCount}회 · 목표 50%+)",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
