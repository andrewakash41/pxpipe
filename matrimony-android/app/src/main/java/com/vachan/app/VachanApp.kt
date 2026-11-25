package com.vachan.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.google.firebase.FirebaseApp
import com.vachan.app.core.safety.CheckInScheduler
import com.vachan.app.core.security.AppLockManager
import com.vachan.app.di.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VachanApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_ENABLED) {
            FirebaseApp.initializeApp(this)
            // Only the genuine, untampered app (Play Integrity) can call the backend.
            AppCheckInstaller.install()
        }
        CheckInScheduler.ensureChannel(this)
        appLock.start()
    }

    /**
     * Other members' photos arrive through short-lived signed URLs and are kept in memory only:
     * no disk cache, so nothing about anyone else is left on this phone.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache(null)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .crossfade(true)
            .build()
}
