package com.example.bismillah.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.setupStore by preferencesDataStore(name = "setup")

object SetupPrefs {
    private val KEY_DONE = booleanPreferencesKey("setup_completed")

    fun isDone(ctx: Context): Flow<Boolean> =
        ctx.applicationContext.setupStore.data.map { it[KEY_DONE] == true }

    suspend fun setDone(ctx: Context, done: Boolean = true) {
        ctx.applicationContext.setupStore.edit { it[KEY_DONE] = done }
    }
}
