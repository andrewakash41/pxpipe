package com.vachan.app.di

import android.content.Context
import com.vachan.app.BuildConfig
import com.vachan.app.core.security.AppLockManager
import com.vachan.app.core.security.SecureStore
import com.vachan.app.data.AiRepository
import com.vachan.app.data.AuthRepository
import com.vachan.app.data.Backend
import com.vachan.app.data.ChatRepository
import com.vachan.app.data.FamilyRepository
import com.vachan.app.data.InterestRepository
import com.vachan.app.data.LocalSettingsRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.PrivacyRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SafetyRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.data.VerificationRepository
import com.vachan.app.data.demo.DemoBackend
import com.vachan.app.data.firebase.FirebaseBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun secureStore(@ApplicationContext context: Context): SecureStore = SecureStore(context)

    @Provides
    @Singleton
    fun settingsRepository(store: SecureStore, scope: CoroutineScope): SettingsRepository =
        LocalSettingsRepository(store, scope)

    @Provides
    @Singleton
    fun appLock(settings: SettingsRepository): AppLockManager = AppLockManager { settings.settings.value }

    @Provides
    @Singleton
    fun demoBackend(store: SecureStore, scope: CoroutineScope, settings: SettingsRepository): DemoBackend =
        DemoBackend(store, scope, settings)

    @Provides
    @Singleton
    fun firebaseBackend(@ApplicationContext context: Context, scope: CoroutineScope, settings: SettingsRepository): FirebaseBackend =
        FirebaseBackend(context, scope, settings)

    /** Firebase when the app was built with a google-services.json; otherwise the on-device Demo backend. */
    @Provides
    @Singleton
    fun backend(demo: Provider<DemoBackend>, firebase: Provider<FirebaseBackend>): Backend =
        if (BuildConfig.FIREBASE_ENABLED) firebase.get() else demo.get()

    @Provides fun auth(b: Backend): AuthRepository = b
    @Provides fun profiles(b: Backend): ProfileRepository = b
    @Provides fun matches(b: Backend): MatchRepository = b
    @Provides fun interests(b: Backend): InterestRepository = b
    @Provides fun chat(b: Backend): ChatRepository = b
    @Provides fun ai(b: Backend): AiRepository = b
    @Provides fun safety(b: Backend): SafetyRepository = b
    @Provides fun privacy(b: Backend): PrivacyRepository = b
    @Provides fun family(b: Backend): FamilyRepository = b
    @Provides fun verification(b: Backend): VerificationRepository = b
}
