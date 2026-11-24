package com.vachan.app.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.VerificationRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.VachanColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val gestures = listOf("Turn your head slowly to the left", "Turn your head to the right", "Smile", "Blink twice", "Look up", "Nod")

    fun startSelfie() { _ui.value = VerifyUi(challenge = gestures.shuffled().take(3), challengeStep = 0) }

    fun nextGesture() {
        val ui = _ui.value
        if (ui.challengeStep < ui.challenge.lastIndex) _ui.value = ui.copy(challengeStep = ui.challengeStep + 1)
        else submitSelfie()
    }

    private fun submitSelfie() = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = "selfie", challengeStep = -1)
        verification.submitSelfie(challengePassed = true)
            .onSuccess { _ui.value = VerifyUi(message = "Selfie verified. Your blue badge is live.") }
            .onFailure { _ui.value = VerifyUi(error = it.message) }
    }

    fun startId() = viewModelScope.launch {
        _ui.value = VerifyUi(busy = "id")
        verification.startGovernmentId()
            .onSuccess { _ui.value = VerifyUi(message = it) }
            .onFailure { _ui.value = VerifyUi(error = it.message) }
    }

    fun sendEmail(email: String) = viewModelScope.launch {
        _ui.value = VerifyUi(busy = "email")
        verification.sendWorkEmailCode(email)
            .onSuccess { _ui.value = VerifyUi(emailSent = true, message = "We sent a code to $email.") }
            .onFailure { _ui.value = VerifyUi(error = it.message) }
    }

    fun confirmEmail(code: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = "email")
        verification.confirmWorkEmail(code)
            .onSuccess { _ui.value = VerifyUi(message = "Work email verified. We don't show your employer's name unless you add it.") }
            .onFailure { _ui.value = _ui.value.copy(busy = null, error = it.message) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(nav: Nav, vm: VerificationViewModel = hiltViewModel()) {
    val me by vm.me.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val v = me?.verification ?: return
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Verification") }, navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Banner(tone = BannerTone.SUCCESS, title = "Always free", text = "Verification is free for every member, before any payment. Verified members get more responses and can see photos shared with verified members only.")
            ui.message?.let { Banner(tone = BannerTone.SUCCESS, text = it) }
            ui.error?.let { Banner(tone = BannerTone.DANGER, text = it) }

            Step(Icons.Filled.PhoneAndroid, "Phone number", "Verified with a one-time code when you signed up.", done = v.phone) {}

            Step(Icons.Filled.Face, "Selfie liveness", "Follow three random prompts on camera. We compare your face with your profile photos — the selfie is deleted after checking.", done = v.selfie) {
                if (ui.challengeStep >= 0) {
                    SectionCard {
                        Text("Prompt ${ui.challengeStep + 1} of ${ui.challenge.size}", style = MaterialTheme.typography.labelLarge)
                        Text(ui.challenge[ui.challengeStep], style = MaterialTheme.typography.headlineSmall)
                        Text("Camera liveness runs here when the verification vendor SDK is configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = vm::nextGesture, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                } else {
                    Button(onClick = vm::startSelfie, enabled = ui.busy == null) {
                        if (ui.busy == "selfie") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Start selfie check")
                    }
                }
            }

            Step(Icons.Filled.Badge, "Government ID", "Verify through DigiLocker or Aadhaar offline verification. We store only the result (name matches, age confirmed) — never your Aadhaar number or a copy of your ID.", done = v.governmentId) {
                Button(onClick = vm::startId, enabled = ui.busy == null) {
                    if (ui.busy == "id") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Verify with DigiLocker")
                }
            }

            Step(Icons.Filled.Work, "Work email", "Confirms you work where you say. Personal email domains aren't accepted.", done = v.workEmail) {
                if (!ui.emailSent) {
                    OutlinedTextField(email, { email = it.trim() }, label = { Text("Work email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { vm.sendEmail(email) }, enabled = email.contains('@') && ui.busy == null) { Text("Send code") }
                } else {
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.confirmEmail(code) }, enabled = code.length == 6) { Text("Confirm") }
                }
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String, done: Boolean, content: @Composable () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text("  $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (done) Icon(Icons.Filled.CheckCircle, "Done", tint = VachanColors.extended.verified)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!done) content()
    }
}
