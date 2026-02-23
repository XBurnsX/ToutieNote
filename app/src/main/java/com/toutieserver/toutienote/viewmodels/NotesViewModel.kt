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

    /** Émis quand une synchro (updateNote) a réussi. L'écran peut remettre synced = true. */
    private val _syncSuccess = MutableStateFlow(false)
    val syncSuccess: StateFlow<Boolean> = _syncSuccess

    private var syncJob: Job? = null

    init { loadNotes() }

    fun loadNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val list = ApiService.getNotes().sortedByDescending { it.updatedAt }
                _notes.value = list
            } catch (e: Exception) {
                _error.value = "Erreur connexion serveur"
            }
            _loading.value = false
        }
    }

    fun createNote(title: String, content: String, onCreated: (Note) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val note = ApiService.createNote(title, content)
                _notes.value = listOf(note) + _notes.value
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
                val updatedNote = _notes.value.find { it.id == id }?.copy(title = title, content = content)
                val rest = _notes.value.filter { it.id != id }
                _notes.value = if (updatedNote != null) listOf(updatedNote) + rest else _notes.value
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
}
