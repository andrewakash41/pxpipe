package com.vachan.app.ui.root

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vachan.app.BuildConfig
import com.vachan.app.core.security.AppLockManager
import com.vachan.app.data.AuthRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.assistant.AssistantScreen
import com.vachan.app.ui.chat.ChatScreen
import com.vachan.app.ui.discover.ProfileDetailScreen
import com.vachan.app.ui.family.FamilyScreen
import com.vachan.app.ui.home.HomeScreen
import com.vachan.app.ui.onboarding.AuthFlow
import com.vachan.app.ui.onboarding.ConsentScreen
import com.vachan.app.ui.profile.ProfileEditorScreen
import com.vachan.app.ui.safety.MeetSafelyScreen
import com.vachan.app.ui.safety.SafetyCenterScreen
import com.vachan.app.ui.settings.PrivacyCenterScreen
import com.vachan.app.ui.verification.VerificationScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject

enum class RootState { SIGNED_OUT, NEEDS_CONSENT, NEEDS_PROFILE, READY }

@HiltViewModel
class RootViewModel @Inject constructor(
    auth: AuthRepository,
    settings: SettingsRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    private fun compute(signedIn: Boolean, onboarded: Boolean, hasProfile: Boolean) = when {
        !signedIn -> RootState.SIGNED_OUT
        !onboarded -> RootState.NEEDS_CONSENT
        !hasProfile -> RootState.NEEDS_PROFILE
        else -> RootState.READY
    }

    val state: StateFlow<RootState> = combine(auth.session, settings.settings, profiles.myProfile) { s, st, p ->
        compute(s != null, st.onboarded, p != null)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        compute(auth.session.value != null, settings.settings.value.onboarded, profiles.myProfile.value != null),
    )
}

/** Lets settings screens enable the app lock only when the device supports it. */
val LocalCanUseLock = staticCompositionLocalOf<() -> Boolean> { { false } }

@Composable
fun VachanRoot(
    appLock: AppLockManager,
    onUnlockRequest: ((String) -> Unit) -> Unit,
    canUseLock: () -> Boolean,
    vm: RootViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locked by appLock.locked.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalCanUseLock provides canUseLock) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(targetState = state, label = "root") { s ->
                when (s) {
                    RootState.SIGNED_OUT -> AuthFlow(demoMode = !BuildConfig.FIREBASE_ENABLED)
                    RootState.NEEDS_CONSENT -> ConsentScreen()
                    RootState.NEEDS_PROFILE -> ProfileEditorScreen(isOnboarding = true, onDone = {})
                    RootState.READY -> MainNavigation()
                }
            }
            if (locked) LockScreen(onUnlockRequest)
        }
    }
}

@Composable
private fun LockScreen(onUnlockRequest: ((String) -> Unit) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { onUnlockRequest { error = it } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
            Text("Vachan is locked", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                "Your matches and conversations stay private, even on a shared phone.",
                color = MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { error = null; onUnlockRequest { error = it } }) { Text("Unlock") }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

// ------------------------------------------------------------------------------------- routes

@Serializable object HomeRoute
@Serializable data class ProfileRoute(val id: String)
@Serializable data class ChatRoute(val matchId: String)
@Serializable object EditProfileRoute
@Serializable object VerificationRoute
@Serializable object PrivacyRoute
@Serializable object SafetyRoute
@Serializable data class MeetSafelyRoute(val otherName: String = "")
@Serializable object FamilyRoute
@Serializable object AssistantRoute

/** Navigation callbacks handed to screens, so screens don't depend on the NavController. */
data class Nav(
    val openProfile: (String) -> Unit,
    val openChat: (String) -> Unit,
    val editProfile: () -> Unit,
    val verification: () -> Unit,
    val privacy: () -> Unit,
    val safety: () -> Unit,
    val meetSafely: (String) -> Unit,
    val family: () -> Unit,
    val assistant: () -> Unit,
    val back: () -> Unit,
)

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val n = remember(nav) {
        Nav(
            openProfile = { nav.navigate(ProfileRoute(it)) },
            openChat = { nav.navigate(ChatRoute(it)) },
            editProfile = { nav.navigate(EditProfileRoute) },
            verification = { nav.navigate(VerificationRoute) },
            privacy = { nav.navigate(PrivacyRoute) },
            safety = { nav.navigate(SafetyRoute) },
            meetSafely = { nav.navigate(MeetSafelyRoute(it)) },
            family = { nav.navigate(FamilyRoute) },
            assistant = { nav.navigate(AssistantRoute) },
            back = { nav.popBackStack() },
        )
    }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> { HomeScreen(n) }
        composable<ProfileRoute> { ProfileDetailScreen(it.toRoute<ProfileRoute>().id, n) }
        composable<ChatRoute> { ChatScreen(it.toRoute<ChatRoute>().matchId, n) }
        composable<EditProfileRoute> { ProfileEditorScreen(isOnboarding = false, onDone = n.back) }
        composable<VerificationRoute> { VerificationScreen(n) }
        composable<PrivacyRoute> { PrivacyCenterScreen(n) }
        composable<SafetyRoute> { SafetyCenterScreen(n) }
        composable<MeetSafelyRoute> { MeetSafelyScreen(it.toRoute<MeetSafelyRoute>().otherName, n) }
        composable<FamilyRoute> { FamilyScreen(n) }
        composable<AssistantRoute> { AssistantScreen(n) }
    }
}
