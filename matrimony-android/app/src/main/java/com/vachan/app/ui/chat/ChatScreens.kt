package com.vachan.app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
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
import com.vachan.app.ui.common.copySensitive
import com.vachan.app.ui.common.relativeTime
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppSnackbarHost
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.Avatar
import com.vachan.app.ui.components.CircleIconButton
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeAction
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.SecondaryButton
import com.vachan.app.ui.components.SelectChip
import com.vachan.app.ui.discover.BlockDialog
import com.vachan.app.ui.discover.ReportSheet
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ------------------------------------------------------------------------------- chat list

@HiltViewModel
class ChatListViewModel @Inject constructor(chat: ChatRepository, private val profiles: ProfileRepository) : ViewModel() {
    val conversations = chat.conversations
    private val _people = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val people = _people.asStateFlow()

    fun ensureLoaded(ids: List<String>) = viewModelScope.launch {
        val missing = ids.filter { it !in _people.value }
        if (missing.isNotEmpty()) _people.value = _people.value + missing.mapNotNull { runCatching { profiles.getProfile(it) }.getOrNull() }.associateBy { it.id }
    }
}

@Composable
fun ChatListScreen(nav: Nav, modifier: Modifier = Modifier, vm: ChatListViewModel = hiltViewModel()) {
    val convos by vm.conversations.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    LaunchedEffect(convos) { vm.ensureLoaded(convos.map { it.otherId }) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenTitle("Chats", eyebrow = "Conversations", subtitle = "Free once you both say yes. Your number stays private unless you share it.") }
        if (convos.isEmpty()) {
            item { EmptyState(Icons.AutoMirrored.Filled.Chat, "No conversations yet", "When you and someone both say yes, you can talk here — for free, and protected by Safety Copilot.") }
        } else {
            item {
                ListGroup {
                    convos.forEachIndexed { i, c ->
                        val p = people[c.otherId] ?: return@forEachIndexed
                        if (i > 0) ListDivider(inset = 84.dp)
                        ConversationRow(c, p) { nav.openChat(c.matchId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, p: Profile, onClick: () -> Unit) {
    val ext = AanganColors.extended
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open chat with ${p.firstName}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p, size = 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (c.closed) "Conversation closed" else c.lastMessage ?: "Say hello 👋",
                style = MaterialTheme.typography.bodyMedium,
                color = if (c.unread > 0) MaterialTheme.colorScheme.onSurface else ext.muted,
                fontStyle = if (c.closed) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.lastMessageAt?.let { Text(relativeTime(it), style = MaterialTheme.typography.labelSmall, color = ext.muted) }
            if (c.unread > 0) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(ext.accent), contentAlignment = Alignment.Center) {
                    Text("${c.unread}", style = MaterialTheme.typography.labelSmall, color = ext.onAccent)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------ chat thread

data class ChatUi(
    val other: Profile? = null,
    val icebreakers: List<String> = emptyList(),
    val translations: Map<String, String> = emptyMap(),
    val sharedContact: String? = null,
    val message: String? = null,
    val leave: Boolean = false,
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
        runCatching { chat.markRead(id) }
        val convo = chat.conversations.value.firstOrNull { it.matchId == id } ?: return@launch
        val other = runCatching { profiles.getProfile(convo.otherId) }.getOrNull() ?: return@launch
        _ui.value = _ui.value.copy(other = other, sharedContact = runCatching { chat.sharedContact(id) }.getOrNull())
        runCatching { matches.compatibilityWith(other.id) }.getOrNull()?.let { c ->
            _ui.value = _ui.value.copy(icebreakers = runCatching { ai.icebreakers(other, c) }.getOrDefault(emptyList()))
        }
    }

    fun assess(m: Message, convo: Conversation?): ScamDetector.Assessment {
        val days = convo?.let { TimeUnit.MILLISECONDS.toDays(m.sentAt - it.createdAt).toInt() } ?: 30
        return detector.assess(m.text, days)
    }

    fun nudgeFor(draft: String): String? = if (draft.length < 6) null else detector.outgoingNudge(draft)

    fun send(text: String, onFailure: (String) -> Unit) = viewModelScope.launch {
        val id = matchId.value ?: return@launch
        chat.sendMessage(id, text).onFailure { onFailure(text); _ui.value = _ui.value.copy(message = it.userMessage("Couldn't send. Check your connection and try again.")) }
        runCatching { chat.markRead(id) }
    }

    fun translate(m: Message) = viewModelScope.launch {
        val target = settings.settings.value.language.substringBefore(" (").let { if (it == "English") "English" else it }
        val out = runCatching { ai.translate(m.text, if (target == "English") "Hindi" else target) }.getOrNull() ?: return@launch
        _ui.value = _ui.value.copy(translations = _ui.value.translations + (m.id to out))
    }

    fun setContactShared(shared: Boolean) = viewModelScope.launch {
        val id = matchId.value ?: return@launch
        chat.setContactShared(id, shared)
            .onSuccess { _ui.value = _ui.value.copy(message = if (shared) "Your number is shared in this chat only. You can take it back any time." else "You stopped sharing your number.") }
            .onFailure { _ui.value = _ui.value.copy(message = it.userMessage()) }
    }

    fun refreshContact() = viewModelScope.launch {
        matchId.value?.let { _ui.value = _ui.value.copy(sharedContact = runCatching { chat.sharedContact(it) }.getOrNull()) }
    }

    fun report(reason: ReportReason, details: String, block: Boolean) = viewModelScope.launch {
        val other = _ui.value.other ?: return@launch
        val reported = safety.report(other.id, reason, details, attachRecentMessages = true)
        val blocked = if (block) safety.block(other.id) else Result.success(Unit)
        val failure = reported.exceptionOrNull() ?: blocked.exceptionOrNull()
        _ui.value = if (failure != null) _ui.value.copy(message = failure.userMessage())
        else _ui.value.copy(message = "Report sent. Our team acknowledges every report within 24 hours.", leave = block)
    }

    fun block() = viewModelScope.launch {
        val other = _ui.value.other ?: return@launch
        safety.block(other.id)
            .onSuccess { _ui.value = _ui.value.copy(leave = true) }
            .onFailure { _ui.value = _ui.value.copy(message = it.userMessage()) }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
    fun markRead() = viewModelScope.launch { matchId.value?.let { runCatching { chat.markRead(it) } } }
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
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        vm.markRead()
    }
    LaunchedEffect(convo?.theySharedContact) { vm.refreshContact() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(ui.leave) { if (ui.leave) nav.back() }
    val other = ui.other

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                com.vachan.app.ui.components.TopBar(onBack = nav.back) {
                    CircleIconButton(Icons.Outlined.Place, "Plan a safe meeting", { nav.meetSafely(other?.firstName ?: "") })
                    Box {
                        CircleIconButton(Icons.Filled.MoreHoriz, "More options", { menu = true })
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (convo?.closed != true) {
                                DropdownMenuItem(
                                    text = { Text(if (convo?.iSharedContact == true) "Stop sharing my number" else "Share my number") },
                                    leadingIcon = { Icon(if (convo?.iSharedContact == true) Icons.Outlined.PhoneDisabled else Icons.Outlined.Phone, null) },
                                    onClick = { menu = false; vm.setContactShared(convo?.iSharedContact != true) },
                                )
                            }
                            DropdownMenuItem(text = { Text("Safety Center") }, leadingIcon = { Icon(Icons.Outlined.Shield, null) }, onClick = { menu = false; nav.safety() })
                            DropdownMenuItem(text = { Text("Report") }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }, onClick = { menu = false; showReport = true })
                            DropdownMenuItem(text = { Text("Block") }, leadingIcon = { Icon(Icons.Outlined.Block, null) }, onClick = { menu = false; confirmBlock = true })
                        }
                    }
                }
                // Identity row under the bar: tap to view the profile.
                if (other != null) {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "View profile") { nav.openProfile(other.id) }
                            .padding(horizontal = Gutter, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(other, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(other.displayName, fontFamily = DisplayFamily, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (other.isNri) "${other.location.city} · ${other.location.country}" else "${other.profession} · ${other.location.city}",
                                style = MaterialTheme.typography.labelMedium, color = AanganColors.extended.muted,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
                val nudge = vm.nudgeFor(draft)
                if (nudge != null) Notice(nudge, tone = NoticeTone.CAUTION, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                if (messages.none { it.senderId == me?.id } && ui.icebreakers.isNotEmpty() && convo?.closed != true) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ui.icebreakers) { s -> Box(Modifier.widthIn(max = 280.dp)) { SelectChip(s, selected = false, onClick = { draft = s }) } }
                    }
                }
                if (convo?.closed == true) {
                    Notice("This conversation is closed. Messages stay here for your records.", modifier = Modifier.padding(12.dp))
                } else {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = draft, onValueChange = { draft = it.take(2000) }, modifier = Modifier.weight(1f),
                            placeholder = "Message", singleLine = false, maxLines = 5, shape = RoundedCornerShape(26.dp),
                        )
                        CircleIconButton(
                            Icons.AutoMirrored.Filled.Send, "Send", { val t = draft; draft = ""; vm.send(t) { failed -> if (draft.isEmpty()) draft = failed } },
                            size = 52.dp, container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary,
                            bordered = false, enabled = draft.isNotBlank(),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Row(
                        Modifier.clip(CircleShape).background(AanganColors.extended.verifiedContainer).padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Shield, null, tint = AanganColors.extended.verified, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Safety Copilot checks messages on this phone only", style = MaterialTheme.typography.labelSmall, color = AanganColors.extended.verified)
                    }
                }
            }
            ui.sharedContact?.let { phone ->
                item { SharedContactCard(other?.firstName ?: "They", phone, onCall = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }, onCopy = { copySensitive(context, "Phone number", phone) }) }
            }
            items(messages, key = { it.id }) { m ->
                if (m.system) {
                    Text(
                        m.text, style = MaterialTheme.typography.labelMedium, color = AanganColors.extended.muted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center,
                    )
                } else {
                    val mine = m.senderId == me?.id
                    val assessment = if (!mine) vm.assess(m, convo) else null
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MessageBubble(m, mine, ui.translations[m.id], onTranslate = { vm.translate(m) })
                        if (assessment != null && assessment.level != ScamDetector.Level.NONE) {
                            val high = assessment.level == ScamDetector.Level.HIGH
                            Notice(
                                tone = if (high) NoticeTone.DANGER else NoticeTone.CAUTION,
                                title = if (high) "This looks like a known scam" else "Be careful",
                                text = assessment.primaryAdvice ?: "",
                                action = if (high) { { NoticeAction("Report and block", { showReport = true }, color = AanganColors.extended.danger) } } else null,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showReport && other != null) {
        ReportSheet(other.firstName, onDismiss = { showReport = false }, onSubmit = { r, d, b -> vm.report(r, d, b); showReport = false })
    }
    if (confirmBlock && other != null) {
        BlockDialog(other.firstName, onDismiss = { confirmBlock = false }, onConfirm = { confirmBlock = false; vm.block() })
    }
}

@Composable
private fun SharedContactCard(name: String, phone: String, onCall: () -> Unit, onCopy: () -> Unit) {
    PaperCard(Modifier.padding(bottom = 8.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Phone, null, tint = AanganColors.extended.verified)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$name shared their number", style = MaterialTheme.typography.titleSmall)
                Text(phone, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Call", onCall, Modifier.weight(1f), icon = Icons.Outlined.Call)
            SecondaryButton("Copy", onCopy, Modifier.weight(1f), icon = Icons.Outlined.ContentCopy)
        }
    }
}

@Composable
private fun MessageBubble(m: Message, mine: Boolean, translation: String?, onTranslate: () -> Unit) {
    val ext = AanganColors.extended
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            color = if (mine) scheme.primary else ext.card,
            contentColor = if (mine) scheme.onPrimary else scheme.onSurface,
            border = if (mine) null else BorderStroke(1.dp, ext.hairline),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (mine) 22.dp else 6.dp, bottomEnd = if (mine) 6.dp else 22.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                translation?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = if (mine) scheme.onPrimary.copy(alpha = 0.8f) else ext.muted)
                }
            }
        }
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(relativeTime(m.sentAt), style = MaterialTheme.typography.labelSmall, color = ext.muted, modifier = Modifier.padding(vertical = 6.dp))
            if (!mine && translation == null) {
                Text(
                    "  ·  Translate", style = MaterialTheme.typography.labelSmall, color = ext.muted,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Translate this message", onClick = onTranslate).padding(vertical = 6.dp),
                )
            }
        }
    }
}
