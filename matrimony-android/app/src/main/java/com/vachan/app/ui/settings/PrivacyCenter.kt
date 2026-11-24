package com.vachan.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.AppSettings
import com.vachan.app.core.model.AutoLockTimeout
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.PhotoVisibility
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.data.PrivacyRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.profile.ChoiceChips
import com.vachan.app.ui.root.LocalCanUseLock
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val privacy: PrivacyRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    val state = settings.settings
    val me = profiles.myProfile
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { settings.update(t) }
    fun consent(p: ConsentPurpose, g: Boolean) = viewModelScope.launch { privacy.setConsent(p, g) }

    fun setVisibility(v: ProfileVisibility) = viewModelScope.launch {
        me.value?.let { profiles.saveMyProfile(it.copy(visibility = v)) }
    }

    fun setPhotoVisibility(v: PhotoVisibility) = viewModelScope.launch {
        me.value?.let { profiles.saveMyProfile(it.copy(photoVisibility = v)) }
    }

    suspend fun export(): String = privacy.exportMyData()
    fun requestDeletion() = viewModelScope.launch { privacy.requestDeletion(); _message.value = "Your account will be deleted in 30 days. You can undo this until then." }
    fun cancelDeletion() = viewModelScope.launch { privacy.cancelDeletion(); _message.value = "Deletion cancelled. Welcome back!" }
    fun consumed() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyCenterScreen(nav: Nav, vm: PrivacyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val canUseLock = LocalCanUseLock.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumed() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Privacy Center") }, navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            s.deletionRequestedAt?.let { t ->
                val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t + TimeUnit.DAYS.toMillis(30)))
                Banner(tone = BannerTone.DANGER, title = "Account scheduled for deletion", text = "Everything will be permanently deleted on $date. Your profile is hidden until then.",
                    action = { TextButton(onClick = vm::cancelDeletion) { Text("Keep my account") } })
            }

            SectionCard(title = "Who can see my profile") {
                ProfileVisibility.entries.forEach { v ->
                    RadioRow(v.label, v.description, selected = me?.visibility == v) { vm.setVisibility(v) }
                }
                ToggleRow("Incognito browsing", "View profiles without appearing in their \"who viewed me\"", s.incognito) { v -> vm.update { it.copy(incognito = v) } }
            }

            SectionCard(title = "Who can see my photos") {
                PhotoVisibility.entries.forEach { v ->
                    RadioRow(v.label, v.description, selected = me?.photoVisibility == v) { vm.setPhotoVisibility(v) }
                }
                Text("Screenshots and screen recording are blocked in Vachan, and every photo shows the viewer's member code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(title = "Who can contact me") {
                ToggleRow("Verified members only", "Only people who completed selfie or ID verification can send interests", s.verifiedOnlyContact) { v -> vm.update { it.copy(verifiedOnlyContact = v) } }
                ToggleRow("Require a profile photo", "Hide interests from profiles without a photo", s.requirePhotoToContact) { v -> vm.update { it.copy(requirePhotoToContact = v) } }
                Text("Your phone number is never shown. You choose to share it per conversation and can take it back any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(title = "On this phone", subtitle = "Helpful when the phone is shared with family") {
                var lockError by remember { mutableStateOf<String?>(null) }
                ToggleRow("App lock", "Use fingerprint, face or your screen lock to open Vachan", s.appLockEnabled) { v ->
                    if (v && !canUseLock()) lockError = "Set up a screen lock or fingerprint on your phone first."
                    else { lockError = null; vm.update { it.copy(appLockEnabled = v) } }
                }
                lockError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (s.appLockEnabled) {
                    Text("Lock again", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(AutoLockTimeout.entries, s.autoLock, { it.label }, { t -> vm.update { it.copy(autoLock = t) } })
                }
                ToggleRow("Private notifications", "Hide names and message text on the lock screen", s.privateNotifications) { v -> vm.update { it.copy(privateNotifications = v) } }
            }

            SectionCard(title = "How we use your data", subtitle = "Change any of these at any time") {
                ConsentPurpose.entries.filterNot { it.required }.forEach { p ->
                    ToggleRow(p.title, p.description, s.hasConsent(p)) { vm.consent(p, it) }
                }
                s.consents.values.maxOfOrNull { it.updatedAt }?.let {
                    Text("Last changed " + SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard(title = "Your data") {
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = vm.export()
                        val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, json); putExtra(Intent.EXTRA_SUBJECT, "My Vachan data") }
                        context.startActivity(Intent.createChooser(send, "Save or share your data"))
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, null); Text("  Download my data")
                }
                if (s.deletionRequestedAt == null) {
                    Button(
                        onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Icon(Icons.Filled.Delete, null); Text("  Delete my account") }
                }
                Text(
                    "Deletion removes your profile, photos, chats and preferences after a 30-day undo window. By law we keep a minimal sign-up log (IP address) for one year, separately and access-restricted.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your profile is hidden right away and everything is permanently deleted after 30 days. You can change your mind until then.") },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.requestDeletion() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
