package com.toutieserver.toutienote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toutieserver.toutienote.data.api.ApiService
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.data.models.TagSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotesViewModel : ViewModel() {

    private fun visibleNotes(notes: List<Note>): List<Note> =
        notes.filterNot { it.isHidden }

    private fun extractTags(title: String, content: String): List<String> {
        val regex = Regex("""(?<!\w)#([0-9A-Za-zÀ-ÿ_-]{1,32})""")
        return regex.findAll("$title $content")
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .sorted()
            .toList()
    }

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _syncSuccess = MutableStateFlow(false)
    val syncSuccess: StateFlow<Boolean> = _syncSuccess

    private val _tags = MutableStateFlow<List<TagSummary>>(emptyList())
    val tags: StateFlow<List<TagSummary>> = _tags

    private var syncJob: Job? = null

    init { loadNotes() }

    fun loadNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                _notes.value = visibleNotes(ApiService.getNotes())
                _tags.value = ApiService.getTags()
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
                if (!hidden) {
                    _notes.value = visibleNotes(listOf(note.copy(tags = extractTags(note.title, note.content))) + _notes.value)
                    _tags.value = ApiService.getTags()
                }
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
                val updated = _notes.value.find { it.id == id }?.copy(
                    title = title,
                    content = content,
                    tags = extractTags(title, content),
                )
                val rest = _notes.value.filter { it.id != id }
                _notes.value = if (updated != null) visibleNotes(listOf(updated) + rest) else visibleNotes(_notes.value)
                _tags.value = ApiService.getTags()
                _syncSuccess.value = true
            } catch (_: Exception) {
            }
        }
    }

    fun clearSyncSuccess() { _syncSuccess.value = false }

    fun deleteNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiService.deleteNote(id)
                _notes.value = _notes.value.filter { it.id != id }
                _tags.value = ApiService.getTags()
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
            } catch (_: Exception) {
            }
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newVal = ApiService.toggleNotePin(id)
                _notes.value = _notes.value.map {
                    if (it.id == id) it.copy(isPinned = newVal) else it
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateNoteColor(id: String, colorTag: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _notes.value = _notes.value.map {
                if (it.id == id) it.copy(colorTag = colorTag) else it
            }
            try {
                val updated = ApiService.updateNoteColor(id, colorTag)
                _notes.value = _notes.value.map {
                    if (it.id == id) it.copy(colorTag = updated.colorTag, updatedAt = updated.updatedAt) else it
                }
            } catch (_: Exception) {
            }
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
            } catch (_: Exception) {
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
                _notes.value = visibleNotes(_notes.value.map {
                    if (it.id == id) it.copy(title = newTitle, tags = extractTags(newTitle, it.content)) else it
                })
                _tags.value = ApiService.getTags()
            } catch (_: Exception) {
            }
        }
    }

    fun ensureHiddenNote(
        title: String,
        onDone: (Note) -> Unit = {},
        onError: (Throwable?) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val existing = ApiService.getNoteByTitle(title, includeHidden = true)
                existing ?: ApiService.createNote(title, "", hidden = true)
            }.onSuccess { result ->
                _notes.value = visibleNotes(
                    _notes.value.map { note ->
                        if (note.id == result.id) result else note
                    }
                )
                viewModelScope.launch(Dispatchers.Main) { onDone(result) }
            }.onFailure { error ->
                viewModelScope.launch(Dispatchers.Main) { onError(error) }
            }
        }
    }

    fun searchNotes(query: String, includeHidden: Boolean = true, onResult: (List<Note>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = ApiService.searchNotes(query, includeHidden)
                viewModelScope.launch(Dispatchers.Main) { onResult(results) }
            } catch (_: Exception) {
                viewModelScope.launch(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    fun loadBacklinks(noteId: String, onResult: (List<Note>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = ApiService.getBacklinks(noteId)
                viewModelScope.launch(Dispatchers.Main) { onResult(results) }
            } catch (_: Exception) {
                viewModelScope.launch(Dispatchers.Main) { onResult(emptyList()) }
            }
        }
    }

    fun refreshTags() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _tags.value = ApiService.getTags()
            } catch (_: Exception) {
            }
        }
    }

    fun updateNoteTags(id: String, tags: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedTags = ApiService.updateNoteTags(id, tags)
                _notes.value = _notes.value.map { note ->
                    if (note.id == id) note.copy(tags = updatedTags) else note
                }
                _tags.value = ApiService.getTags()
            } catch (_: Exception) {
            }
        }
    }
}
