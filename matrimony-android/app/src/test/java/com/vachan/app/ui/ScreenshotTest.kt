package com.vachan.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.ConsentRecord
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.PartnerPreferences
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ValueAnswer
import com.vachan.app.core.model.ValueQuestion
import com.vachan.app.core.model.Verification
import com.vachan.app.core.photos.EncryptedPhotoStore
import com.vachan.app.core.photos.LocalPhotoStore
import com.vachan.app.core.security.SecureStore
import com.vachan.app.data.LocalSettingsRepository
import com.vachan.app.data.demo.DemoBackend
import com.vachan.app.data.demo.DemoSeed
import com.vachan.app.ui.chat.ChatScreen
import com.vachan.app.ui.chat.ChatViewModel
import com.vachan.app.ui.discover.DiscoverScreen
import com.vachan.app.ui.discover.DiscoverViewModel
import com.vachan.app.ui.discover.ProfileDetailScreen
import com.vachan.app.ui.discover.ProfileDetailViewModel
import com.vachan.app.ui.interests.InterestsScreen
import com.vachan.app.ui.interests.InterestsViewModel
import com.vachan.app.ui.me.MeScreen
import com.vachan.app.ui.me.MeViewModel
import com.vachan.app.ui.onboarding.AuthFlow
import com.vachan.app.ui.onboarding.AuthViewModel
import com.vachan.app.ui.onboarding.ConsentScreen
import com.vachan.app.ui.onboarding.ConsentViewModel
import com.vachan.app.ui.profile.ProfileEditorScreen
import com.vachan.app.ui.profile.ProfileEditorViewModel
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.safety.CheckInAlarms
import com.vachan.app.ui.safety.MeetSafelyScreen
import com.vachan.app.ui.safety.SafetyCenterScreen
import com.vachan.app.ui.safety.SafetyViewModel
import com.vachan.app.ui.search.SearchScreen
import com.vachan.app.ui.search.SearchViewModel
import com.vachan.app.ui.settings.PrivacyCenterScreen
import com.vachan.app.ui.settings.PrivacyViewModel
import com.vachan.app.ui.theme.AanganTheme
import com.vachan.app.ui.verification.VerificationScreen
import com.vachan.app.ui.verification.VerificationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders real screens against the Demo backend. Doubles as a smoke test (every screen must
 * compose without crashing) and, with `-Pscreenshots`, writes PNGs to app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one would start Hilt and open the same DataStores as the test.
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {

    // v1 rule on purpose: its unconfined effect dispatcher lets view models settle without manual pumping.
    @Suppress("DEPRECATION")
    @get:Rule val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: LocalSettingsRepository
    private lateinit var backend: DemoBackend
    private lateinit var photos: EncryptedPhotoStore

    private val nav = Nav()
    private var scamMatchId = ""

    private object NoAlarms : CheckInAlarms {
        override fun schedule(context: Context, atMillis: Long) = Unit
        override fun cancel(context: Context) = Unit
    }

    private val me = Profile(
        id = "me", firstName = "Meera", lastInitial = "S", gender = Gender.FEMALE, age = 29, heightCm = 163,
        location = DemoSeed.Cities.pune, motherTongue = "Marathi", education = com.vachan.app.core.model.EducationLevel.MASTERS,
        educationField = "Design", profession = "UX designer", diet = com.vachan.app.core.model.Diet.VEGETARIAN,
        interests = setOf("Trekking", "Cooking", "Classical music", "Reading"), languages = setOf("English", "Hindi"),
        about = "Product designer who loves monsoon treks, slow Sunday breakfasts and Hindustani classical music.",
        values = ValueQuestion.entries.associateWith { ValueAnswer(answer = 3, importance = 2) } +
            (ValueQuestion.CHILDREN to ValueAnswer(1, 3)),
        verification = Verification(phone = true, selfie = true),
        preferences = PartnerPreferences(minAge = 28, maxAge = 36),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AeadConfig.register()
        val aead = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.generateEntryFromParameters(PredefinedAeadParameters.AES256_GCM).withRandomId().makePrimary())
            .build().getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val store = SecureStore(context) { aead }
        settings = LocalSettingsRepository(store, scope)
        photos = EncryptedPhotoStore(context) { aead }
        backend = DemoBackend(store, scope, settings, photos)
        runBlocking {
            backend.sendOtp("+919876543210")
            backend.verifyOtp(DemoBackend.DEMO_OTP)
            settings.update {
                it.copy(onboarded = true, consents = mapOf(ConsentPurpose.AI_ASSISTANCE to ConsentRecord(true, 0L)))
            }
            backend.saveMyProfile(me)
        }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun shoot(name: String, settleMs: Long = 600, dark: Boolean = false, inset: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            AanganTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalPhotoStore provides photos) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (inset) Modifier.statusBarsPadding() else Modifier)) { content() }
                }
            }
        }
        // Let view-model coroutines (simulated network delays) run on the paused main looper.
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(settleMs))
            compose.waitForIdle()
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("$name rendered empty", bitmap.width > 0 && bitmap.height > 0)
        if (System.getProperty("vachan.screenshots.enabled") == "true") {
            val dir = File(System.getProperty("vachan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun welcome() = shoot("01_welcome", inset = false) { AuthFlow(demoMode = true, vm = AuthViewModel(backend)) }

    @Test fun signIn() {
        val vm = AuthViewModel(backend).apply { goTo(1); onPhone("9876543210") }
        shoot("02_sign_in", inset = false) { AuthFlow(demoMode = true, vm = vm) }
    }

    @Test fun consent() = shoot("03_consent", inset = false) { ConsentScreen(vm = ConsentViewModel(settings, backend)) }

    @Test fun valuesCompass() {
        val vm = ProfileEditorViewModel(backend, backend, settings)
        vm.goTo(4)
        shoot("04_values_compass", inset = false) { ProfileEditorScreen(isOnboarding = false, onDone = {}, vm = vm) }
    }

    @Test fun dailyPicks() = shoot("05_today") { DiscoverScreen(nav, vm = DiscoverViewModel(backend, backend)) }

    @Test fun dailyPicksDark() = shoot("05b_today_dark", dark = true) { DiscoverScreen(nav, vm = DiscoverViewModel(backend, backend)) }

    @Test fun profileDetail() {
        val target = runBlocking { backend.dailyPicks().first().profile.id }
        val vm = ProfileDetailViewModel(backend, backend, backend, backend, backend, backend)
        shoot("06_profile", settleMs = 900, inset = false) { ProfileDetailScreen(target, nav, vm) }
    }

    @Test fun naturalLanguageSearch() {
        val vm = SearchViewModel(backend, backend, backend)
        vm.onQuery("Vegetarian doctor or scientist, 28 to 34, verified")
        vm.search()
        shoot("07_search") { SearchScreen(nav, vm = vm) }
    }

    @Test fun interests() = shoot("08_interests") { InterestsScreen(nav, vm = InterestsViewModel(backend, backend, backend, backend)) }

    @Test fun scamWarningInChat() {
        runBlocking {
            val scamMatch = backend.conversations.first { list -> list.any { it.otherId == DemoSeed.SCAM_PERSONA_MAN } }
                .first { it.otherId == DemoSeed.SCAM_PERSONA_MAN }.matchId
            scamMatchId = scamMatch
            // Advance the scripted scam persona: off-platform push, then the "customs fee" request.
            backend.sendMessage(scamMatch, "Hi Daniel, nice to meet you.")
            Thread.sleep(4_200)
            backend.sendMessage(scamMatch, "Why can't we video call?")
            Thread.sleep(4_200)
        }
        val vm = ChatViewModel(backend, backend, backend, backend, backend, settings)
        shoot("09_chat_safety_copilot", inset = false) { ChatScreen(scamMatchId, nav, vm) }
    }

    @Test fun you() = shoot("10_you") { MeScreen(nav, vm = MeViewModel(backend, backend)) }

    @Test fun privacyCenter() = shoot("11_privacy_center", inset = false) { PrivacyCenterScreen(nav, vm = PrivacyViewModel(settings, backend, backend)) }

    @Test fun safetyCenter() = shoot("12_safety_center", inset = false) { SafetyCenterScreen(nav, vm = SafetyViewModel(backend, settings).apply { alarms = NoAlarms }) }

    @Test fun meetSafelyWithCheckIn() {
        runBlocking { settings.update { it.copy(trustedContactName = "Asha", trustedContactPhone = "+919812345678", checkInAt = System.currentTimeMillis() + 83 * 60_000L) } }
        shoot("13_meet_safely", inset = false) { MeetSafelyScreen("Rohan", nav, vm = SafetyViewModel(backend, settings).apply { alarms = NoAlarms }) }
    }

    @Test fun verification() = shoot("14_verification", inset = false) { VerificationScreen(nav, vm = VerificationViewModel(backend, backend)) }

    @Test fun photosAndBio() {
        val vm = ProfileEditorViewModel(backend, backend, settings)
        vm.goTo(6)
        shoot("15_photos_and_bio", inset = false) { ProfileEditorScreen(isOnboarding = false, onDone = {}, vm = vm) }
    }
}
