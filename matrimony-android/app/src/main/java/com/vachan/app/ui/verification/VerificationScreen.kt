package com.vachan.app.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.VerificationRepository
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.MatchScoreRing
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.SecondaryButton
import com.vachan.app.ui.components.Tag
import com.vachan.app.ui.components.TagTone
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

data class VerifyUi(
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null,
    val challenge: List<String> = emptyList(),
    val challengeStep: Int = -1,
    val emailSent: Boolean = false,
)

/**
 * Liveness: a randomised sequence of gestures defeats replayed videos and static deepfakes.
 * In production the camera frames are checked on-device (face present, pose changes) and a vendor
 * liveness + face-match service (with virtual-camera / injection-attack detection) confirms the
 * result server-side. Here the flow and API are in place; the vendor SDK plugs into [submitSelfie].
 */
@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verification: VerificationRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    val me = profiles.myProfile
    private val _ui = MutableStateFlow(VerifyUi())
    val ui = _ui.asStateFlow()
    private val random = SecureRandom()

    private val gestures = listOf("Turn your head slowly to the left", "Turn your head to the right", "Smile", "Blink twice", "Look up", "Nod")

    fun startSelfie() { _ui.value = VerifyUi(challenge = gestures.shuffled(random).take(3), challengeStep = 0) }

    fun nextGesture() {
        val ui = _ui.value
        if (ui.challengeStep < ui.challenge.lastIndex) _ui.value = ui.copy(challengeStep = ui.challengeStep + 1)
        else submitSelfie()
    }

    private fun submitSelfie() = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = "selfie", challengeStep = -1)
        verification.submitSelfie(challengePassed = true)
            .onSuccess { _ui.value = VerifyUi(message = "Selfie verified. Your badge is live.") }
            .onFailure { _ui.value = VerifyUi(error = it.userMessage()) }
    }

    fun startId() = viewModelScope.launch {
        _ui.value = VerifyUi(busy = "id")
        verification.startGovernmentId()
            .onSuccess { _ui.value = VerifyUi(message = it) }
            .onFailure { _ui.value = VerifyUi(error = it.userMessage()) }
    }

    fun sendEmail(email: String) = viewModelScope.launch {
        _ui.value = VerifyUi(busy = "email")
        verification.sendWorkEmailCode(email.trim())
            .onSuccess { _ui.value = VerifyUi(emailSent = true, message = "We sent a 6-digit code to $email.") }
            .onFailure { _ui.value = VerifyUi(error = it.userMessage()) }
    }

    fun confirmEmail(code: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = "email", error = null)
        verification.confirmWorkEmail(code)
            .onSuccess { _ui.value = VerifyUi(message = "Work email verified. We don't show your employer unless you add it.") }
            .onFailure { _ui.value = _ui.value.copy(busy = null, error = it.userMessage()) }
    }
}

@Composable
fun VerificationScreen(nav: Nav, vm: VerificationViewModel = hiltViewModel()) {
    val me by vm.me.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val v = me?.verification ?: return
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { TopBar(onBack = nav.back) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScreenTitle("Verification", subtitle = "Always free, before any payment. Verified members get more replies.", modifier = Modifier.weight(1f))
                MatchScoreRing(v.count * 25, size = 68.dp, caption = "${v.count}/4")
            }
            ui.message?.let { Notice(tone = NoticeTone.SUCCESS, text = it) }
            ui.error?.let { Notice(tone = NoticeTone.DANGER, text = it) }

            Step(Icons.Outlined.PhoneAndroid, "Phone number", "Confirmed with a one-time code when you signed up.", done = v.phone) {}

            Step(Icons.Outlined.Face, "Selfie liveness", "Follow three random prompts on camera. We compare your face with your photos, then delete the selfie.", done = v.selfie) {
                if (ui.challengeStep >= 0) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Eyebrow("Prompt ${ui.challengeStep + 1} of ${ui.challenge.size}")
                        Text(ui.challenge[ui.challengeStep], fontFamily = DisplayFamily, style = MaterialTheme.typography.headlineMedium)
                        Text("Camera liveness runs here once the verification vendor SDK is configured.", style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
                        PrimaryButton("Done", vm::nextGesture, Modifier.fillMaxWidth())
                    }
                } else {
                    PrimaryButton("Start selfie check", vm::startSelfie, Modifier.fillMaxWidth(), loading = ui.busy == "selfie", enabled = ui.busy == null)
                }
            }

            Step(Icons.Outlined.Badge, "Government ID", "Through DigiLocker or Aadhaar offline verification. We keep only the result — never your Aadhaar number or a copy of your ID.", done = v.governmentId) {
                SecondaryButton("Verify with DigiLocker", vm::startId, Modifier.fillMaxWidth(), loading = ui.busy == "id", enabled = ui.busy == null)
            }

            Step(Icons.Outlined.Work, "Work email", "Confirms where you work. Personal email domains aren't accepted.", done = v.workEmail) {
                if (!ui.emailSent) {
                    AppTextField(email, { email = it.trim().take(120) }, label = "Work email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    SecondaryButton("Send code", { vm.sendEmail(email) }, Modifier.fillMaxWidth(), enabled = email.contains('@') && ui.busy == null, loading = ui.busy == "email")
                } else {
                    AppTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = "6-digit code", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    PrimaryButton("Confirm", { vm.confirmEmail(code) }, Modifier.fillMaxWidth(), enabled = code.length == 6, loading = ui.busy == "email")
                }
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String, done: Boolean, content: @Composable () -> Unit) {
    val ext = AanganColors.extended
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (done) ext.verifiedContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (done) ext.verified else MaterialTheme.colorScheme.onSurface) }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (done) Tag("Done", tone = TagTone.VERIFIED)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = ext.muted)
        if (!done) content()
    }
}
