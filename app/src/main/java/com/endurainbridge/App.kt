package com.endurainbridge

import android.app.Application
import com.endurainbridge.upload.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }
}
