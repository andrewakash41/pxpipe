package com.vachan.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.vachan.app.core.security.AppLockManager
import com.vachan.app.di.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VachanApp : Application() {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_ENABLED) {
            FirebaseApp.initializeApp(this)
            // Only the genuine, untampered app (Play Integrity) can call the backend.
            AppCheckInstaller.install()
        }
        appLock.start()
    }
}
