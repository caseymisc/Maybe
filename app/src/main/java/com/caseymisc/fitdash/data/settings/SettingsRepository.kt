package com.caseymisc.fitdash.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class WeightUnit { KG, LB }

data class Settings(
    val hevyApiKey: String = "",
    val weightUnit: WeightUnit = WeightUnit.KG,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val hevyKey = stringPreferencesKey("hevy_api_key")
    private val unitKey = stringPreferencesKey("weight_unit")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            hevyApiKey = prefs[hevyKey].orEmpty(),
            weightUnit = prefs[unitKey]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
                ?: WeightUnit.KG,
        )
    }

    suspend fun setHevyApiKey(key: String) {
        context.dataStore.edit { it[hevyKey] = key.trim() }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        context.dataStore.edit { it[unitKey] = unit.name }
    }
}
