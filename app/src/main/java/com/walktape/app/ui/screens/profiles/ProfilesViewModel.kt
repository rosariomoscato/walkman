package com.walktape.app.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.walktape.app.Graph
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileActionState(val busy: Boolean = false, val error: ApiError? = null, val success: Boolean = false)

class ProfilesViewModel : ViewModel() {
    private val repository = Graph.profiles
    val profiles = repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val mutableAction = MutableStateFlow(ProfileActionState())
    val action = mutableAction.asStateFlow()
    private val mutableRemoval = MutableStateFlow<Pair<ProfileEntity, Long>?>(null)
    val removal = mutableRemoval.asStateFlow()
    private val mutableShare = MutableStateFlow<ProfileEntity?>(null)
    val share = mutableShare.asStateFlow()

    fun shareQr(profile: ProfileEntity) { mutableShare.value = profile }
    fun dismissShare() { mutableShare.value = null }

    fun reset() { mutableAction.value = ProfileActionState() }

    fun add(name: String, url: String, username: String, password: String, http: Boolean, onSaved: () -> Unit) {
        if (mutableAction.value.busy) return
        viewModelScope.launch {
            mutableAction.value = ProfileActionState(busy = true)
            when (val result = repository.addVerified(name, url, username, password, http)) {
                is ApiResult.Failure -> mutableAction.value = ProfileActionState(error = result.error)
                is ApiResult.Success -> { mutableAction.value = ProfileActionState(success = true); onSaved() }
            }
        }
    }

    fun verify(id: Long) {
        if (mutableAction.value.busy) return
        viewModelScope.launch {
            mutableAction.value = ProfileActionState(busy = true)
            mutableAction.value = when (val result = repository.verify(id)) {
                is ApiResult.Failure -> ProfileActionState(error = result.error)
                is ApiResult.Success -> ProfileActionState(success = true)
            }
        }
    }

    fun activate(id: Long) {
        if (mutableAction.value.busy) return
        viewModelScope.launch {
            mutableAction.value = ProfileActionState(error = if (repository.activate(id)) null else ApiError.CredentialsUnavailable)
        }
    }

    fun confirmRemoval(profile: ProfileEntity) {
        viewModelScope.launch { mutableRemoval.value = profile to repository.localBytes(profile.id) }
    }
    fun cancelRemoval() { mutableRemoval.value = null }
    fun remove() {
        val profile = mutableRemoval.value?.first ?: return
        viewModelScope.launch {
            mutableAction.value = ProfileActionState(busy = true)
            val removed = repository.remove(profile.id)
            mutableRemoval.value = null
            mutableAction.value = ProfileActionState(error = if (removed) null else ApiError.LocalStorage)
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfilesViewModel() as T
        }
    }
}
