package com.vachan.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppSnackbarHost
import com.vachan.app.ui.components.ChoiceChips
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.ListRow
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeAction
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.RadioRow
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.ToggleRow
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.onboarding.supportedLanguages
import com.vachan.app.ui.root.LocalCanUseLock
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { settings.update(t) }
    fun consent(p: ConsentPurpose, g: Boolean) = viewModelScope.launch { privacy.setConsent(p, g) }

    private fun saveVisibility(v: ProfileVisibility, then: suspend () -> Unit = {}) = viewModelScope.launch {
        val p = me.value ?: return@launch
        profiles.saveMyProfile(p.copy(visibility = v))
            .onSuccess { then() }
            .onFailure { _message.value = it.userMessage() }
    }

    fun setVisibility(v: ProfileVisibility) = saveVisibility(v) { settings.update { it.copy(visibilityBeforePause = null) } }

    fun setPhotoVisibility(v: PhotoVisibility) = viewModelScope.launch {
        val p = me.value ?: return@launch
        profiles.saveMyProfile(p.copy(photoVisibility = v)).onFailure { _message.value = it.userMessage() }
    }

    /** Pausing hides the profile everywhere but remembers the member's own choice for later. */
    fun setPaused(paused: Boolean) {
        val current = me.value?.visibility ?: return
        if (paused) {
            saveVisibility(ProfileVisibility.HIDDEN) {
                settings.update { it.copy(visibilityBeforePause = current.takeIf { v -> v != ProfileVisibility.HIDDEN } ?: ProfileVisibility.EVERYONE) }
                _message.value = "Paused. You won't appear to anyone new; your chats continue."
            }
        } else {
            val restore = state.value.visibilityBeforePause ?: ProfileVisibility.EVERYONE
            saveVisibility(restore) {
                settings.update { it.copy(visibilityBeforePause = null) }
                _message.value = "Welcome back — you're visible again."
            }
        }
    }

    /** Writes the export straight into a file the member chose (Storage Access Framework). */
    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _busy.value = true
        privacy.exportMyData()
            .mapCatching { json ->
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToByteArray()) } ?: error("We couldn't write that file.")
                }
            }
            .onSuccess { _message.value = "Saved. Keep this file somewhere private — it contains your personal data." }
            .onFailure { _message.value = it.userMessage("We couldn't export your data. Please try again.") }
        _busy.value = false
    }

    fun requestDeletion() = viewModelScope.launch {
        privacy.requestDeletion()
            .onSuccess { _message.value = "Your account will be deleted in 30 days. You can undo this until then." }
            .onFailure { _message.value = it.userMessage() }
    }

    fun cancelDeletion() = viewModelScope.launch {
        privacy.cancelDeletion()
            .onSuccess { _message.value = "Deletion cancelled. Welcome back!" }
            .onFailure { _message.value = it.userMessage() }
    }

    fun consumed() { _message.value = null }
}

