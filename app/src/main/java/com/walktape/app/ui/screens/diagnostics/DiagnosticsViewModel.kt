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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val collector: DiagnosticsCollector
) : ViewModel() {

    private val _sections = MutableStateFlow<List<DiagnosticsCollector.ReportSection>>(emptyList())
    val sections: StateFlow<List<DiagnosticsCollector.ReportSection>> = _sections.asStateFlow()

    /** Sections the user chose to include; connections stay opt-in only. */
    private val _included = MutableStateFlow(collector.defaultIncluded)
    val included: StateFlow<Set<DiagnosticsCollector.SectionId>> = _included.asStateFlow()

    private val _report = MutableStateFlow<String?>(null)
    val report: StateFlow<String?> = _report.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _sections.value = collector.buildSections()
            _report.value = render()
        }
    }

    /** Re-renders from the cached sections; no data is re-collected on toggle. */
    fun toggle(section: DiagnosticsCollector.SectionId) {
        _included.value = _included.value.toMutableSet().apply {
            if (!add(section)) remove(section)
        }
        _report.value = render()
    }

    private fun render(): String = collector.render(_sections.value, _included.value)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DiagnosticsViewModel(DiagnosticsCollector(Graph.app()))
            }
        }
    }
}
