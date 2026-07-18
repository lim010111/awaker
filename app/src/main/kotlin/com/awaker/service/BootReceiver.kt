package com.awaker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.awaker.core.Permissions

/** 재부팅 후 상주 복구 — 24시간 상주 AC의 일부 (이슈 02). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Permissions.hasUsageAccess(context)) TrackerService.start(context)
    }
}
