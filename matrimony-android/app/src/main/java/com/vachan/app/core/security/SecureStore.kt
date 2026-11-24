package com.vachan.app.core.security

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

/**
 * Encrypted on-device storage.
 *
 * Data is serialised to JSON and sealed with AES-256-GCM (Tink). The Tink keyset is itself
 * encrypted by a master key that lives in the Android Keystore (hardware-backed where available),
 * so the file is useless if copied off the device. App data is also excluded from backups
 * (see `data_extraction_rules.xml`).
 */
class SecureStore(context: Context) {

    private val appContext = context.applicationContext

    val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    fun <T> dataStore(
        fileName: String,
        serializer: KSerializer<T>,
        defaultValue: T,
        scope: CoroutineScope,
    ): DataStore<T> = DataStoreFactory.create(
        serializer = EncryptedJsonSerializer(serializer, defaultValue, { aead }, associatedData = fileName.encodeToByteArray()),
        // If decryption fails (e.g. Keystore key was wiped after a device reset), start fresh rather than crash.
        corruptionHandler = ReplaceFileCorruptionHandler { defaultValue },
        scope = scope,
        produceFile = { appContext.dataStoreFile("$fileName.enc") },
    )

    private companion object {
        const val KEYSET_NAME = "vachan_master_keyset"
        const val KEYSET_PREFS = "vachan_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://vachan_master_key"
    }
}

class EncryptedJsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val aead: () -> Aead,
    private val associatedData: ByteArray,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            val plain = aead().decrypt(bytes, associatedData)
            json.decodeFromString(serializer, plain.decodeToString())
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Unable to decrypt secure store", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unable to parse secure store", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plain = json.encodeToString(serializer, t).encodeToByteArray()
        output.write(aead().encrypt(plain, associatedData))
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }
    }
}
