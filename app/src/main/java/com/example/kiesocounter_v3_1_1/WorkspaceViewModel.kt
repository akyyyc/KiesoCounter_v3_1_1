package com.example.kiesocounter_v3_1_1

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.google.firebase.database.ValueEventListener

/**
 * WorkspaceViewModel - Workspace + Felhasználó kezelés
 */
class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository()
    private val workspaceManager = WorkspaceManager(application)

    // ═══════════════════════════════════════════════════════════
    // FIREBASE LISTENER-EK
    // ═══════════════════════════════════════════════════════════

    private var currentEntriesListener: ValueEventListener? = null
    private var currentNotesListener: ValueEventListener? = null
    private var currentListeningWorkspaceId: String? = null

    // Firebase entry-k StateFlow-ja
    private val _firebaseEntries = MutableStateFlow<List<FirebaseEntry>>(emptyList())
    val firebaseEntries: StateFlow<List<FirebaseEntry>> = _firebaseEntries.asStateFlow()

    // Firebase napi megjegyzések StateFlow-ja
    private val _firebaseNotes = MutableStateFlow<Map<String, FirebaseDailyNote>>(emptyMap())
    val firebaseNotes: StateFlow<Map<String, FirebaseDailyNote>> = _firebaseNotes.asStateFlow()

    private val _currentWorkspace = MutableStateFlow<FirebaseWorkspace?>(null)
    val currentWorkspace: StateFlow<FirebaseWorkspace?> = _currentWorkspace.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    // ÚJ: FELHASZNÁLÓ ÁLLAPOT
    // ═══════════════════════════════════════════════════════════

    private val _currentUserName = MutableStateFlow<String?>(null)
    val currentUserName: StateFlow<String?> = _currentUserName.asStateFlow()

    private val _currentDeviceId = MutableStateFlow<String>("")
    val currentDeviceId: StateFlow<String> = _currentDeviceId.asStateFlow()

    // Loading állapot
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Hibaüzenet
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════
    // INIT
    // ═══════════════════════════════════════════════════════════════════

    init {
        // Workspace betöltése
        val savedWorkspace = workspaceManager.loadWorkspace()
        if (savedWorkspace != null) {
            _currentWorkspace.value = savedWorkspace
            startListeningToWorkspace(savedWorkspace.id)
        }

        // ═══════════════════════════════════════════════════════════
        // ÚJ: FELHASZNÁLÓ BETÖLTÉSE
        // ═══════════════════════════════════════════════════════════
        _currentUserName.value = workspaceManager.getUserName()
        _currentDeviceId.value = workspaceManager.getOrCreateDeviceId()

        android.util.Log.d("WORKSPACE_VM", "Init - User: ${_currentUserName.value}, Device: ${_currentDeviceId.value}")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ÚJ: FELHASZNÁLÓ KEZELÉS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Felhasználó név beállítása (első indításkor)
     */
    fun setUserName(userName: String) {
        workspaceManager.saveUserName(userName)
        _currentUserName.value = userName
        android.util.Log.d("WORKSPACE_VM", "Felhasználó beállítva: $userName")
    }

    /**
     * Van-e beállított felhasználó?
     */
    fun hasUserName(): Boolean {
        return workspaceManager.hasUserName()
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORKSPACE FUNKCIÓK
    // ═══════════════════════════════════════════════════════════════════

    fun createWorkspace(workspaceName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.createWorkspace(workspaceName)

            result.onSuccess { workspace ->
                _currentWorkspace.value = workspace
                workspaceManager.saveWorkspace(workspace)
                startListeningToWorkspace(workspace.id)
            }.onFailure { error ->
                _errorMessage.value = "Hiba: ${error.message}"
            }

            _isLoading.value = false
        }
    }

    fun joinWorkspace(inviteCode: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.joinWorkspace(inviteCode.uppercase())

            result.onSuccess { workspace ->
                _currentWorkspace.value = workspace
                workspaceManager.saveWorkspace(workspace)
                startListeningToWorkspace(workspace.id)
            }.onFailure { error ->
                _errorMessage.value = "Csatlakozás sikertelen: ${error.message}"
            }

            _isLoading.value = false
        }
    }

    fun leaveWorkspace() {
        stopListening()
        _currentWorkspace.value = null
        workspaceManager.clearWorkspace()
    }

    fun getInviteCode(): String? {
        return _currentWorkspace.value?.inviteCode
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENTRY SZINKRONIZÁLÁS - FELHASZNÁLÓ NÉVVEL
    // ═══════════════════════════════════════════════════════════════════

    fun syncEntryToFirebase(entry: NumberEntry) {
        android.util.Log.d("🔥 SYNC", "===== syncEntryToFirebase HÍVÁS =====")

        val workspace = _currentWorkspace.value
        val userName = _currentUserName.value
        val deviceId = _currentDeviceId.value

        if (workspace == null) {
            android.util.Log.e("🔥 SYNC", "❌ HIBA: Nincs workspace!")
            return
        }

        // ═══════════════════════════════════════════════════════════
        // JAVÍTÁS: HA NINCS FELHASZNÁLÓ NÉV, HASZNÁLJUNK DEFAULT-OT!
        // ═══════════════════════════════════════════════════════════
        val effectiveUserName = userName ?: "Unknown"  // ← ÚJ!
        val effectiveDeviceId = deviceId.ifEmpty { "device_unknown" }  // ← ÚJ!

        android.util.Log.d("🔥 SYNC", "✅ Workspace ID: ${workspace.id}")
        android.util.Log.d("🔥 SYNC", "✅ Felhasználó: $effectiveUserName")
        android.util.Log.d("🔥 SYNC", "✅ Device ID: $effectiveDeviceId")
        android.util.Log.d("🔥 SYNC", "Entry value: ${entry.value}")

        viewModelScope.launch {
            try {
                repository.addEntry(workspace.id, entry, effectiveUserName, effectiveDeviceId)  // ← JAVÍTVA!
                android.util.Log.d("🔥 SYNC", "✅✅✅ Entry mentve Firebase-be!")
            } catch (e: Exception) {
                android.util.Log.e("🔥 SYNC", "❌❌❌ HIBA: ${e.message}", e)
            }
        }
    }

    /**
     * NAPI MEGJEGYZÉS SZINKRONIZÁLÁSA - FELHASZNÁLÓ NÉVVEL
     */
    fun syncDailyNoteToFirebase(dailyNote: DailyNote) {
        android.util.Log.d("🔥 SYNC_NOTE", "===== syncDailyNoteToFirebase HÍVÁS =====")

        val workspace = _currentWorkspace.value
        val userName = _currentUserName.value
        val deviceId = _currentDeviceId.value

        if (workspace == null) {
            android.util.Log.e("🔥 SYNC_NOTE", "❌ HIBA: Nincs workspace!")
            return
        }

        // ═══════════════════════════════════════════════════════════
        // JAVÍTÁS: HA NINCS FELHASZNÁLÓ NÉV, HASZNÁLJUNK DEFAULT-OT!
        // ═══════════════════════════════════════════════════════════
        val effectiveUserName = userName ?: "Unknown"  // ← ÚJ!
        val effectiveDeviceId = deviceId.ifEmpty { "device_unknown" }  // ← ÚJ!

        android.util.Log.d("🔥 SYNC_NOTE", "✅ Workspace ID: ${workspace.id}")
        android.util.Log.d("🔥 SYNC_NOTE", "✅ Felhasználó: $effectiveUserName")
        android.util.Log.d("🔥 SYNC_NOTE", "Note date: ${dailyNote.date}")

        viewModelScope.launch {
            try {
                if (dailyNote.note.isBlank()) {
                    repository.deleteDailyNote(workspace.id, dailyNote.date)
                    android.util.Log.d("🔥 SYNC_NOTE", "✅✅✅ Megjegyzés törölve!")
                } else {
                    repository.saveDailyNote(workspace.id, dailyNote, effectiveUserName, effectiveDeviceId)  // ← JAVÍTVA!
                    android.util.Log.d("🔥 SYNC_NOTE", "✅✅✅ Megjegyzés mentve ($effectiveUserName)!")
                }
            } catch (e: Exception) {
                android.util.Log.e("🔥 SYNC_NOTE", "❌❌❌ HIBA: ${e.message}", e)
            }
        }
    }


    // ═══════════════════════════════════════════════════════════════════
    // VALÓS IDEJŰ FIGYELÉS
    // ═══════════════════════════════════════════════════════════════════

    fun startListeningToWorkspace(workspaceId: String) {
        val entriesListener = currentEntriesListener
        val notesListener = currentNotesListener
        val previousWorkspaceId = currentListeningWorkspaceId

        if (previousWorkspaceId != null) {
            entriesListener?.let { repository.stopListeningToWorkspace(previousWorkspaceId, it) }
            notesListener?.let { repository.stopListeningToDailyNotes(previousWorkspaceId, it) }
        }

        // Entry-k figyelése
        currentEntriesListener = repository.listenToWorkspaceEntries(workspaceId) { entries ->
            _firebaseEntries.value = entries
            android.util.Log.d("WORKSPACE_SYNC", "Entries: ${entries.size} db")
        }

        // Napi megjegyzések figyelése
        currentNotesListener = repository.listenToDailyNotes(workspaceId) { notes ->
            _firebaseNotes.value = notes
            android.util.Log.d("WORKSPACE_SYNC", "Notes: ${notes.size} db")
        }

        currentListeningWorkspaceId = workspaceId
    }

    fun stopListening() {
        val entriesListener = currentEntriesListener
        val notesListener = currentNotesListener
        val workspaceId = currentListeningWorkspaceId

        if (workspaceId != null) {
            entriesListener?.let { repository.stopListeningToWorkspace(workspaceId, it) }
            notesListener?.let { repository.stopListeningToDailyNotes(workspaceId, it) }

            currentEntriesListener = null
            currentNotesListener = null
            currentListeningWorkspaceId = null
            _firebaseEntries.value = emptyList()
            _firebaseNotes.value = emptyMap()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    fun deleteEntryFromFirebase(entryId: Long) {
        val workspace = _currentWorkspace.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteEntry(workspace.id, entryId)
            } catch (e: Exception) {
                android.util.Log.e("WORKSPACE_DELETE", "Error: ${e.message}")
            }
        }
    }
}