package com.example.kiesocounter_v3_1_1

import java.util.Date

// ═══════════════════════════════════════════════════════════════════
// FIREBASE DATA MODELS
// ═══════════════════════════════════════════════════════════════════

/**
 * Workspace (munkacsapat) egy megosztott munkaterület több felhasználó számára
 */
data class FirebaseWorkspace(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val members: Map<String, WorkspaceMember> = emptyMap(),
    val inviteCode: String = ""
)

/**
 * Workspace tag (egyszerűsített verzió Firebase-hez)
 */
data class WorkspaceMember(
    val userId: String = "",
    val joinedAt: Long = System.currentTimeMillis(),
    val role: String = "member" // "owner", "admin", "member"
)

/**
 * Szinkronizált bejegyzés Firebase-ben
 */
data class FirebaseEntry(
    val id: Long = 0,
    val value: Int = 0,
    val categoryName: String = "",
    val subCategory: String? = null,  // ← ÚJ MEZŐ!
    val note: String? = null,  // ← ÚJ MEZŐ!
    val timestamp: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val deviceId: String = "",
    val syncedAt: Long = System.currentTimeMillis()
)

/**
 * ÚJ: Napi megjegyzés Firebase-ben
 */
data class FirebaseDailyNote(
    val date: String = "",  // "2025-11-30" formátum
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val deviceId: String = ""
)

/**
 * Megosztási kód generálás
 */
data class InviteCode(
    val code: String = "",
    val workspaceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000), // 7 nap
    val usedBy: List<String> = emptyList(),
    val maxUses: Int = 10
)

/**
 * Helper: NumberEntry -> FirebaseEntry konverzió
 */
fun NumberEntry.toFirebaseEntry(userId: String, deviceId: String): FirebaseEntry {
    return FirebaseEntry(
        id = this.id,
        value = this.value,
        categoryName = this.categoryName,
        subCategory = this.subCategory,  // ← ÚJ!
        note = this.note,  // ← ÚJ!
        timestamp = this.timestamp.time,
        createdBy = userId,
        deviceId = deviceId,
        syncedAt = System.currentTimeMillis()
    )
}

/**
 * Helper: FirebaseEntry -> NumberEntry konverzió
 */
fun FirebaseEntry.toNumberEntry(): NumberEntry {
    return NumberEntry(
        id = this.id,
        value = this.value,
        categoryName = this.categoryName,
        subCategory = this.subCategory,  // ← ÚJ!
        note = this.note,  // ← ÚJ!
        timestamp = Date(this.timestamp)
    )
}

/**
 * ÚJ: Helper - DailyNote -> FirebaseDailyNote konverzió
 */
fun DailyNote.toFirebaseDailyNote(userId: String, deviceId: String): FirebaseDailyNote {
    return FirebaseDailyNote(
        date = this.date,
        note = this.note,
        timestamp = this.timestamp.time,
        createdBy = userId,
        deviceId = deviceId
    )
}

/**
 * ÚJ: Helper - FirebaseDailyNote -> DailyNote konverzió
 */
fun FirebaseDailyNote.toDailyNote(): DailyNote {
    return DailyNote(
        date = this.date,
        note = this.note,
        timestamp = Date(this.timestamp)
    )
}
