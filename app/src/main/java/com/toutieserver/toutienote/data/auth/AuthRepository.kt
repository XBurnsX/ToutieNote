package com.toutieserver.toutienote.data.auth

import android.content.Context
import android.content.SharedPreferences

object AuthRepository {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun getToken(): String? = prefs?.getString(KEY_TOKEN, null)
    fun getUsername(): String? = prefs?.getString(KEY_USERNAME, null)

    fun saveSession(token: String, username: String) {
        prefs?.edit()?.apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            apply()
        }
    }

    fun clearSession() {
        prefs?.edit()?.apply {
            remove(KEY_TOKEN)
            remove(KEY_USERNAME)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()
}
