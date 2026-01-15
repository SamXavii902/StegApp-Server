package com.vamsi.stegapp.utils

import android.content.Context
import android.content.SharedPreferences

object UserPrefs {
    private const val PREF_NAME = "StegAppPrefs"
    private const val KEY_USERNAME = "username"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String? {
        return getPrefs(context).getString(KEY_USERNAME, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return !getUsername(context).isNullOrEmpty()
    }
    
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
