package com.vachan.app.ui.family

import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.FamilyManager
import com.vachan.app.core.model.FamilyPermission
import com.vachan.app.data.FamilyRepository
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.common.copySensitive
import com.vachan.app.ui.common.relativeTime
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppSnackbarHost
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.ChoiceChips
import com.vachan.app.ui.components.FieldLabel
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.MultiChips
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.Section
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
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(private val family: FamilyRepository) : ViewModel() {
    val managers = family.managers
    val activity = family.activity
    private val _lastInvite = MutableStateFlow<FamilyManager?>(null)
    val lastInvite = _lastInvite.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun invite(name: String, relation: String, perms: Set<FamilyPermission>, onDone: () -> Unit) = viewModelScope.launch {
        _busy.value = true
        family.invite(name, relation, perms)
            .onSuccess { _lastInvite.value = it; onDone() }
            .onFailure { _message.value = it.userMessage() }
        _busy.value = false
    }
    fun update(id: String, perms: Set<FamilyPermission>) = viewModelScope.launch { family.updatePermissions(id, perms).onFailure { _message.value = it.userMessage() } }
    fun remove(id: String) = viewModelScope.launch { family.remove(id).onFailure { _message.value = it.userMessage() } }
    fun dismissInvite() { _lastInvite.value = null }
    fun consumeMessage() { _message.value = null }
}

@Composable
fun FamilyScreen(nav: Nav, vm: FamilyViewModel = hiltViewModel()) {
    val managers by vm.managers.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val lastInvite by vm.lastInvite.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var name by rememberSaveable { mutableStateOf("") }
    var relation by rememberSaveable { mutableStateOf("Mother") }
    var perms by remember { mutableStateOf(setOf(FamilyPermission.VIEW_MATCHES, FamilyPermission.SUGGEST)) }
    var removing by remember { mutableStateOf<FamilyManager?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val ext = AanganColors.extended

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AppSnackbarHost(snackbar) }, topBar = { TopBar(onBack = nav.back) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScreenTitle("Family Mode", subtitle = "Family helps. You decide.")
            Notice(
                text = "Invite a parent or sibling to help. They can never read your chats, reply as you, or accept or decline interests. Everything they do appears below, and you can remove them anytime.",
            )
            Section(title = "Invite someone") {
                AppTextField(name, { name = it.take(40) }, label = "Their name")
                FieldLabel("They are your")
                ChoiceChips(listOf("Mother", "Father", "Sister", "Brother", "Relative"), relation, { it }, { relation = it })
                FieldLabel("They can")
                MultiChips(FamilyPermission.entries, perms, { it.label }, { perms = it })
                PrimaryButton(
                    "Create invite", { vm.invite(name, relation, perms) { name = "" } }, Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && perms.isNotEmpty(), loading = busy, icon = Icons.Outlined.PersonAdd,
                )
            }
            if (managers.isNotEmpty()) {
                Section(title = "People helping you") {
                    managers.forEachIndexed { i, m ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(color = ext.hairline)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.name} · ${m.relation}", style = MaterialTheme.typography.titleMedium)
                                    Text(if (m.accepted) "Active" else "Invite code ${m.inviteCode} · waiting", style = MaterialTheme.typography.bodySmall, color = ext.muted)
                                }
                                if (m.accepted) Tag("Active", tone = TagTone.VERIFIED) else Tag("Pending")
                            }
                            MultiChips(FamilyPermission.entries, m.permissions, { it.label }, { vm.update(m.id, it) })
                            QuietButton("Remove ${m.name}", { removing = m }, color = ext.danger)
                        }
                    }
                }
            }
            Section(title = "Activity", subtitle = "Everything family members do on your behalf") {
                if (activity.isEmpty()) Text("No activity yet.", color = ext.muted)
                activity.sortedByDescending { it.at }.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(8.dp).background(ext.accent, CircleShape))
                        Text("${a.managerName} ${a.action}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(relativeTime(a.at), style = MaterialTheme.typography.labelSmall, color = ext.muted)
                    }
                }
            }
        }
    }

    lastInvite?.let { inv ->
        AlertDialog(
            onDismissRequest = vm::dismissInvite,
            containerColor = ext.card,
            title = { Text("Invite ready", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ask ${inv.name} to install ${Brand.NAME} and enter this code under \"I'm helping a family member\". It works once and expires in 48 hours.")
                    PaperCard(padding = 16.dp) {
                        Text(inv.inviteCode.chunked(4).joinToString(" "), fontFamily = DisplayFamily, fontSize = 34.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Copy", { copySensitive(context, "Invite code", inv.inviteCode) }, Modifier.weight(1f), icon = Icons.Outlined.ContentCopy)
                        SecondaryButton("Share", {
                            val text = "I've invited you to help with my ${Brand.NAME} search. Install ${Brand.NAME} and enter this code under \"I'm helping a family member\": ${inv.inviteCode} (valid for 48 hours)."
                            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share invite")) }
                        }, Modifier.weight(1f), icon = Icons.Outlined.Share)
                    }
                }
            },
            confirmButton = { QuietButton("Done", vm::dismissInvite) },
        )
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = ext.card,
            title = { Text("Remove ${m.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("They'll lose access straight away. You can invite them again later.") },
            confirmButton = { QuietButton("Remove", { vm.remove(m.id); removing = null }, color = ext.danger) },
            dismissButton = { QuietButton("Cancel", { removing = null }) },
        )
    }
}
