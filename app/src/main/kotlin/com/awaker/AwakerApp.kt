package com.awaker

import android.app.Application

class AwakerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
