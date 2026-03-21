package com.toutieserver.toutienote.data.auth

import android.content.Context
import android.content.SharedPreferences

object AuthRepository {
    private const val PREFS        = "auth_prefs"
    private const val KEY_TOKEN    = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_USER_ID  = "user_id"
    private const val KEY_CREATED  = "created_at"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun getToken():    String? = prefs?.getString(KEY_TOKEN,    null)
    fun getUsername(): String? = prefs?.getString(KEY_USERNAME, null)
    fun getUserId():   String? = prefs?.getString(KEY_USER_ID,  null)
    fun getCreatedAt(): String? = prefs?.getString(KEY_CREATED, null)

    fun saveSession(token: String, username: String, userId: String = "", createdAt: String = "") {
        prefs?.edit()?.apply {
            putString(KEY_TOKEN,    token)
            putString(KEY_USERNAME, username)
            putString(KEY_USER_ID,  userId)
            putString(KEY_CREATED,  createdAt)
            apply()
        }
    }

    fun clearSession() {
        prefs?.edit()?.apply {
            remove(KEY_TOKEN)
            remove(KEY_USERNAME)
            remove(KEY_USER_ID)
            remove(KEY_CREATED)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()
}
