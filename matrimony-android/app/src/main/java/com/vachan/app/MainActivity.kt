package com.vachan.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.vachan.app.core.security.AppLockManager
import com.vachan.app.core.security.SecureWindow
import com.vachan.app.data.firebase.ActivityHolder
import com.vachan.app.ui.root.VachanRoot
import com.vachan.app.ui.theme.VachanTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording and Recents previews everywhere in the app.
        SecureWindow.apply(this)
        enableEdgeToEdge()
        ActivityHolder.set(this)
        setContent {
            VachanTheme {
                VachanRoot(
                    appLock = appLock,
                    onUnlockRequest = { onError -> appLock.unlock(this, onError) },
                    canUseLock = { appLock.canUseLock(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ActivityHolder.set(this)
    }
}
