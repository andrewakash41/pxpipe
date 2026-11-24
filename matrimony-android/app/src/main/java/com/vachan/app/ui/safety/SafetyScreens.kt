package com.vachan.app.ui.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.data.SafetyRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.components.SettingsRow
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SafetyViewModel @Inject constructor(private val safety: SafetyRepository, private val settings: SettingsRepository) : ViewModel() {
    val reports = safety.reports
    val blocked = safety.blocked
    val state = settings.settings
    fun unblock(id: String) = viewModelScope.launch { safety.unblock(id) }
    fun saveTrustedContact(name: String, phone: String) = viewModelScope.launch {
        settings.update { it.copy(trustedContactName = name.trim().take(40), trustedContactPhone = phone.filter { c -> c.isDigit() || c == '+' }.take(14)) }
    }
}

private val scamGuide = listOf(
    "\"Gift stuck at customs, pay the fee\"" to "Customs never collects fees over chat or UPI. This is the most common matrimony scam.",
    "Investment, crypto or trading tips" to "A match who teaches you to trade or invest is running a scam — however genuine it feels.",
    "A sudden emergency that needs money" to "Hospital bills, visas, stranded abroad: verify independently and never send money.",
    "Can't video call, works abroad (rig, army, ship)" to "Insist on a live video call here before trusting anyone.",
    "\"Let's move to WhatsApp\" in the first days" to "Staying here keeps reporting, blocking and Safety Copilot on your side.",
    "Asking for OTPs, bank details or ID numbers" to "Never share these with anyone. Real matches never need them.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCenterScreen(nav: Nav, vm: SafetyViewModel = hiltViewModel()) {
    val reports by vm.reports.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Safety Center") }, navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Banner(
                tone = BannerTone.DANGER, title = "In danger right now?", text = "Call 112 (emergency) or 1930 (cyber fraud helpline). You can also report at cybercrime.gov.in.",
                action = {
                    Row {
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }) { Text("Call 112") }
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))) }) { Text("Call 1930") }
                    }
                },
            )
            SectionCard(title = "Meeting someone?") {
                SettingsRow(Icons.Filled.Place, "Plan a safe first meeting", "Share time and place with a trusted contact and set a check-in", onClick = { nav.meetSafely("") })
            }
            SectionCard(title = "Recognise a scam", subtitle = "These scripts are behind most matrimonial fraud in India") {
                scamGuide.forEach { (t, d) ->
                    Column {
                        Text(t, style = MaterialTheme.typography.titleSmall)
                        Text(d, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SectionCard(title = "Your reports", subtitle = "We acknowledge within 24 hours and resolve within 15 days") {
                if (reports.isEmpty()) Text("You haven't reported anyone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                reports.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Flag, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(r.reason.label, style = MaterialTheme.typography.bodyLarge)
                            Text("${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(r.createdAt))} · ${r.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SectionCard(title = "Blocked members") {
                if (blocked.isEmpty()) Text("No one is blocked.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                blocked.forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Member ${id.takeLast(4).uppercase()}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.unblock(id) }) { Text("Unblock") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetSafelyScreen(otherName: String, nav: Nav, vm: SafetyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by rememberSaveable(s.trustedContactName) { mutableStateOf(s.trustedContactName) }
    var phone by rememberSaveable(s.trustedContactPhone) { mutableStateOf(s.trustedContactPhone) }
    var place by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var checkInMinutes by rememberSaveable { mutableStateOf(90) }
    var checkInAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(checkInAt) { while (checkInAt > 0) { now = System.currentTimeMillis(); delay(1_000) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Meet safely") }, navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Banner(tone = BannerTone.INFO, title = "First meetings, done right", text = "Meet in a busy public place, arrange your own travel, keep your phone charged, and tell someone you trust.")
            SectionCard(title = "Your plan") {
                OutlinedTextField(place, { place = it.take(80) }, label = { Text("Where (a public place)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it.take(40) }, label = { Text("When") }, placeholder = { Text("e.g. Sat 5 pm") }, modifier = Modifier.fillMaxWidth())
            }
            SectionCard(title = "Trusted contact") {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        vm.saveTrustedContact(name, phone)
                        val who = otherName.ifBlank { "someone from Vachan" }
                        val text = "Hi $name, I'm meeting $who at ${place.ifBlank { "a public place" }} ${time.ifBlank { "today" }}. I'll check in with you by then. If you don't hear from me, please call me."
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", text))
                    },
                    enabled = phone.length >= 10, modifier = Modifier.fillMaxWidth(),
                ) { Text("Send plan by SMS") }
                Text("We don't see or store your plan; it goes straight from your phone to your contact.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "Check-in timer", subtitle = "If you don't check in, we'll prompt you to alert your contact") {
                if (checkInAt == 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 90, 120).forEach { m ->
                            OutlinedButton(onClick = { checkInMinutes = m }, colors = if (checkInMinutes == m) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()) { Text("$m min") }
                        }
                    }
                    Button(onClick = { checkInAt = System.currentTimeMillis() + checkInMinutes * 60_000L }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Timer, null); Text("  Start check-in timer")
                    }
                } else {
                    val left = ((checkInAt - now) / 1000).coerceAtLeast(0)
                    Text("Check in within %d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.headlineSmall)
                    if (left == 0L) {
                        Banner(tone = BannerTone.DANGER, text = "Time's up. Are you okay?", action = {
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) { Text("Call ${name.ifBlank { "contact" }}") }
                        })
                    }
                    Button(onClick = { checkInAt = 0L }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CheckCircle, null); Text("  I'm safe — stop timer")
                    }
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Call, null); Text("  Emergency: 112")
                    }
                }
            }
        }
    }
}

