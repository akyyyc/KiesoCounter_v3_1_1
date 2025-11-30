package com.example.kiesocounter_v3_1_1

import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * FirebaseRepository - Firebase műveletek felhasználó azonosítással
 */
class FirebaseRepository {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://kiesocounter-default-rtdb.europe-west1.firebasedatabase.app"
    )
    private val workspacesRef: DatabaseReference = database.getReference("workspaces")

    // ═══════════════════════════════════════════════════════════════════
    // WORKSPACE MŰVELETEK
    // ═══════════════════════════════════════════════════════════════════

    suspend fun createWorkspace(workspaceName: String): Result<FirebaseWorkspace> {
        return try {
            val workspaceId = workspacesRef.push().key
                ?: return Result.failure(Exception("Nem sikerült ID-t generálni"))

            val inviteCode = generateInviteCode()

            val workspace = FirebaseWorkspace(
                id = workspaceId,
                name = workspaceName,
                createdAt = System.currentTimeMillis(),
                inviteCode = inviteCode
            )

            workspacesRef.child(workspaceId).setValue(workspace).await()
            Result.success(workspace)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinWorkspace(inviteCode: String): Result<FirebaseWorkspace> {
        return try {
            val query = workspacesRef
                .orderByChild("inviteCode")
                .equalTo(inviteCode)
                .get()
                .await()

            if (!query.exists()) {
                return Result.failure(Exception("Érvénytelen megosztási kód"))
            }

            val workspaceSnapshot = query.children.firstOrNull()
                ?: return Result.failure(Exception("Workspace nem található"))

            val workspace = workspaceSnapshot.getValue(FirebaseWorkspace::class.java)
                ?: return Result.failure(Exception("Hibás workspace adat"))

            Result.success(workspace)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENTRY MŰVELETEK - FELHASZNÁLÓ NÉVVEL
    // ═══════════════════════════════════════════════════════════════════

    suspend fun addEntry(
        workspaceId: String,
        entry: NumberEntry,
        createdBy: String,  // ← ÚJ PARAMÉTER!
        deviceId: String     // ← ÚJ PARAMÉTER!
    ) {
        try {
            android.util.Log.d("🔥 FIREBASE", "===== addEntry HÍVÁS =====")
            android.util.Log.d("🔥 FIREBASE", "Workspace ID: $workspaceId")
            android.util.Log.d("🔥 FIREBASE", "Entry value: ${entry.value}")
            android.util.Log.d("🔥 FIREBASE", "Created by: $createdBy")
            android.util.Log.d("🔥 FIREBASE", "Device ID: $deviceId")

            val firebaseEntry = FirebaseEntry(
                id = entry.id,
                value = entry.value,
                categoryName = entry.categoryName,
                subCategory = entry.subCategory,
                note = entry.note,
                timestamp = entry.timestamp.time,
                createdBy = createdBy,       // ← ÚJ!
                deviceId = deviceId,         // ← ÚJ!
                syncedAt = System.currentTimeMillis()
            )

            workspacesRef
                .child(workspaceId)
                .child("entries")
                .child(entry.id.toString())
                .setValue(firebaseEntry)
                .await()

            android.util.Log.d("🔥 FIREBASE", "✅✅✅ Firebase setValue() SIKERES ($createdBy)!")
        } catch (e: Exception) {
            android.util.Log.e("🔥 FIREBASE", "❌❌❌ Firebase HIBA: ${e.message}", e)
            throw e
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NAPI MEGJEGYZÉS MŰVELETEK - FELHASZNÁLÓ NÉVVEL
    // ═══════════════════════════════════════════════════════════════════

    suspend fun saveDailyNote(
        workspaceId: String,
        dailyNote: DailyNote,
        createdBy: String,   // ← ÚJ PARAMÉTER!
        deviceId: String     // ← ÚJ PARAMÉTER!
    ) {
        try {
            android.util.Log.d("🔥 FIREBASE_NOTE", "===== saveDailyNote HÍVÁS =====")
            android.util.Log.d("🔥 FIREBASE_NOTE", "Workspace ID: $workspaceId")
            android.util.Log.d("🔥 FIREBASE_NOTE", "Date: ${dailyNote.date}")
            android.util.Log.d("🔥 FIREBASE_NOTE", "Created by: $createdBy")

            val firebaseNote = FirebaseDailyNote(
                date = dailyNote.date,
                note = dailyNote.note,
                timestamp = dailyNote.timestamp.time,
                createdBy = createdBy,   // ← ÚJ!
                deviceId = deviceId      // ← ÚJ!
            )

            workspacesRef
                .child(workspaceId)
                .child("daily_notes")
                .child(dailyNote.date)
                .setValue(firebaseNote)
                .await()

            android.util.Log.d("🔥 FIREBASE_NOTE", "✅✅✅ Napi megjegyzés mentve ($createdBy)!")
        } catch (e: Exception) {
            android.util.Log.e("🔥 FIREBASE_NOTE", "❌❌❌ HIBA: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteDailyNote(workspaceId: String, date: String) {
        try {
            workspacesRef
                .child(workspaceId)
                .child("daily_notes")
                .child(date)
                .removeValue()
                .await()

            android.util.Log.d("🔥 FIREBASE_NOTE", "Napi megjegyzés törölve: $date")
        } catch (e: Exception) {
            android.util.Log.e("🔥 FIREBASE_NOTE", "Hiba törléskor: ${e.message}")
            throw e
        }
    }

    fun listenToDailyNotes(
        workspaceId: String,
        onNotesChanged: (Map<String, FirebaseDailyNote>) -> Unit
    ): ValueEventListener {
        val notesRef = workspacesRef.child(workspaceId).child("daily_notes")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notes = mutableMapOf<String, FirebaseDailyNote>()

                snapshot.children.forEach { noteSnapshot ->
                    val note = noteSnapshot.getValue(FirebaseDailyNote::class.java)
                    note?.let { notes[it.date] = it }
                }

                android.util.Log.d("🔥 FIREBASE_NOTE_LISTENER", "Napi megjegyzések: ${notes.size} db")

                onNotesChanged(notes)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("🔥 FIREBASE_NOTE_LISTENER", "Error: ${error.message}")
            }
        }

        notesRef.addValueEventListener(listener)
        return listener
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENTRY-K FIGYELÉSE
    // ═══════════════════════════════════════════════════════════════════

    fun listenToWorkspaceEntries(
        workspaceId: String,
        onEntriesChanged: (List<FirebaseEntry>) -> Unit
    ): ValueEventListener {
        val entriesRef = workspacesRef.child(workspaceId).child("entries")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<FirebaseEntry>()

                snapshot.children.forEach { entrySnapshot ->
                    val entry = entrySnapshot.getValue(FirebaseEntry::class.java)
                    entry?.let { entries.add(it) }
                }

                onEntriesChanged(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FIREBASE_LISTENER", "Error: ${error.message}")
            }
        }

        entriesRef.addValueEventListener(listener)
        return listener
    }

    // ═══════════════════════════════════════════════════════════════════
    // TÖRLÉS
    // ═══════════════════════════════════════════════════════════════════

    suspend fun deleteEntry(workspaceId: String, entryId: Long) {
        try {
            workspacesRef
                .child(workspaceId)
                .child("entries")
                .child(entryId.toString())
                .removeValue()
                .await()

            android.util.Log.d("FIREBASE_DELETE", "Entry deleted: $entryId")
        } catch (e: Exception) {
            android.util.Log.e("FIREBASE_DELETE", "Error deleting entry: ${e.message}")
            throw e
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LISTENER LEÁLLÍTÁS
    // ═══════════════════════════════════════════════════════════════════

    fun stopListeningToWorkspace(workspaceId: String, listener: ValueEventListener) {
        val entriesRef = workspacesRef.child(workspaceId).child("entries")
        entriesRef.removeEventListener(listener)
    }

    fun stopListeningToDailyNotes(workspaceId: String, listener: ValueEventListener) {
        val notesRef = workspacesRef.child(workspaceId).child("daily_notes")
        notesRef.removeEventListener(listener)
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════════

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}