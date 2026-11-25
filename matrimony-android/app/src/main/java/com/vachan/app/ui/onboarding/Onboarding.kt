package com.vachan.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.ConsentRecord
import com.vachan.app.core.safety.PhoneNumbers
import com.vachan.app.data.AuthRepository
import com.vachan.app.data.PrivacyRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.brand.AanganWordmark
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.ChoiceChips
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.IllustratedAvatar
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.Section
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.settings.PrivacyNoticeScreen
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.TextFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------------------------- Auth

data class AuthUiState(
    val step: Int = 0, // 0 welcome, 1 phone, 2 code
    val country: PhoneNumbers.Country = PhoneNumbers.countries.first(),
    val phone: String = "",
    val otp: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** Epoch ms after which "Resend code" is allowed. */
    val resendAt: Long = 0L,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui = _ui.asStateFlow()

    fun goTo(step: Int) { _ui.value = _ui.value.copy(step = step, error = null, otp = if (step < 2) "" else _ui.value.otp) }
    fun onCountry(c: PhoneNumbers.Country) { _ui.value = _ui.value.copy(country = c, phone = _ui.value.phone.take(c.maxDigits), error = null) }
    fun onPhone(v: String) { _ui.value = _ui.value.copy(phone = v.filter(Char::isDigit).take(_ui.value.country.maxDigits), error = null) }

    fun onOtp(v: String) {
        val code = v.filter(Char::isDigit).take(6)
        _ui.value = _ui.value.copy(otp = code, error = null)
        if (code.length == 6 && !_ui.value.busy) verify()
    }

    private fun e164() = _ui.value.country.dialCode + _ui.value.phone

    fun sendOtp(resend: Boolean = false) = viewModelScope.launch {
        val s = _ui.value
        PhoneNumbers.validate(s.country, s.phone)?.let { _ui.value = s.copy(error = it); return@launch }
        _ui.value = s.copy(busy = true, error = null)
        auth.sendOtp(e164(), resend)
            .onSuccess { _ui.value = _ui.value.copy(busy = false, step = 2, otp = "", resendAt = System.currentTimeMillis() + RESEND_MS) }
            .onFailure { _ui.value = _ui.value.copy(busy = false, error = it.userMessage("Couldn't send the code. Please try again.")) }
    }

    fun verify() = viewModelScope.launch {
        if (_ui.value.otp.length != 6 || _ui.value.busy) return@launch
        _ui.value = _ui.value.copy(busy = true)
        auth.verifyOtp(_ui.value.otp)
            .onSuccess { _ui.value = _ui.value.copy(busy = false) }
            .onFailure { _ui.value = _ui.value.copy(busy = false, otp = "", error = it.userMessage("Verification failed. Please try again.")) }
    }

    private companion object { const val RESEND_MS = 30_000L }
}

@Composable
fun AuthFlow(demoMode: Boolean, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = ui.step > 0) { vm.goTo(ui.step - 1) }
    when (ui.step) {
        0 -> WelcomeScreen(demoMode, onStart = { vm.goTo(1) })
        1 -> PhoneScreen(ui, demoMode, vm)
        else -> CodeScreen(ui, demoMode, vm)
    }
}

