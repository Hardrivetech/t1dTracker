package com.hardrivetech.t1dtracker.data

import android.content.Context
import com.hardrivetech.t1dtracker.EncryptionUtil
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "t1d_prefs")

class PrefsRepository(private val context: Context) {
    private val keystoreOk = EncryptionUtil.isKeystoreUsable(context)
    private val ICR_KEY = stringPreferencesKey("default_icr")
    private val ISF_KEY = stringPreferencesKey("default_isf")
    private val TARGET_KEY = stringPreferencesKey("default_target")
    private val TELEMETRY_KEY = stringPreferencesKey("telemetry_consent")

    val defaultICR: Flow<Double> = context.dataStore.data.map { prefs ->
        val enc = prefs[ICR_KEY]
        if (keystoreOk) {
            val dec = EncryptionUtil.decryptString(context, enc)
            dec?.toDoubleOrNull() ?: 0.0
        } else {
            enc?.toDoubleOrNull() ?: 0.0
        }
    }

    val defaultISF: Flow<Double> = context.dataStore.data.map { prefs ->
        val enc = prefs[ISF_KEY]
        if (keystoreOk) {
            val dec = EncryptionUtil.decryptString(context, enc)
            dec?.toDoubleOrNull() ?: 0.0
        } else {
            enc?.toDoubleOrNull() ?: 0.0
        }
    }

    val defaultTarget: Flow<Double> = context.dataStore.data.map { prefs ->
        val enc = prefs[TARGET_KEY]
        if (keystoreOk) {
            val dec = EncryptionUtil.decryptString(context, enc)
            dec?.toDoubleOrNull() ?: 0.0
        } else {
            enc?.toDoubleOrNull() ?: 0.0
        }
    }
    
    val telemetryConsent: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val enc = prefs[TELEMETRY_KEY]
        if (keystoreOk) {
            val dec = EncryptionUtil.decryptString(context, enc)
            dec?.toBoolean() ?: false
        } else {
            enc?.toBoolean() ?: false
        }
    }
    
    private val BIOMETRIC_KEY = stringPreferencesKey("biometric_enabled")
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val enc = prefs[BIOMETRIC_KEY]
        if (keystoreOk) {
            val dec = EncryptionUtil.decryptString(context, enc)
            dec?.toBoolean() ?: false
        } else {
            enc?.toBoolean() ?: false
        }
    }

    suspend fun setDefaultICR(value: Double) {
        val toStore = if (keystoreOk) EncryptionUtil.encryptString(context, value.toString()) else value.toString()
        context.dataStore.edit { prefs ->
            prefs[ICR_KEY] = toStore
        }
    }

    suspend fun setDefaultTarget(value: Double) {
        val toStore = if (keystoreOk) EncryptionUtil.encryptString(context, value.toString()) else value.toString()
        context.dataStore.edit { prefs ->
            prefs[TARGET_KEY] = toStore
        }
    }

    suspend fun setDefaultISF(value: Double) {
        val toStore = if (keystoreOk) EncryptionUtil.encryptString(context, value.toString()) else value.toString()
        context.dataStore.edit { prefs ->
            prefs[ISF_KEY] = toStore
        }
    }

    suspend fun setTelemetryConsent(value: Boolean) {
        val toStore = if (keystoreOk) EncryptionUtil.encryptString(context, value.toString()) else value.toString()
        context.dataStore.edit { prefs ->
            prefs[TELEMETRY_KEY] = toStore
        }
    }
    
    suspend fun setBiometricEnabled(value: Boolean) {
        val toStore = if (keystoreOk) EncryptionUtil.encryptString(context, value.toString()) else value.toString()
        context.dataStore.edit { prefs ->
            prefs[BIOMETRIC_KEY] = toStore
        }
    }

}