@Composable
fun PrivacyCenterScreen(nav: Nav, vm: PrivacyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canUseLock = LocalCanUseLock.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumed() } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(context.contentResolver, uri)
    }
    val paused = me?.visibility == ProfileVisibility.HIDDEN
    val ext = AanganColors.extended

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = { TopBar(onBack = nav.back) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            ScreenTitle("Privacy Center", subtitle = "You decide who sees you, what we use, and what stays on this phone.")

            s.deletionRequestedAt?.let { t ->
                val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t + TimeUnit.DAYS.toMillis(30)))
                Notice(
                    tone = NoticeTone.DANGER, title = "Account scheduled for deletion",
                    text = "Everything will be permanently deleted on $date. Your profile is hidden until then.",
                    action = { NoticeAction("Keep my account", vm::cancelDeletion) },
                )
            }

            ListGroup(title = "Visibility", footer = if (paused) "Paused: hidden from introductions and search. Existing chats continue." else null) {
                ToggleRow("Pause my profile", "Take a break without deleting anything", paused, { vm.setPaused(it) }, icon = Icons.Outlined.PauseCircle)
                ListDivider()
                listOf(ProfileVisibility.EVERYONE, ProfileVisibility.VERIFIED_ONLY).forEachIndexed { i, v ->
                    if (i > 0) ListDivider()
                    RadioRow(v.label, v.description, selected = !paused && me?.visibility == v, onClick = { vm.setVisibility(v) })
                }
                ListDivider()
                ToggleRow("Browse privately", "View profiles without appearing in their \"who viewed me\"", s.incognito, { v -> vm.update { it.copy(incognito = v) } }, icon = Icons.Outlined.VisibilityOff)
            }

            ListGroup(title = "Who can see my photos", footer = "Screenshots and screen recording are blocked in ${Brand.NAME}, and every photo carries the viewer's member code.") {
                PhotoVisibility.entries.forEachIndexed { i, v ->
                    if (i > 0) ListDivider()
                    RadioRow(v.label, v.description, selected = me?.photoVisibility == v, onClick = { vm.setPhotoVisibility(v) })
                }
            }

            ListGroup(title = "Who can contact me", footer = "Your phone number is never shown. You choose to share it per conversation and can take it back any time.") {
                ToggleRow("Verified members only", "Only people who completed selfie or ID verification can send interests", s.verifiedOnlyContact, { v -> vm.update { it.copy(verifiedOnlyContact = v) } })
                ListDivider()
                ToggleRow("Require a profile photo", "Hide interests from profiles without a photo", s.requirePhotoToContact, { v -> vm.update { it.copy(requirePhotoToContact = v) } })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListGroup(title = "On this phone", footer = lockError) {
                    ToggleRow("App lock", "Fingerprint, face or screen lock to open ${Brand.NAME}", s.appLockEnabled, { v ->
                        if (v && !canUseLock()) lockError = "Set up a screen lock or fingerprint on your phone first."
                        else { lockError = null; vm.update { it.copy(appLockEnabled = v) } }
                    }, icon = Icons.Outlined.Fingerprint)
                    ListDivider()
                    ToggleRow("Private notifications", "No names or message text on the lock screen", s.privateNotifications, { v -> vm.update { it.copy(privateNotifications = v) } }, icon = Icons.Outlined.NotificationsOff)
                }
                if (s.appLockEnabled) {
                    Text("Lock again", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                    ChoiceChips(AutoLockTimeout.entries, s.autoLock, { it.label }, { t -> vm.update { it.copy(autoLock = t) } })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListGroup(title = "How we use your data", footer = s.consents.values.maxOfOrNull { it.updatedAt }?.let { "Last changed " + SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(it)) }) {
                    ConsentPurpose.entries.filterNot { it.required }.forEachIndexed { i, p ->
                        if (i > 0) ListDivider()
                        ToggleRow(p.title, p.description, s.hasConsent(p), { vm.consent(p, it) })
                    }
                }
                Text("Language for translation and bio help", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                ChoiceChips(supportedLanguages, s.language, { it }, { l -> vm.update { it.copy(language = l) } })
            }

            ListGroup(title = "Your data") {
                ListRow(
                    "Download my data", subtitle = if (busy) "Preparing your file…" else "Everything we hold about you, as a JSON file",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { if (!busy) exportLauncher.launch("${Brand.NAME.lowercase()}-my-data-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json") },
                )
                ListDivider(inset = 66.dp)
                ListRow("Read the privacy notice", subtitle = "Plain words, no legalese", icon = Icons.Outlined.Description, onClick = nav.privacyNotice)
                if (s.deletionRequestedAt == null) {
                    ListDivider(inset = 66.dp)
                    ListRow("Delete my account", subtitle = "30 days to change your mind", icon = Icons.Outlined.DeleteOutline, destructive = true, onClick = { confirmDelete = true })
                }
            }
            Text(
                "Deletion removes your profile, photos, chats and preferences after a 30-day undo window. By law we keep a minimal sign-up log (IP address) for one year, separately and access-restricted.",
                style = MaterialTheme.typography.bodySmall, color = ext.muted,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = ext.card,
            title = { Text("Delete your account?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Your profile is hidden right away and everything is permanently deleted after 30 days. You can change your mind until then.") },
            confirmButton = { QuietButton("Delete", { confirmDelete = false; vm.requestDeletion() }, color = ext.danger) },
            dismissButton = { QuietButton("Cancel", { confirmDelete = false }) },
        )
    }
}
