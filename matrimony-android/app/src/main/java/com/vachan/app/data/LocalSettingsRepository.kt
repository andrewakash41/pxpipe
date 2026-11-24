package com.vachan.app.data

import androidx.datastore.core.DataStore
import com.vachan.app.core.model.AppSettings
import com.vachan.app.core.security.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/** App settings and consents, stored encrypted on the device. */
class LocalSettingsRepository(secureStore: SecureStore, scope: CoroutineScope) : SettingsRepository {

    private val store: DataStore<AppSettings> =
        secureStore.dataStore("settings", AppSettings.serializer(), AppSettings(), scope)

    // Read once synchronously at startup so the lock screen and routing don't flash the wrong state.
    private val initial: AppSettings = runBlocking { store.data.first() }

    override val settings: StateFlow<AppSettings> = store.data.stateIn(scope, SharingStarted.Eagerly, initial)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData(transform)
    }
}
