package com.vachan.app.ui.root

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.vachan.app.ui.brand.AanganMark
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.chat.ChatScreen
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.discover.ProfileDetailScreen
import com.vachan.app.ui.family.FamilyScreen
import com.vachan.app.ui.home.HomeScreen
import com.vachan.app.ui.onboarding.AuthFlow
import com.vachan.app.ui.onboarding.ConsentScreen
import com.vachan.app.ui.profile.ProfileEditorScreen
import com.vachan.app.ui.safety.MeetSafelyScreen
import com.vachan.app.ui.safety.SafetyCenterScreen
import com.vachan.app.ui.settings.PrivacyCenterScreen
import com.vachan.app.ui.settings.PrivacyNoticeScreen
import com.vachan.app.ui.theme.AanganColors
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
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(locked) {
        // A focused text field behind the lock must not keep receiving keystrokes.
        if (locked) { focus.clearFocus(force = true); keyboard?.hide() }
    }

    CompositionLocalProvider(LocalCanUseLock provides canUseLock) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // While locked the app stays composed (so you return to where you were) but is invisible,
            // removed from the accessibility tree, and covered by an input-consuming lock screen.
            Box(Modifier.fillMaxSize().then(if (locked) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier)) {
                AnimatedContent(targetState = state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "root") { s ->
                    when (s) {
                        RootState.SIGNED_OUT -> AuthFlow(demoMode = !BuildConfig.FIREBASE_ENABLED)
                        RootState.NEEDS_CONSENT -> ConsentScreen()
                        RootState.NEEDS_PROFILE -> ProfileEditorScreen(isOnboarding = true, onDone = {})
                        RootState.READY -> MainNavigation()
                    }
                }
            }
            if (locked) LockScreen(onUnlockRequest)
        }
    }
}

@Composable
private fun LockScreen(onUnlockRequest: ((String) -> Unit) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) { onUnlockRequest { error = it } }
    // Back must not navigate the hidden app underneath; it leaves the app instead.
    BackHandler { activity?.moveTaskToBack(true) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AanganMark(size = 56.dp)
            Spacer(Modifier.height(6.dp))
            Text("${Brand.NAME} is locked", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Text(
                "Your matches and conversations stay private, even on a shared phone.",
                style = MaterialTheme.typography.bodyLarge, color = AanganColors.extended.muted, textAlign = TextAlign.Center,
            )
            error?.let { Text(it, color = AanganColors.extended.danger, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
        PrimaryButton(
            "Unlock", onClick = { error = null; onUnlockRequest { error = it } },
            icon = Icons.Outlined.Fingerprint, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
    }
}

// ------------------------------------------------------------------------------------- routes

@Serializable object HomeRoute
@Serializable data class ProfileRoute(val id: String)
@Serializable data class ChatRoute(val matchId: String)
@Serializable object EditProfileRoute
@Serializable object VerificationRoute
@Serializable object PrivacyRoute
@Serializable object PrivacyNoticeRoute
@Serializable object SafetyRoute
@Serializable data class MeetSafelyRoute(val otherName: String = "")
@Serializable object FamilyRoute
@Serializable object AssistantRoute

/** Navigation callbacks handed to screens, so screens don't depend on the NavController. */
data class Nav(
    val openProfile: (String) -> Unit = {},
    val openChat: (String) -> Unit = {},
    val editProfile: () -> Unit = {},
    val verification: () -> Unit = {},
    val privacy: () -> Unit = {},
    val privacyNotice: () -> Unit = {},
    val safety: () -> Unit = {},
    val meetSafely: (String) -> Unit = {},
    val family: () -> Unit = {},
    val assistant: () -> Unit = {},
    val back: () -> Unit = {},
)

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val n = remember(nav) {
        // launchSingleTop: a double tap can't stack two copies of the same screen.
        Nav(
            openProfile = { nav.navigate(ProfileRoute(it)) { launchSingleTop = true } },
            openChat = { nav.navigate(ChatRoute(it)) { launchSingleTop = true } },
            editProfile = { nav.navigate(EditProfileRoute) { launchSingleTop = true } },
            verification = { nav.navigate(VerificationRoute) { launchSingleTop = true } },
            privacy = { nav.navigate(PrivacyRoute) { launchSingleTop = true } },
            privacyNotice = { nav.navigate(PrivacyNoticeRoute) { launchSingleTop = true } },
            safety = { nav.navigate(SafetyRoute) { launchSingleTop = true } },
            meetSafely = { nav.navigate(MeetSafelyRoute(it)) { launchSingleTop = true } },
            family = { nav.navigate(FamilyRoute) { launchSingleTop = true } },
            assistant = { nav.navigate(AssistantRoute) { launchSingleTop = true } },
            back = { if (nav.previousBackStackEntry != null) nav.popBackStack() },
        )
    }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> { HomeScreen(n) }
        composable<ProfileRoute> { ProfileDetailScreen(it.toRoute<ProfileRoute>().id, n) }
        composable<ChatRoute> { ChatScreen(it.toRoute<ChatRoute>().matchId, n) }
        composable<EditProfileRoute> { ProfileEditorScreen(isOnboarding = false, onDone = n.back) }
        composable<VerificationRoute> { VerificationScreen(n) }
        composable<PrivacyRoute> { PrivacyCenterScreen(n) }
        composable<PrivacyNoticeRoute> { PrivacyNoticeScreen(onBack = n.back) }
        composable<SafetyRoute> { SafetyCenterScreen(n) }
        composable<MeetSafelyRoute> { MeetSafelyScreen(it.toRoute<MeetSafelyRoute>().otherName, n) }
        composable<FamilyRoute> { FamilyScreen(n) }
        composable<AssistantRoute> { AssistantScreen(n) }
    }
}
