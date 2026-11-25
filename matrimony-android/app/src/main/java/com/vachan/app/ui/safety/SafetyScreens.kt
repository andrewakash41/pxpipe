package com.vachan.app.ui.safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.safety.CheckInScheduler
import com.vachan.app.data.SafetyRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.ChoiceChips
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.ListRow
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeAction
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.SecondaryButton
import com.vachan.app.ui.components.Section
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Alarm side effects for the check-in, behind an interface so tests don't touch AlarmManager. */
interface CheckInAlarms {
    fun schedule(context: Context, atMillis: Long)
    fun cancel(context: Context)

    object Device : CheckInAlarms {
        override fun schedule(context: Context, atMillis: Long) = CheckInScheduler.schedule(context, atMillis)
        override fun cancel(context: Context) = CheckInScheduler.cancel(context)
    }
}

@HiltViewModel
class SafetyViewModel @Inject constructor(private val safety: SafetyRepository, private val settings: SettingsRepository) : ViewModel() {
    var alarms: CheckInAlarms = CheckInAlarms.Device
    val reports = safety.reports
    val blocked = safety.blocked
    val state = settings.settings
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun unblock(id: String) = viewModelScope.launch { safety.unblock(id).onFailure { _message.value = it.userMessage() } }

    fun saveTrustedContact(name: String, phone: String) = viewModelScope.launch {
        settings.update { it.copy(trustedContactName = name.trim().take(40), trustedContactPhone = phone.filter { c -> c.isDigit() || c == '+' }.take(16)) }
    }

    /** Persists the deadline (survives leaving the screen and app restarts) and sets an alarm. */
    fun startCheckIn(context: Context, minutes: Int) = viewModelScope.launch {
        val at = System.currentTimeMillis() + minutes * 60_000L
        settings.update { it.copy(checkInAt = at) }
        alarms.schedule(context.applicationContext, at)
    }

    fun stopCheckIn(context: Context) = viewModelScope.launch {
        settings.update { it.copy(checkInAt = null) }
        alarms.cancel(context.applicationContext)
    }

    fun consumeMessage() { _message.value = null }
}

private val scamGuide = listOf(
    "\"Gift stuck at customs, pay the fee\"" to "Customs never collects fees over chat or UPI. This is the most common matrimony scam.",
    "Investment, crypto or trading tips" to "A match who teaches you to trade or invest is running a scam — however genuine it feels.",
    "A sudden emergency that needs money" to "Hospital bills, visas, stranded abroad: verify independently and never send money.",
    "Can't video call, works abroad (rig, army, ship)" to "Insist on a live video call before trusting anyone.",
    "\"Let's move to WhatsApp\" in the first days" to "Staying here keeps reporting, blocking and Safety Copilot on your side.",
    "Asking for OTPs, bank details or ID numbers" to "Never share these with anyone. Real matches never need them.",
)

private fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
}

@Composable
fun SafetyCenterScreen(nav: Nav, vm: SafetyViewModel = hiltViewModel()) {
    val reports by vm.reports.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { TopBar(onBack = nav.back) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            ScreenTitle("Safety Center", subtitle = "Calm, practical help — before, during and after you meet someone.")
            Notice(
                tone = NoticeTone.DANGER, title = "In danger right now?",
                text = "Call 112 for emergencies or 1930 for the cyber-fraud helpline. You can also report at cybercrime.gov.in.",
                action = {
                    NoticeAction("Call 112", { dial(context, "112") }, color = AanganColors.extended.danger)
                    NoticeAction("Call 1930", { dial(context, "1930") }, color = AanganColors.extended.danger)
                },
            )
            s.checkInAt?.let { at ->
                Notice(
                    tone = NoticeTone.SUCCESS, icon = Icons.Outlined.Timer, title = "Check-in active",
                    text = "Due at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))}.",
                    action = { NoticeAction("Open", { nav.meetSafely("") }) },
                )
            }
            ListGroup(title = "Before you meet") {
                ListRow("Plan a safe first meeting", subtitle = "Share the plan with someone you trust and set a check-in", icon = Icons.Outlined.Place, onClick = { nav.meetSafely("") })
            }
            Section(title = "Recognise a scam", subtitle = "These scripts are behind most matrimonial fraud in India") {
                scamGuide.forEachIndexed { i, (t, d) ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = AanganColors.extended.hairline)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(t, style = MaterialTheme.typography.titleSmall)
                        Text(d, style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted)
                    }
                }
            }
            ListGroup(title = "Your reports", footer = "We acknowledge within 24 hours and resolve within 15 days.") {
                if (reports.isEmpty()) ListRow("You haven't reported anyone", subtitle = "Report from any profile or chat — it's confidential")
                reports.sortedByDescending { it.createdAt }.forEachIndexed { i, r ->
                    if (i > 0) ListDivider(inset = 66.dp)
                    ListRow(r.reason.label, subtitle = "${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(r.createdAt))} · ${r.status}", icon = Icons.Outlined.Flag)
                }
            }
            ListGroup(title = "Blocked members") {
                if (blocked.isEmpty()) ListRow("No one is blocked")
                blocked.forEachIndexed { i, id ->
                    if (i > 0) ListDivider()
                    ListRow("Member ···${id.takeLast(4).uppercase()}", trailing = { QuietButton("Unblock", { vm.unblock(id) }) })
                }
            }
        }
    }
}

