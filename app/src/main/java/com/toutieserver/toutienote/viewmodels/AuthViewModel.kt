package com.toutieserver.toutienote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = ApiService.login(username, password)
                AuthRepository.saveSession(res.token, res.username)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur de connexion"
            }
        }
    }

    fun register(username: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = ApiService.register(username, password)
                AuthRepository.saveSession(res.token, res.username)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur d'inscription"
            }
        }
    }

    fun logout() {
        AuthRepository.clearSession()
    }
}
