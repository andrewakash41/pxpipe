package com.vachan.app.ui.interests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.vachan.app.core.model.Interest
import com.vachan.app.core.model.InterestStatus
import com.vachan.app.core.model.Profile
import com.vachan.app.data.AiRepository
import com.vachan.app.data.InterestRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.Pill
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.discover.canSeePhoto
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val interests: InterestRepository,
    private val profiles: ProfileRepository,
    private val ai: AiRepository,
    private val matches: MatchRepository,
) : ViewModel() {
    val all = interests.interests
    val me = profiles.myProfile
    private val _people = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val people = _people.asStateFlow()
    private val _scores = MutableStateFlow<Map<String, Int>>(emptyMap())
    val scores = _scores.asStateFlow()
    private val _openChat = MutableStateFlow<String?>(null)
    val openChat = _openChat.asStateFlow()

    fun ensureLoaded(ids: Collection<String>) = viewModelScope.launch {
        val missing = ids.filter { it !in _people.value }
        if (missing.isEmpty()) return@launch
        val loaded = missing.mapNotNull { profiles.getProfile(it) }.associateBy { it.id }
        _people.value = _people.value + loaded
        _scores.value = _scores.value + loaded.keys.mapNotNull { id -> matches.compatibilityWith(id)?.let { id to it.score } }
    }

    fun accept(i: Interest) = viewModelScope.launch { interests.accept(i.id).onSuccess { _openChat.value = it } }
    fun decline(i: Interest, message: String?) = viewModelScope.launch { interests.decline(i.id, message) }
    fun withdraw(i: Interest) = viewModelScope.launch { interests.withdraw(i.id) }
    suspend fun draftDecline(name: String) = ai.kindDecline(name)
    fun chatOpened() { _openChat.value = null }
}

@Composable
fun InterestsScreen(nav: Nav, modifier: Modifier = Modifier, vm: InterestsViewModel = hiltViewModel()) {
    val all by vm.all.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    val openChat by vm.openChat.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var declining by remember { mutableStateOf<Interest?>(null) }

    LaunchedEffect(openChat) { openChat?.let { nav.openChat(it); vm.chatOpened() } }
    val myId = me?.id
    val received = all.filter { it.toId == myId && it.status == InterestStatus.PENDING }
    val sent = all.filter { it.fromId == myId }
    val history = all.filter { it.toId == myId && it.status != InterestStatus.PENDING }
    LaunchedEffect(all) { vm.ensureLoaded(all.flatMap { listOf(it.fromId, it.toId) }.filter { it != myId }.toSet()) }

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Received (${received.size})", "Sent", "Responded").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        val list = when (tab) { 0 -> received; 1 -> sent; else -> history }
        if (list.isEmpty()) {
            EmptyState(
                Icons.Filled.FavoriteBorder,
                when (tab) { 0 -> "No new interests"; 1 -> "You haven't sent any interests yet"; else -> "Nothing here yet" },
                when (tab) {
                    0 -> "When someone sends you an interest, you'll see it here. Every interest expires after 7 days, so nobody waits forever."
                    1 -> "Found someone in Daily Picks? Send an interest with a short personal note."
                    else -> "Interests you accept or decline appear here."
                },
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { i ->
                val otherId = if (i.fromId == myId) i.toId else i.fromId
                val p = people[otherId] ?: return@items
                InterestCard(
                    interest = i, other = p, me = me, score = scores[otherId], incoming = i.toId == myId,
                    onOpen = { nav.openProfile(p.id) },
                    onAccept = { vm.accept(i) },
                    onDecline = { declining = i },
                    onWithdraw = { vm.withdraw(i) },
                )
            }
        }
    }

    declining?.let { i ->
        val name = people[i.fromId]?.firstName ?: ""
        DeclineDialog(name, draft = { vm.draftDecline(name) }, onDismiss = { declining = null }, onSend = { msg -> vm.decline(i, msg); declining = null })
    }
}

@Composable
private fun InterestCard(
    interest: Interest, other: Profile, me: Profile?, score: Int?, incoming: Boolean,
    onOpen: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit, onWithdraw: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(other, size = 64.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp), blurred = !canSeePhoto(other, me, interest.status == InterestStatus.ACCEPTED))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${other.displayName}, ${other.age}", style = MaterialTheme.typography.titleMedium)
                    Text("${other.profession} · ${other.location.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    score?.let { Text("$it% two-way compatible", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
                }
                StatusPill(interest)
            }
            interest.note?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (interest.status == InterestStatus.DECLINED && interest.declineMessage != null && !incoming) {
                Text(interest.declineMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (interest.status == InterestStatus.PENDING) {
                val days = TimeUnit.MILLISECONDS.toDays(interest.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
                Text(
                    if (incoming) "Please respond within $days days — a kind \"no\" is better than silence." else "Waiting for a reply · expires in $days days",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (incoming) {
                        Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                        OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline kindly") }
                    } else {
                        TextButton(onClick = onWithdraw) { Text("Withdraw") }
                    }
                }
            } else {
                Spacer(Modifier.size(6.dp))
            }
        }
    }
}

@Composable
private fun StatusPill(i: Interest) {
    val (text, tone) = when (i.status) {
        InterestStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.secondaryContainer
        InterestStatus.ACCEPTED -> "Connected" to MaterialTheme.colorScheme.tertiaryContainer
        InterestStatus.DECLINED -> "Declined" to MaterialTheme.colorScheme.surfaceVariant
        InterestStatus.EXPIRED -> "Expired" to MaterialTheme.colorScheme.surfaceVariant
        InterestStatus.WITHDRAWN -> "Withdrawn" to MaterialTheme.colorScheme.surfaceVariant
    }
    Pill(text, container = tone, content = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun DeclineDialog(name: String, draft: suspend () -> String, onDismiss: () -> Unit, onSend: (String?) -> Unit) {
    var message by remember { mutableStateOf("") }
    LaunchedEffect(name) { message = draft() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decline $name's interest") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A short, kind note gives closure. We drafted one — edit it or send without a note.")
                OutlinedTextField(message, { message = it.take(400) }, minLines = 4, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSend(message.ifBlank { null }) }) { Text("Send kindly") } },
        dismissButton = { TextButton(onClick = { onSend(null) }) { Text("Decline without note") } },
    )
}
