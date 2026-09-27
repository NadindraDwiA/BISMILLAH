package com.example.bismillah

import android.app.Application
import ai.onnxruntime.OrtEnvironment
import com.example.bismillah.di.AppContainer

class App : Application() {
    var ortEnv: OrtEnvironment? = null
        private set

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        try {
            ortEnv = OrtEnvironment.getEnvironment()
        } catch (_: Exception) {
            ortEnv = null
        }
    }

    override fun onTerminate() {
        try { ortEnv?.close() } catch (_: Exception) {}
        super.onTerminate()
    }
}
