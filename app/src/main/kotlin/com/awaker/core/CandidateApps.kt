package com.awaker.core

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * 후보 앱 셋 해석 (이슈 08). 브라우저는 종류가 많고 사용자마다 달라 하드코딩
 * 대신 OS에 묻는다: `https` VIEW 인텐트를 범용으로 처리하는 앱 = 브라우저.
 * 프로브 URL이 범용 호스트라 도메인 특정 App Link 앱(예: YouTube)은 매칭되지
 * 않는다. 매니페스트 `<queries>` 선언이 전제 (Android 11+ 패키지 가시성).
 */
object CandidateApps {

    /** 기본 셋(피드 앱) + 설치된 브라우저. 서비스 시작 시 1회 해석. */
    fun resolve(pm: PackageManager): Set<String> =
        Tunables.DEFAULT_CANDIDATE_PACKAGES + browserPackages(pm)

    fun browserPackages(pm: PackageManager): Set<String> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }
}
