package com.example.kiesocounter_v3_1_1

import android.content.Context
import android.content.SharedPreferences

/**
 * WorkspaceManager - Kezeli a workspace ÉS felhasználó mentését/betöltését
 */
class WorkspaceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "workspace_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WORKSPACE_ID = "workspace_id"
        private const val KEY_WORKSPACE_NAME = "workspace_name"
        private const val KEY_INVITE_CODE = "invite_code"

        // ÚJ: FELHASZNÁLÓ AZONOSÍTÁS
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_DEVICE_ID = "device_id"
    }

    // ═══════════════════════════════════════════════════════════
    // WORKSPACE KEZELÉS
    // ═══════════════════════════════════════════════════════════

    fun saveWorkspace(workspace: FirebaseWorkspace) {
        prefs.edit().apply {
            putString(KEY_WORKSPACE_ID, workspace.id)
            putString(KEY_WORKSPACE_NAME, workspace.name)
            putString(KEY_INVITE_CODE, workspace.inviteCode)
            apply()
        }
    }

    fun loadWorkspace(): FirebaseWorkspace? {
        val id = prefs.getString(KEY_WORKSPACE_ID, null) ?: return null
        val name = prefs.getString(KEY_WORKSPACE_NAME, null) ?: return null
        val inviteCode = prefs.getString(KEY_INVITE_CODE, null) ?: return null

        return FirebaseWorkspace(
            id = id,
            name = name,
            inviteCode = inviteCode
        )
    }

    fun hasWorkspace(): Boolean {
        return prefs.contains(KEY_WORKSPACE_ID)
    }

    fun clearWorkspace() {
        prefs.edit().apply {
            remove(KEY_WORKSPACE_ID)
            remove(KEY_WORKSPACE_NAME)
            remove(KEY_INVITE_CODE)
            apply()
        }
    }

    fun getWorkspaceId(): String? {
        return prefs.getString(KEY_WORKSPACE_ID, null)
    }

    // ═══════════════════════════════════════════════════════════
    // ÚJ: FELHASZNÁLÓ KEZELÉS
    // ═══════════════════════════════════════════════════════════

    /**
     * Felhasználó név mentése
     * @param userName "rgazda" vagy "Felhasznalo"
     */
    fun saveUserName(userName: String) {
        prefs.edit().apply {
            putString(KEY_USER_NAME, userName)
            apply()
        }
        android.util.Log.d("WORKSPACE_MANAGER", "Felhasználó mentve: $userName")
    }

    /**
     * Felhasználó név lekérése
     * @return Mentett felhasználó név, vagy null ha még nincs
     */
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    /**
     * Van-e beállított felhasználó?
     */
    fun hasUserName(): Boolean {
        return prefs.contains(KEY_USER_NAME)
    }

    /**
     * Device ID generálása és mentése (egyszer, első indításkor)
     */
    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = "device_${System.currentTimeMillis()}_${(0..9999).random()}"
            prefs.edit().apply {
                putString(KEY_DEVICE_ID, deviceId)
                apply()
            }
            android.util.Log.d("WORKSPACE_MANAGER", "Device ID generálva: $deviceId")
        }

        return deviceId
    }

    /**
     * Felhasználó törlése
     */
    fun clearUserName() {
        prefs.edit().apply {
            remove(KEY_USER_NAME)
            apply()
        }
    }
}