@Composable
fun MeetSafelyScreen(otherName: String, nav: Nav, vm: SafetyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by rememberSaveable(s.trustedContactName) { mutableStateOf(s.trustedContactName) }
    var phone by rememberSaveable(s.trustedContactPhone) { mutableStateOf(s.trustedContactPhone) }
    var place by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(90) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val checkInAt = s.checkInAt
    LaunchedEffect(checkInAt) { while (checkInAt != null) { now = System.currentTimeMillis(); delay(1_000) } }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startCheckIn(context, minutes) }
    val phoneOk = phone.count { it.isDigit() } >= 8

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { TopBar(onBack = nav.back) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScreenTitle("Meet safely", subtitle = "Busy public place, your own travel, phone charged — and someone who knows where you are.")

            if (checkInAt != null) {
                val left = ((checkInAt - now) / 1000).coerceAtLeast(0)
                PaperCard {
                    Eyebrow("Check-in")
                    Text(
                        if (left > 0) "%d:%02d".format(left / 60, left % 60) else "Time to check in",
                        fontFamily = DisplayFamily, style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        if (left > 0) "We'll remind you even if you close ${Brand.NAME}." else "Are you okay? If not, call your contact or 112.",
                        style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted,
                    )
                    PrimaryButton("I'm safe — stop check-in", { vm.stopCheckIn(context) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.CheckCircle)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (phoneOk) SecondaryButton("Call ${name.ifBlank { "contact" }}", { dial(context, phone) }, Modifier.weight(1f), icon = Icons.Outlined.Call)
                        SecondaryButton("Call 112", { dial(context, "112") }, Modifier.weight(1f), contentColor = AanganColors.extended.danger)
                    }
                }
            }

            Section(title = "Your plan") {
                AppTextField(place, { place = it.take(80) }, label = "Where (a public place)")
                AppTextField(time, { time = it.take(40) }, label = "When", placeholder = "e.g. Sat 5 pm")
            }
            Section(title = "Someone you trust") {
                AppTextField(name, { name = it.take(40) }, label = "Name")
                AppTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(18) }, label = "Phone", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                PrimaryButton(
                    "Send plan by SMS",
                    {
                        vm.saveTrustedContact(name, phone)
                        val who = otherName.ifBlank { "someone from ${Brand.NAME}" }
                        val text = "Hi ${name.ifBlank { "there" }}, I'm meeting $who at ${place.ifBlank { "a public place" }} ${time.ifBlank { "today" }}. I'll check in with you after. If you don't hear from me, please call me."
                        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.filter { it.isDigit() || it == '+' }}")).putExtra("sms_body", text)) }
                    },
                    Modifier.fillMaxWidth(), enabled = phoneOk, icon = Icons.Outlined.Sms,
                )
                Text("Your plan goes straight from your phone to your contact. We don't see or store it.", style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
            }
            if (checkInAt == null) {
                Section(title = "Check-in timer", subtitle = "If you don't check in, we'll remind you to call your contact") {
                    ChoiceChips(listOf(60, 90, 120, 180), minutes, { "$it min" }, { minutes = it })
                    PrimaryButton(
                        "Start check-in",
                        {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !CheckInScheduler.canNotify(context)) {
                                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else vm.startCheckIn(context, minutes)
                        },
                        Modifier.fillMaxWidth(), icon = Icons.Outlined.Timer,
                    )
                }
            }
        }
    }
}
