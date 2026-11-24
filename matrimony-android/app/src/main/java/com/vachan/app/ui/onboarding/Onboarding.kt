package com.vachan.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.ConsentRecord
import com.vachan.app.data.AuthRepository
import com.vachan.app.data.PrivacyRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ------------------------------------------------------------------------------------- Auth

data class AuthUiState(
    val step: Int = 0, // 0 welcome, 1 phone, 2 otp
    val phone: String = "",
    val otp: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui = _ui.asStateFlow()

    fun goTo(step: Int) { _ui.value = _ui.value.copy(step = step, error = null) }
    fun onPhone(v: String) { _ui.value = _ui.value.copy(phone = v.filter(Char::isDigit).take(10), error = null) }
    fun onOtp(v: String) { _ui.value = _ui.value.copy(otp = v.filter(Char::isDigit).take(6), error = null) }

    fun sendOtp() = viewModelScope.launch {
        val phone = _ui.value.phone
        if (!Regex("[6-9]\\d{9}").matches(phone)) {
            _ui.value = _ui.value.copy(error = "Enter a valid 10-digit Indian mobile number"); return@launch
        }
        _ui.value = _ui.value.copy(busy = true)
        auth.sendOtp("+91$phone").onSuccess { _ui.value = _ui.value.copy(busy = false, step = 2) }
            .onFailure { _ui.value = _ui.value.copy(busy = false, error = it.message ?: "Couldn't send the code") }
    }

    fun verify() = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = true)
        auth.verifyOtp(_ui.value.otp).onFailure { _ui.value = _ui.value.copy(busy = false, error = it.message ?: "Verification failed") }
    }
}

@Composable
fun AuthFlow(demoMode: Boolean, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    when (ui.step) {
        0 -> WelcomeScreen(demoMode, onStart = { vm.goTo(1) })
        else -> PhoneScreen(ui, demoMode, vm)
    }
}

@Composable
private fun WelcomeScreen(demoMode: Boolean, onStart: () -> Unit) {
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF5E0F24)))
    Box(Modifier.fillMaxSize().background(gradient)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("वचन", style = MaterialTheme.typography.displaySmall, color = Color(0xFFFFC46B))
            Text("Vachan", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Verified people. Serious intent.\nYour data stays yours.",
                style = MaterialTheme.typography.titleLarge, color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Promise(Icons.Filled.Verified, "Free verification for everyone", "Selfie and ID checks before anyone can pay — so you meet real people.")
            Promise(Icons.Filled.AutoAwesome, "Matches that work both ways", "Two-way compatibility on values that matter, explained clearly.")
            Promise(Icons.Filled.Shield, "Safety built in", "Scam warnings in chat, safe-meeting check-ins, one-tap report.")
            Promise(Icons.Filled.Lock, "Private by default", "No screenshots, hidden phone number, photo controls, delete anytime.")
            Text(
                "Chat is free once you both say yes. We never make sales calls.",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                // Marigold on kumkum: the primary action must stand out from the brand background.
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC46B), contentColor = Color(0xFF3F0014)),
            ) { Text("Get started") }
            if (demoMode) {
                Text(
                    "Demo mode: runs fully on this phone with fictional profiles. Use any Indian mobile number; the code is 123456.",
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Promise(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFFFFC46B))
        }
        Column {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(body, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PhoneScreen(ui: AuthUiState, demoMode: Boolean, vm: AuthViewModel) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = { vm.goTo(if (ui.step == 2) 1 else 0) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        if (ui.step == 1) {
            Text("Your mobile number", style = MaterialTheme.typography.headlineMedium)
            Text(
                "We'll send a one-time code. Your number is never shown on your profile — you decide if and when to share it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ui.phone, onValueChange = vm::onPhone, label = { Text("Mobile number") },
                prefix = { Text("+91 ") }, singleLine = true, isError = ui.error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Enter the code", style = MaterialTheme.typography.headlineMedium)
            Text("Sent to +91 ${ui.phone}. Never share this code with anyone — not even someone claiming to be from Vachan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = ui.otp, onValueChange = vm::onOtp, label = { Text("6-digit code") }, singleLine = true,
                isError = ui.error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            if (demoMode) Text("Demo mode code: 123456", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (ui.step == 1) vm.sendOtp() else vm.verify() },
            enabled = !ui.busy && (if (ui.step == 1) ui.phone.length == 10 else ui.otp.length == 6),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (ui.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (ui.step == 1) "Send code" else "Verify")
        }
        Text(
            "By continuing you agree to our Terms and Privacy Notice. Vachan is for people seeking marriage — it isn't a dating app.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ----------------------------------------------------------------------------------- Consent

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val privacy: PrivacyRepository,
) : ViewModel() {
    fun finish(choices: Map<ConsentPurpose, Boolean>, language: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        choices.forEach { (p, g) -> privacy.setConsent(p, g) }
        settings.update {
            it.copy(
                onboarded = true,
                language = language,
                declaredIntentToMarry = true,
                consents = it.consents + choices.mapValues { (_, g) -> ConsentRecord(g, now) } +
                    (ConsentPurpose.ESSENTIAL to ConsentRecord(true, now)),
            )
        }
    }
}

val supportedLanguages = listOf("English", "हिन्दी (Hindi)", "मराठी (Marathi)", "தமிழ் (Tamil)", "తెలుగు (Telugu)", "বাংলা (Bengali)")

@Composable
fun ConsentScreen(vm: ConsentViewModel = hiltViewModel()) {
    val choices = remember { mutableStateMapOf(ConsentPurpose.AI_ASSISTANCE to true, ConsentPurpose.ANALYTICS to false, ConsentPurpose.MARKETING to false) }
    var language by rememberSaveable { mutableStateOf("English") }
    var intent by rememberSaveable { mutableStateOf(false) }
    var accurate by rememberSaveable { mutableStateOf(false) }
    var age by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your privacy, your choices", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Here's exactly what we use your information for. Only the first item is required. You can change any of these later in Privacy Center.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Language", subtitle = "Used for the app, AI bio help and translation") {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                supportedLanguages.forEach { l -> FilterChip(selected = language == l, onClick = { language = l }, label = { Text(l) }) }
            }
        }

        ConsentPurpose.entries.forEach { p ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Text(p.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(12.dp))
                    if (p.required) {
                        Text("Required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Switch(checked = choices[p] == true, onCheckedChange = { choices[p] = it })
                    }
                }
            }
        }

        Banner(
            tone = BannerTone.INFO,
            title = "What we never do",
            text = "Sell your data, show your phone number without your consent, use your photos for ads, or make sales calls.",
        )

        SectionCard(title = "Before you continue") {
            Declaration(intent, { intent = it }, "I'm joining to find a life partner for marriage.")
            Declaration(accurate, { accurate = it }, "The information I provide will be true and my own.")
            Declaration(age, { age = it }, "I'm of legal marriageable age (21+ for men, 18+ for women).")
        }

        Button(
            onClick = { scope.launch { vm.finish(choices.toMap(), language) } },
            enabled = intent && accurate && age,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Agree and continue") }
        TextButton(onClick = {}, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Read the full Privacy Notice") }
    }
}

@Composable
private fun Declaration(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
