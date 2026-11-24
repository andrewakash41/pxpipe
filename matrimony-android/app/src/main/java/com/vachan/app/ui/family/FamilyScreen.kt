package com.vachan.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.FamilyManager
import com.vachan.app.core.model.FamilyPermission
import com.vachan.app.data.FamilyRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.Pill
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.profile.ChoiceChips
import com.vachan.app.ui.profile.MultiChips
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(private val family: FamilyRepository) : ViewModel() {
    val managers = family.managers
    val activity = family.activity
    private val _lastInvite = MutableStateFlow<FamilyManager?>(null)
    val lastInvite = _lastInvite.asStateFlow()

    fun invite(name: String, relation: String, perms: Set<FamilyPermission>) = viewModelScope.launch {
        _lastInvite.value = family.invite(name, relation, perms)
    }
    fun update(id: String, perms: Set<FamilyPermission>) = viewModelScope.launch { family.updatePermissions(id, perms) }
    fun remove(id: String) = viewModelScope.launch { family.remove(id) }
    fun dismissInvite() { _lastInvite.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(nav: Nav, vm: FamilyViewModel = hiltViewModel()) {
    val managers by vm.managers.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val lastInvite by vm.lastInvite.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var relation by rememberSaveable { mutableStateOf("Mother") }
    var perms by remember { mutableStateOf(setOf(FamilyPermission.VIEW_MATCHES, FamilyPermission.SUGGEST)) }

    Scaffold(topBar = { TopAppBar(title = { Text("Family Mode") }, navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Banner(
                tone = BannerTone.INFO, title = "Family helps. You decide.",
                text = "Invite a parent or sibling to help with your search. They can never read your chats, reply as you, or accept or decline interests. Every action they take appears below, and you can remove them anytime.",
            )
            SectionCard(title = "Invite a family member") {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Their name") }, modifier = Modifier.fillMaxWidth())
                ChoiceChips(listOf("Mother", "Father", "Sister", "Brother", "Relative"), relation, { it }, { relation = it })
                Text("What can they do?", style = MaterialTheme.typography.labelLarge)
                MultiChips(FamilyPermission.entries, perms, { it.label }, { perms = it })
                Button(onClick = { vm.invite(name, relation, perms); name = "" }, enabled = name.isNotBlank() && perms.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Create invite") }
            }
            if (managers.isNotEmpty()) {
                SectionCard(title = "People helping you") {
                    managers.forEach { m ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.name} · ${m.relation}", style = MaterialTheme.typography.titleSmall)
                                    Text(if (m.accepted) "Active" else "Invite code ${m.inviteCode} · waiting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { vm.remove(m.id) }) { Text("Remove") }
                            }
                            MultiChips(FamilyPermission.entries, m.permissions, { it.label }, { vm.update(m.id, it) })
                        }
                    }
                }
            }
            SectionCard(title = "Activity", subtitle = "Everything family members do on your behalf") {
                if (activity.isEmpty()) Text("No activity yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                activity.sortedByDescending { it.at }.forEach { a ->
                    Row {
                        Pill(SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(a.at)))
                        Text("  ${a.managerName} ${a.action}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    lastInvite?.let { inv ->
        AlertDialog(
            onDismissRequest = vm::dismissInvite,
            title = { Text("Invite ready") },
            text = { Text("Ask ${inv.name} to install Vachan and enter this code under \"I'm helping a family member\":\n\n${inv.inviteCode}\n\nThe code works once and expires in 48 hours.") },
            confirmButton = { TextButton(onClick = vm::dismissInvite) { Text("Done") } },
        )
    }
}
