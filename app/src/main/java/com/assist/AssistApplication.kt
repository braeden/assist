package com.assist

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/** Process entry point; hosts the Hilt object graph. */
@HiltAndroidApp
class AssistApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AssistApplication onCreate")
    }

    private companion object {
        const val TAG = "AssistApplication"
    }
}
