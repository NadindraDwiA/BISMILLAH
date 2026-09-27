package com.example.bismillah.di

import android.content.Context
import com.example.bismillah.data.ModelRepository

/** Manual DI container (no reflection). Owned by [com.example.bismillah.App]. */
class AppContainer(appContext: Context) {
    private val ctx = appContext.applicationContext
    val modelRepository: ModelRepository by lazy { ModelRepository(ctx) }
}
