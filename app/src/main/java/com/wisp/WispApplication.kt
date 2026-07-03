package com.wisp

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/** Process entry point; hosts the Hilt object graph. */
@HiltAndroidApp
class WispApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WispApplication onCreate")
    }

    private companion object {
        const val TAG = "WispApplication"
    }
}
