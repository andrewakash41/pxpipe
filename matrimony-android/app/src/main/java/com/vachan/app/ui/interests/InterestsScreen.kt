package com.vachan.app.ui.interests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AccentButton
import com.vachan.app.ui.components.AppSnackbarHost
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.Avatar
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.SecondaryButton
import com.vachan.app.ui.components.SegmentedTabs
import com.vachan.app.ui.components.Tag
import com.vachan.app.ui.components.TagTone
import com.vachan.app.ui.discover.canSeePhoto
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
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
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    /** Id of the interest currently being answered, so its buttons show progress and can't double-fire. */
    val busy = _busy.asStateFlow()

    fun ensureLoaded(ids: Collection<String>) = viewModelScope.launch {
        val missing = ids.filter { it !in _people.value }
        if (missing.isEmpty()) return@launch
        val loaded = missing.mapNotNull { runCatching { profiles.getProfile(it) }.getOrNull() }.associateBy { it.id }
        _people.value = _people.value + loaded
        _scores.value = _scores.value + loaded.keys.mapNotNull { id -> runCatching { matches.compatibilityWith(id) }.getOrNull()?.let { id to it.score } }
    }

    fun accept(i: Interest) = viewModelScope.launch {
        if (_busy.value != null) return@launch
        _busy.value = i.id
        interests.accept(i.id).onSuccess { _openChat.value = it }.onFailure { _message.value = it.userMessage() }
        _busy.value = null
    }

    fun decline(i: Interest, message: String?) = viewModelScope.launch {
        interests.decline(i.id, message).onFailure { _message.value = it.userMessage() }
    }

    fun withdraw(i: Interest) = viewModelScope.launch {
        interests.withdraw(i.id)
            .onSuccess { _message.value = "Interest withdrawn." }
            .onFailure { _message.value = it.userMessage() }
    }

    suspend fun draftDecline(name: String): String = runCatching { ai.kindDecline(name) }.getOrDefault("")
    fun chatOpened() { _openChat.value = null }
    fun consumeMessage() { _message.value = null }
}

