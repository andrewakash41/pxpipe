package com.vachan.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.Conversation
import com.vachan.app.core.model.Message
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ReportReason
import com.vachan.app.core.safety.ScamDetector
import com.vachan.app.data.AiRepository
import com.vachan.app.data.ChatRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SafetyRepository
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.discover.ReportDialog
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ------------------------------------------------------------------------------- chat list

@HiltViewModel
class ChatListViewModel @Inject constructor(chat: ChatRepository, private val profiles: ProfileRepository) : ViewModel() {
    val conversations = chat.conversations
    val me = profiles.myProfile
    private val _people = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val people = _people.asStateFlow()

    fun ensureLoaded(ids: List<String>) = viewModelScope.launch {
        val missing = ids.filter { it !in _people.value }
        if (missing.isNotEmpty()) _people.value = _people.value + missing.mapNotNull { profiles.getProfile(it) }.associateBy { it.id }
    }
}

@Composable
fun ChatListScreen(nav: Nav, modifier: Modifier = Modifier, vm: ChatListViewModel = hiltViewModel()) {
    val convos by vm.conversations.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    LaunchedEffect(convos) { vm.ensureLoaded(convos.map { it.otherId }) }

    if (convos.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.Chat, "No conversations yet", "When you and someone both say yes, you can chat here for free.", modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item { Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp)) }
        items(convos, key = { it.matchId }) { c ->
            val p = people[c.otherId] ?: return@items
            ConversationRow(c, p) { nav.openChat(c.matchId) }
            HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, p: Profile, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfilePhoto(p, size = 56.dp, shape = RoundedCornerShape(28.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (c.closed) "Conversation closed" else c.lastMessage ?: "Say hello 👋",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            c.lastMessageAt?.let { Text(timeLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (c.unread > 0) Badge { Text("${c.unread}") }
        }
    }
}

private fun timeLabel(t: Long): String {
    val diff = System.currentTimeMillis() - t
    return when {
        diff < TimeUnit.DAYS.toMillis(1) -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
    }
}

// ------------------------------------------------------------------------------ chat thread

data class ChatUi(
    val other: Profile? = null,
    val icebreakers: List<String> = emptyList(),
    val translations: Map<String, String> = emptyMap(),
    val sharedContact: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val profiles: ProfileRepository,
    private val matches: MatchRepository,
    private val ai: AiRepository,
    private val safety: SafetyRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val detector = ScamDetector()
    private val matchId = MutableStateFlow<String?>(null)
    private val _ui = MutableStateFlow(ChatUi())
    val ui = _ui.asStateFlow()
    val me = profiles.myProfile
    val conversations = chat.conversations

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = matchId.flatMapLatest { id -> if (id == null) emptyFlow() else chat.messages(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load(id: String) = viewModelScope.launch {
        matchId.value = id
        chat.markRead(id)
        val convo = chat.conversations.value.firstOrNull { it.matchId == id } ?: return@launch
        val other = profiles.getProfile(convo.otherId) ?: return@launch
        _ui.value = _ui.value.copy(other = other, sharedContact = chat.sharedContact(id))
        matches.compatibilityWith(other.id)?.let { c -> _ui.value = _ui.value.copy(icebreakers = ai.icebreakers(other, c)) }
    }

    fun assess(m: Message, convo: Conversation?): ScamDetector.Assessment {
        val days = convo?.let { TimeUnit.MILLISECONDS.toDays(m.sentAt - it.createdAt).toInt() } ?: 30
        return detector.assess(m.text, days)
    }

    fun nudgeFor(draft: String): String? = if (draft.length < 6) null else detector.outgoingNudge(draft)

    fun send(text: String) = viewModelScope.launch {
        val id = matchId.value ?: return@launch
        chat.sendMessage(id, text).onFailure { _ui.value = _ui.value.copy(message = it.message) }
        chat.markRead(id)
    }

    fun translate(m: Message) = viewModelScope.launch {
        val target = settings.settings.value.language.substringBefore(" (").let { if (it == "English") "English" else it }
        val out = ai.translate(m.text, if (target == "English") "Hindi" else target)
        _ui.value = _ui.value.copy(translations = _ui.value.translations + (m.id to out))
    }

    fun setContactShared(shared: Boolean) = viewModelScope.launch {
        val id = matchId.value ?: return@launch
        chat.setContactShared(id, shared)
        _ui.value = _ui.value.copy(sharedContact = chat.sharedContact(id))
    }

    fun refreshContact() = viewModelScope.launch { matchId.value?.let { _ui.value = _ui.value.copy(sharedContact = chat.sharedContact(it)) } }

    fun report(reason: ReportReason, details: String, block: Boolean) = viewModelScope.launch {
        val other = _ui.value.other ?: return@launch
        safety.report(other.id, reason, details, attachRecentMessages = true)
        if (block) safety.block(other.id)
        _ui.value = _ui.value.copy(message = "Report sent. Our team acknowledges every report within 24 hours.")
    }

    fun block() = viewModelScope.launch { _ui.value.other?.let { safety.block(it.id) } }
    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
    fun markRead() = viewModelScope.launch { matchId.value?.let { chat.markRead(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(matchId: String, nav: Nav, vm: ChatViewModel = hiltViewModel()) {
    LaunchedEffect(matchId) { vm.load(matchId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val convos by vm.conversations.collectAsStateWithLifecycle()
    val convo = convos.firstOrNull { it.matchId == matchId }
    var draft by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        vm.markRead()
    }
    LaunchedEffect(convo?.theySharedContact) { vm.refreshContact() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val other = ui.other

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { other?.let { nav.openProfile(it.id) } }) {
                        other?.let { ProfilePhoto(it, size = 36.dp, shape = RoundedCornerShape(18.dp)) }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(other?.displayName ?: "", style = MaterialTheme.typography.titleMedium)
                            Text(
                                other?.let { if (it.isNri) "${it.location.city} · ${it.location.country}" else it.location.city } ?: "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { other?.let { nav.meetSafely(it.firstName) } }) { Icon(Icons.Filled.Place, "Plan a safe meeting") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (convo?.iSharedContact == true) "Stop sharing my number" else "Share my number") },
                            leadingIcon = { Icon(Icons.Filled.Phone, null) },
                            onClick = { menu = false; vm.setContactShared(convo?.iSharedContact != true) },
                        )
                        DropdownMenuItem(text = { Text("Safety Center") }, leadingIcon = { Icon(Icons.Filled.Shield, null) }, onClick = { menu = false; nav.safety() })
                        DropdownMenuItem(text = { Text("Report") }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = { menu = false; showReport = true })
                        DropdownMenuItem(text = { Text("Block") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = { menu = false; vm.block(); nav.back() })
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().background(MaterialTheme.colorScheme.surface)) {
                val nudge = vm.nudgeFor(draft)
                if (nudge != null) Banner(nudge, tone = BannerTone.CAUTION, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                if (messages.none { it.senderId == me?.id } && ui.icebreakers.isNotEmpty() && convo?.closed != true) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.icebreakers) { s -> AssistChip(onClick = { draft = s }, label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp)) }) }
                    }
                }
                if (convo?.closed == true) {
                    Text("This conversation is closed.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it.take(2000) }, modifier = Modifier.weight(1f),
                            placeholder = { Text("Message") }, maxLines = 4, shape = RoundedCornerShape(24.dp),
                        )
                        IconButton(onClick = { vm.send(draft); draft = "" }, enabled = draft.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Banner(
                    tone = BannerTone.INFO, icon = Icons.Filled.Shield, title = "Safety Copilot is on",
                    text = "Messages are checked on your phone for common scam patterns. Nothing you read is sent anywhere for this.",
                )
            }
            ui.sharedContact?.let { phone ->
                item { Banner(tone = BannerTone.SUCCESS, icon = Icons.Filled.Phone, title = "${other?.firstName} shared their number", text = phone) }
            }
            items(messages, key = { it.id }) { m ->
                if (m.system) {
                    Text(m.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    val mine = m.senderId == me?.id
                    val assessment = if (!mine) vm.assess(m, convo) else null
                    MessageBubble(m, mine, ui.translations[m.id], onTranslate = { vm.translate(m) })
                    if (assessment != null && assessment.level != ScamDetector.Level.NONE) {
                        Banner(
                            tone = if (assessment.level == ScamDetector.Level.HIGH) BannerTone.DANGER else BannerTone.CAUTION,
                            title = if (assessment.level == ScamDetector.Level.HIGH) "This looks like a known scam" else "Be careful",
                            text = assessment.primaryAdvice ?: "",
                            action = if (assessment.level == ScamDetector.Level.HIGH) { { TextButton(onClick = { showReport = true }) { Text("Report and block") } } } else null,
                        )
                    }
                }
            }
        }
    }

    if (showReport && other != null) {
        ReportDialog(other.firstName, onDismiss = { showReport = false }, onSubmit = { r, d, b -> vm.report(r, d, b); showReport = false; if (b) nav.back() })
    }
}

@Composable
private fun MessageBubble(m: Message, mine: Boolean, translation: String?, onTranslate: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                translation?.let {
                    Spacer(Modifier.size(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(timeLabel(m.sentAt), style = MaterialTheme.typography.labelSmall)
                    if (!mine && translation == null) {
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Filled.Translate, "Translate", Modifier.size(20.dp).clickable(onClick = onTranslate))
                    }
                }
            }
        }
    }
}
