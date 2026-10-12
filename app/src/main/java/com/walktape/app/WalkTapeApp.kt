package com.walktape.app

import android.app.Application

class WalkTapeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
