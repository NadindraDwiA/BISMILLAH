package com.example.bismillah.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bismillah.App
import com.example.bismillah.data.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelManagerUiState(
    val ready: Boolean = false,
    val files: List<Pair<String, Boolean>> = emptyList(),
    val dictReady: Boolean = false,
    val message: String = "",
    val working: Boolean = false
)

class ModelManagerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: ModelRepository = (app as App).container.modelRepository

    private val _ui = MutableStateFlow(ModelManagerUiState())
    val ui: StateFlow<ModelManagerUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        val s = repo.paddleStatus()
        _ui.update {
            it.copy(ready = s.ready, files = s.files, dictReady = s.dictReady)
        }
    }

    fun copyFromAssets() {
        if (_ui.value.working) return
        _ui.update { it.copy(working = true, message = "Menyalin dari assets...") }
        viewModelScope.launch {
            val ok = repo.ensurePaddle()
            _ui.update {
                it.copy(
                    working = false,
                    message = if (ok) "Model OCR siap." else "det/rec belum ada di assets/paddle/."
                )
            }
            refresh()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelManagerViewModel(app) as T
    }
}
