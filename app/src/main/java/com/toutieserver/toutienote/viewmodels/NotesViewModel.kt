package com.toutieserver.toutienote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotesViewModel : ViewModel() {

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _syncSuccess = MutableStateFlow(false)
    val syncSuccess: StateFlow<Boolean> = _syncSuccess

    private var syncJob: Job? = null

    init { loadNotes() }

    fun loadNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _notes.value = ApiService.getNotes()
            } catch (e: Exception) {
                _error.value = "Erreur connexion serveur"
            }
            _loading.value = false
        }
    }

    fun createNote(title: String, content: String, hidden: Boolean = false, onCreated: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val note = ApiService.createNote(title, content, hidden)
                if (!hidden) _notes.value = listOf(note) + _notes.value
                viewModelScope.launch(Dispatchers.Main) { onCreated(note) }
            } catch (e: Exception) {
                _error.value = "Erreur création note"
            }
        }
    }

    fun scheduleSync(id: String, title: String, content: String) {
        _syncSuccess.value = false
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1200)
            try {
                ApiService.updateNote(id, title, content)
                val updated = _notes.value.find { it.id == id }?.copy(title = title, content = content)
                val rest    = _notes.value.filter { it.id != id }
                _notes.value = if (updated != null) listOf(updated) + rest else _notes.value
                _syncSuccess.value = true
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun clearSyncSuccess() { _syncSuccess.value = false }

    fun deleteNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.deleteNote(id)
                _notes.value = _notes.value.filter { it.id != id }
            } catch (e: Exception) {
                _error.value = "Erreur suppression"
            }
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newVal = ApiService.toggleNoteFavorite(id)
                _notes.value = _notes.value.map {
                    if (it.id == id) it.copy(isFavorite = newVal) else it
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newVal = ApiService.toggleNotePin(id)
                _notes.value = _notes.value.map {
                    if (it.id == id) it.copy(isPinned = newVal) else it
                }
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun lockNote(id: String, pin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.lockNote(id, pin)
                _notes.value = _notes.value.map {
                    if (it.id == id) it.copy(isLocked = true) else it
                }
                viewModelScope.launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) { onError() }
            }
        }
    }

    fun unlockNote(id: String, pin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiService.unlockNote(id, pin)
            viewModelScope.launch(Dispatchers.Main) {
                if (ok) onSuccess() else onError()
            }
        }
    }

    fun removeNoteLock(id: String, pin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiService.removeNoteLock(id, pin)
            viewModelScope.launch(Dispatchers.Main) {
                if (ok) {
                    _notes.value = _notes.value.map {
                        if (it.id == id) it.copy(isLocked = false) else it
                    }
                    onSuccess()
                } else {
                    onError()
                }
            }
        }
    }

    fun renameNote(id: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val note = _notes.value.find { it.id == id } ?: return@launch
                ApiService.updateNote(id, newTitle, note.content)
                _notes.value = _notes.value.map {
                    if (it.id == id) it.copy(title = newTitle) else it
                }
            } catch (e: Exception) { /* silent */ }
        }
    }
}
