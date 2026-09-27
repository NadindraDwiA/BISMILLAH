package com.example.bismillah.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bismillah.App
import com.example.bismillah.data.ModelRepository
import com.example.bismillah.data.SetupPrefs
import com.example.bismillah.data.preference.ApiKeyStore
import kotlinx.coroutines.launch

class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: ModelRepository = (app as App).container.modelRepository

    fun hasTranslatorBackend(ctx: android.content.Context): Boolean =
        ApiKeyStore.effectiveKey(ctx).isNotBlank()

    suspend fun ensurePaddle(ctx: android.content.Context): Boolean =
        repo.ensurePaddle()

    fun markDone(ctx: android.content.Context, onDone: () -> Unit) {
        viewModelScope.launch {
            SetupPrefs.setDone(ctx.applicationContext, true)
            onDone()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SetupViewModel(app) as T
    }
}
