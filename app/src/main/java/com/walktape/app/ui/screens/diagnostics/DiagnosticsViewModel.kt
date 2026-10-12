package com.walktape.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walktape.app.Graph
import com.walktape.app.core.diagnostics.DiagnosticsCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val collector: DiagnosticsCollector
) : ViewModel() {

    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _report.value = collector.buildReport()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DiagnosticsViewModel(DiagnosticsCollector(Graph.app()))
            }
        }
    }
}