@Composable
fun InterestsScreen(nav: Nav, modifier: Modifier = Modifier, vm: InterestsViewModel = hiltViewModel()) {
    val all by vm.all.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val scores by vm.scores.collectAsStateWithLifecycle()
    val openChat by vm.openChat.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var declining by remember { mutableStateOf<Interest?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(openChat) { openChat?.let { nav.openChat(it); vm.chatOpened() } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val myId = me?.id
    val received = all.filter { it.toId == myId && it.status == InterestStatus.PENDING }
    val sent = all.filter { it.fromId == myId }.sortedByDescending { it.createdAt }
    val history = all.filter { it.toId == myId && it.status != InterestStatus.PENDING }.sortedByDescending { it.createdAt }
    LaunchedEffect(all) { vm.ensureLoaded(all.flatMap { listOf(it.fromId, it.toId) }.filter { it != myId }.toSet()) }

    Box(modifier) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenTitle("Interests", eyebrow = "People who'd like to meet", subtitle = "Every interest expires in 7 days, so nobody waits forever. A kind no beats silence.")
            }
            item {
                SegmentedTabs(
                    listOf(if (received.isEmpty()) "Received" else "Received · ${received.size}", "Sent", "History"),
                    selected = tab, onSelect = { tab = it },
                )
            }
            val list = when (tab) { 0 -> received; 1 -> sent; else -> history }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.FavoriteBorder,
                        when (tab) { 0 -> "No new interests"; 1 -> "Nothing sent yet"; else -> "Nothing here yet" },
                        when (tab) {
                            0 -> "When someone sends you an interest, it appears here with their note."
                            1 -> "Found someone in Today? Send an interest with a short, personal note."
                            else -> "Interests you accept or decline appear here."
                        },
                    )
                }
            }
            items(list, key = { it.id }) { i ->
                val otherId = if (i.fromId == myId) i.toId else i.fromId
                val p = people[otherId] ?: return@items
                InterestCard(
                    interest = i, other = p, me = me, score = scores[otherId], incoming = i.toId == myId,
                    busy = busy == i.id,
                    onOpen = { nav.openProfile(p.id) },
                    onAccept = { vm.accept(i) },
                    onDecline = { declining = i },
                    onWithdraw = { vm.withdraw(i) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    declining?.let { i ->
        val name = people[i.fromId]?.firstName ?: ""
        DeclineSheet(name, draft = { vm.draftDecline(name) }, onDismiss = { declining = null }, onSend = { msg -> vm.decline(i, msg); declining = null })
    }
}

@Composable
private fun InterestCard(
    interest: Interest, other: Profile, me: Profile?, score: Int?, incoming: Boolean, busy: Boolean,
    onOpen: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit, onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = AanganColors.extended
    PaperCard(modifier, onClick = onOpen, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(other, size = 60.dp, blurred = !canSeePhoto(other, me, interest.status == InterestStatus.ACCEPTED))
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${other.firstName}, ${other.age}", fontFamily = DisplayFamily, fontSize = 26.sp)
                Text("${other.profession} · ${other.location.city}", style = MaterialTheme.typography.bodySmall, color = ext.muted)
                score?.let { Text("$it% two-way match", style = MaterialTheme.typography.labelMedium, color = ext.accent) }
            }
            StatusTag(interest)
        }
        interest.note?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(2.dp).height(40.dp).background(ext.accent))
                Text(it, fontFamily = DisplayFamily, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 24.sp)
            }
        }
        if (interest.status == InterestStatus.DECLINED && interest.declineMessage != null && !incoming) {
            Text(interest.declineMessage, style = MaterialTheme.typography.bodyMedium, color = ext.muted)
        }
        if (interest.status == InterestStatus.PENDING) {
            val days = TimeUnit.MILLISECONDS.toDays(interest.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
            val left = if (days == 0L) "today" else if (days == 1L) "in 1 day" else "in $days days"
            Text(
                if (incoming) "Please reply — this expires $left." else "Waiting for a reply · expires $left",
                style = MaterialTheme.typography.bodySmall, color = ext.muted,
            )
            if (incoming) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Decline kindly", onDecline, Modifier.weight(1f), enabled = !busy)
                    AccentButton("Accept", onAccept, Modifier.weight(1f), loading = busy, icon = Icons.Outlined.Check)
                }
            } else {
                QuietButton("Withdraw interest", onWithdraw, color = ext.muted)
            }
        }
    }
}

@Composable
private fun StatusTag(i: Interest) {
    when (i.status) {
        InterestStatus.PENDING -> Tag("Pending")
        InterestStatus.ACCEPTED -> Tag("Connected", tone = TagTone.VERIFIED)
        InterestStatus.DECLINED -> Tag("Declined")
        InterestStatus.EXPIRED -> Tag("Expired")
        InterestStatus.WITHDRAWN -> Tag("Withdrawn")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeclineSheet(name: String, draft: suspend () -> String, onDismiss: () -> Unit, onSend: (String?) -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    // Prefill with a kind draft, but never overwrite what the member has started typing.
    LaunchedEffect(name) { val d = draft(); if (!touched && message.isEmpty()) message = d }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (name.isBlank()) "Decline kindly" else "Decline $name kindly", style = MaterialTheme.typography.headlineMedium)
            Text("A short, warm note gives closure. We drafted one — edit it, or decline without a note.", style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted)
            AppTextField(message, { touched = true; message = it.take(400) }, singleLine = false, minLines = 4, supporting = "${message.length}/400")
            PrimaryButton("Send note and decline", { onSend(message.ifBlank { null }) }, Modifier.fillMaxWidth())
            SecondaryButton("Decline without a note", { onSend(null) }, Modifier.fillMaxWidth())
            Box(Modifier.height(4.dp))
        }
    }
}