@Composable
private fun WelcomeScreen(demoMode: Boolean, onStart: () -> Unit) {
    val ext = AanganColors.extended
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        AanganWordmark(markSize = 28.dp, style = MaterialTheme.typography.headlineMedium)
        ArchCollage(Modifier.fillMaxWidth().height(250.dp))
        Text("Marriage-minded.\nPrivacy-first.", style = MaterialTheme.typography.displayMedium)
        Text(
            "Verified people, introductions that work both ways, and real safety — with your data kept yours.",
            style = MaterialTheme.typography.bodyLarge, color = ext.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Promise(Icons.Outlined.Verified, "Free verification for everyone", "Selfie and ID checks before anyone can pay.")
            Promise(Icons.Outlined.Shield, "Safety built in", "Scam warnings in chat, safe-meeting check-ins, one-tap report.")
            Promise(Icons.Outlined.Lock, "Private by default", "No screenshots, hidden phone number, photo controls.")
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Get started", onStart, Modifier.fillMaxWidth())
        Text(
            "Chat is free once you both say yes. We never make sales calls.",
            style = MaterialTheme.typography.bodySmall, color = ext.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (demoMode) {
            Notice("Demo mode runs fully on this phone with fictional members. Use any mobile number; the code is 123456.")
        }
    }
}

/** Three arched "windows" with illustrated portraits: the courtyard motif of the brand. */
@Composable
private fun ArchCollage(modifier: Modifier) {
    val arch = RoundedCornerShape(topStart = 200.dp, topEnd = 200.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    Box(modifier.clearAndSetSemantics { }) {
        IllustratedAvatar(seed = 0, initials = "AK", modifier = Modifier.align(Alignment.BottomStart).width(118.dp).height(196.dp).clip(arch))
        IllustratedAvatar(seed = 1, initials = "RS", modifier = Modifier.align(Alignment.TopCenter).width(132.dp).height(236.dp).clip(arch))
        IllustratedAvatar(seed = 4, initials = "MV", modifier = Modifier.align(Alignment.BottomEnd).width(118.dp).height(184.dp).clip(arch))
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = (-6).dp).clip(CircleShape).background(AanganColors.extended.card)
                .border(1.dp, AanganColors.extended.hairline, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Verified, null, tint = AanganColors.extended.verified, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Verification is free", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(AanganColors.extended.card).border(1.dp, AanganColors.extended.hairline, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneScreen(ui: AuthUiState, demoMode: Boolean, vm: AuthViewModel) {
    var picking by remember { mutableStateOf(false) }
    val ext = AanganColors.extended
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().navigationBarsPadding()) {
        TopBar(onBack = { vm.goTo(0) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ScreenTitle("What's your number?", subtitle = "We'll text a one-time code. Your number is never shown on your profile — you decide if and when to share it.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { picking = true }, shape = RoundedCornerShape(18.dp), color = ext.card, border = BorderStroke(1.dp, ext.hairline),
                    modifier = Modifier.heightIn(min = 60.dp).semantics { contentDescription = "Country code ${ui.country.name} ${ui.country.dialCode}, change" },
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${ui.country.flag}  ${ui.country.dialCode}", style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    }
                }
                com.vachan.app.ui.components.AppTextField(
                    value = ui.phone, onValueChange = vm::onPhone, modifier = Modifier.weight(1f),
                    placeholder = ui.country.example, isError = ui.error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
            ui.error?.let { Text(it, color = ext.danger, style = MaterialTheme.typography.bodyMedium) }
        }
        Column(Modifier.padding(Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Send code", { vm.sendOtp() }, Modifier.fillMaxWidth(), loading = ui.busy, enabled = ui.phone.length >= ui.country.minDigits)
            Text(
                "By continuing you agree to our Terms and Privacy Notice. ${Brand.NAME} is for people seeking marriage — it isn't a dating app.",
                style = MaterialTheme.typography.bodySmall, color = ext.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Where's your number from?", style = MaterialTheme.typography.headlineSmall)
                ListGroup {
                    PhoneNumbers.countries.forEachIndexed { i, c ->
                        if (i > 0) ListDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.onCountry(c); picking = false }.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${c.flag}   ${c.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(c.dialCode, style = MaterialTheme.typography.bodyLarge, color = ext.muted)
                            if (c == ui.country) { Spacer(Modifier.width(10.dp)); Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeScreen(ui: AuthUiState, demoMode: Boolean, vm: AuthViewModel) {
    val ext = AanganColors.extended
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.resendAt) { while (System.currentTimeMillis() < ui.resendAt) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val wait = ((ui.resendAt - now) / 1000).coerceAtLeast(0)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().navigationBarsPadding()) {
        TopBar(onBack = { vm.goTo(1) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ScreenTitle("Enter the code", subtitle = "Sent to ${ui.country.dialCode} ${ui.phone}. Never share this code — not even with someone claiming to be from ${Brand.NAME}.")
            // One hidden field drives six boxes, so paste and SMS autofill both work.
            BasicTextField(
                value = ui.otp, onValueChange = vm::onOtp,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.focusRequester(focus).semantics { contentDescription = "6-digit code" },
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { i ->
                            val ch = ui.otp.getOrNull(i)?.toString() ?: ""
                            val active = i == ui.otp.length
                            Box(
                                Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(16.dp)).background(ext.card)
                                    .border(if (active) 1.5.dp else 1.dp, if (ui.error != null) ext.danger else if (active) MaterialTheme.colorScheme.onSurface else ext.hairline, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(ch, style = TextStyle(fontFamily = TextFamily, fontSize = 24.sp), color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                },
            )
            ui.error?.let { Text(it, color = ext.danger, style = MaterialTheme.typography.bodyMedium) }
            if (demoMode) Text("Demo mode code: 123456", style = MaterialTheme.typography.bodySmall, color = ext.verified)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (wait > 0) Text("Resend code in 0:%02d".format(wait), style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                else QuietButton("Resend code", { vm.sendOtp(resend = true) }, enabled = !ui.busy)
                Spacer(Modifier.weight(1f))
                QuietButton("Change number", { vm.goTo(1) }, color = ext.muted)
            }
        }
        PrimaryButton("Verify", { vm.verify() }, Modifier.fillMaxWidth().padding(Gutter), loading = ui.busy, enabled = ui.otp.length == 6)
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
        choices.forEach { (p, g) -> runCatching { privacy.setConsent(p, g) } }
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

/** Languages for AI bio help and chat translation (screens are in English for now). */
val supportedLanguages = listOf("English", "हिन्दी (Hindi)", "मराठी (Marathi)", "தமிழ் (Tamil)", "తెలుగు (Telugu)", "বাংলা (Bengali)")

@Composable
fun ConsentScreen(vm: ConsentViewModel = hiltViewModel()) {
    // DPDP: consent must be a clear affirmative act, so every optional purpose starts OFF.
    val choices = remember { mutableStateMapOf(ConsentPurpose.AI_ASSISTANCE to false, ConsentPurpose.ANALYTICS to false, ConsentPurpose.MARKETING to false) }
    var language by rememberSaveable { mutableStateOf("English") }
    var intent by rememberSaveable { mutableStateOf(false) }
    var accurate by rememberSaveable { mutableStateOf(false) }
    var age by rememberSaveable { mutableStateOf(false) }
    var showNotice by rememberSaveable { mutableStateOf(false) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ext = AanganColors.extended

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ScreenTitle("Your privacy, your choices", eyebrow = "Before we begin", subtitle = "Here's exactly what we use your information for. Only the first is needed to run your account; everything else is off unless you turn it on, and you can change it anytime.")

        ListGroup(title = "How we may use your data") {
            ConsentPurpose.entries.forEachIndexed { i, p ->
                if (i > 0) ListDivider()
                if (p.required) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(p.title, style = MaterialTheme.typography.bodyLarge)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = ext.muted)
                        }
                        Text("Needed", style = MaterialTheme.typography.labelMedium, color = ext.muted)
                    }
                } else {
                    com.vachan.app.ui.components.ToggleRow(p.title, p.description, choices[p] == true, { choices[p] = it })
                }
            }
        }

        Section(title = "Language for translation and bio help", subtitle = "App screens are in English for now; AI help can write and translate in these.") {
            ChoiceChips(supportedLanguages, language, { it }, { language = it })
        }

        Notice(title = "What we never do", text = "Sell your data, show your number without your consent, use your photos for ads, or make sales calls.")

        ListGroup(title = "Please confirm") {
            Declaration(intent, { intent = it }, "I'm joining to find a life partner for marriage.")
            ListDivider()
            Declaration(accurate, { accurate = it }, "The information I give will be true and my own.")
            ListDivider()
            Declaration(age, { age = it }, "I'm of legal marriageable age (21+ for men, 18+ for women).")
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PrimaryButton(
                "Agree and continue", { submitting = true; scope.launch { vm.finish(choices.toMap(), language) } },
                Modifier.fillMaxWidth(), enabled = intent && accurate && age, loading = submitting,
            )
            QuietButton("Read the full privacy notice", { showNotice = true }, Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (showNotice) {
        Dialog(onDismissRequest = { showNotice = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                PrivacyNoticeScreen(onBack = { showNotice = false }, closeIcon = true)
            }
        }
    }
}

@Composable
private fun Declaration(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary else AanganColors.extended.card)
                .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
