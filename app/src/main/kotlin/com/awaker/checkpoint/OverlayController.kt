package com.awaker.checkpoint

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 다른 앱 위 SYSTEM_ALERT_WINDOW 바텀 시트 (이슈 05, ADR-0012의 결정타 표면).
 * 창 높이 = 화면 높이 × 가림 비율 — 시트 밖 영역은 창 자체가 없어서 터치가
 * 후보 앱으로 그대로 간다 (상단 30% 항상 노출, ADR-0003).
 *
 * 반드시 메인 스레드에서 호출할 것.
 */
class OverlayController(private val context: Context) {

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean
        get() = view != null

    fun show(heightFraction: Float, content: @Composable () -> Unit) {
        hide()
        val windowManager = context.getSystemService(WindowManager::class.java)
        val heightPx = (context.resources.displayMetrics.heightPixels * heightFraction).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        val overlayOwner = OverlayOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setContent(content)
        }

        windowManager.addView(composeView, params)
        overlayOwner.resume()
        view = composeView
        owner = overlayOwner
    }

    fun hide() {
        val current = view ?: return
        runCatching { context.getSystemService(WindowManager::class.java).removeView(current) }
        owner?.destroy()
        view = null
        owner = null
    }

    /** ComposeView가 오버레이 창에서 살 수 있게 하는 최소 수명 소유자. */
    private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        init {
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
    }
}
