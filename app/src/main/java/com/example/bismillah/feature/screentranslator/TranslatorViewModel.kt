package com.example.bismillah.feature.screentranslator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bismillah.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** MVVM holder for the translator screen. Owns the controller lifecycle (UDF: state flows down, events flow up). */
class TranslatorViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = TranslatorController(app.applicationContext)

    private val _status = MutableStateFlow(app.getString(R.string.translator_status_idle))
    val status: StateFlow<String> = _status.asStateFlow()

    init {
        controller.onState = { _status.value = it }
    }

    fun start() = controller.start()
    fun runOnce() = controller.runOnce()
    fun stop() {
        controller.stop()
        _status.value = getApplication<Application>().getString(R.string.translator_status_stopped)
    }

    override fun onCleared() {
        controller.close()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TranslatorViewModel(app) as T
    }
}
