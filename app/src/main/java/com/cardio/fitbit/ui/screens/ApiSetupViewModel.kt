package com.cardio.fitbit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApiSetupViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow("")
    val clientSecret: StateFlow<String> = _clientSecret.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun onClientIdChange(newValue: String) {
        _clientId.value = newValue
    }

    fun onClientSecretChange(newValue: String) {
        _clientSecret.value = newValue
    }

    fun saveCredentials() {
        if (_clientId.value.isNotBlank() && _clientSecret.value.isNotBlank()) {
            viewModelScope.launch {
                userPreferencesRepository.setApiCredentials(_clientId.value.trim(), _clientSecret.value.trim())
                _isSaved.value = true
            }
        }
    }
}
