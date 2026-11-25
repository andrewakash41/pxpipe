package com.vachan.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.matching.CompatibilityResult
import com.vachan.app.core.matching.PreferenceLearner
import com.vachan.app.core.model.InterestStatus
import com.vachan.app.core.model.ManagedBy
import com.vachan.app.core.model.MatchInsight
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ReportReason
import com.vachan.app.core.model.heightLabel
import com.vachan.app.data.AiRepository
import com.vachan.app.data.ChatRepository
import com.vachan.app.data.InterestRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SafetyRepository
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AccentButton
import com.vachan.app.ui.components.AppSnackbarHost
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.CircleIconButton
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.FactorBar
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.InfoRow
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.MatchScoreRing
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.RadioRow
import com.vachan.app.ui.components.SecondaryButton
import com.vachan.app.ui.components.Section
import com.vachan.app.ui.components.Skeleton
import com.vachan.app.ui.components.Tag
import com.vachan.app.ui.components.TagRow
import com.vachan.app.ui.components.ToggleRow
import com.vachan.app.ui.components.VerificationBadges
import com.vachan.app.ui.components.VerifiedSeal
import com.vachan.app.ui.root.LoadingBox
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUi(
    val profile: Profile? = null,
    val notFound: Boolean = false,
    val compatibility: CompatibilityResult? = null,
    val insight: MatchInsight? = null,
    val insightLoading: Boolean = true,
    val interestSent: Boolean = false,
    val accepted: Boolean = false,
    val matchId: String? = null,
    val sending: Boolean = false,
    val message: String? = null,
    /** Set after block/report-and-block so the screen closes itself. */
    val leave: Boolean = false,
)

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val matches: MatchRepository,
    private val interests: InterestRepository,
    private val ai: AiRepository,
    private val safety: SafetyRepository,
    private val chat: ChatRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DetailUi())
    val ui = _ui.asStateFlow()
    val me = profiles.myProfile
    val shortlist = matches.shortlist
    val aiEnabled = ai.aiEnabled

    fun load(id: String) = viewModelScope.launch {
        val p = runCatching { profiles.getProfile(id) }.getOrNull()
        if (p == null) { _ui.value = _ui.value.copy(notFound = true); return@launch }
        val c = runCatching { matches.compatibilityWith(id) }.getOrNull()
        val myId = me.value?.id
        val rel = interests.interests.value.filter { (it.fromId == myId && it.toId == id) || (it.fromId == id && it.toId == myId) }
        _ui.value = _ui.value.copy(
            profile = p, compatibility = c,
            interestSent = rel.any { it.fromId == myId && it.status == InterestStatus.PENDING },
            accepted = rel.any { it.status == InterestStatus.ACCEPTED },
            matchId = chat.conversations.value.firstOrNull { it.otherId == id && !it.closed }?.matchId,
        )
        _ui.value = _ui.value.copy(insight = c?.let { runCatching { ai.matchInsight(p, it) }.getOrNull() }, insightLoading = false)
        // Reading a profile for a while is a weak positive signal for the on-device learner.
        delay(8_000)
        runCatching { matches.recordFeedback(id, PreferenceLearner.Feedback.VIEWED_LONG) }
    }

    fun sendInterest(note: String?, onSent: () -> Unit) = viewModelScope.launch {
        val p = _ui.value.profile ?: return@launch
        _ui.value = _ui.value.copy(sending = true)
        interests.sendInterest(p.id, note?.trim()?.ifBlank { null })
            .onSuccess {
                _ui.value = _ui.value.copy(sending = false, interestSent = true, message = "Interest sent to ${p.firstName}. They have 7 days to reply.")
                onSent()
            }
            .onFailure { _ui.value = _ui.value.copy(sending = false, message = it.userMessage()) }
    }

    fun toggleShortlist() = viewModelScope.launch {
        _ui.value.profile?.let { p -> matches.toggleShortlist(p.id).onFailure { _ui.value = _ui.value.copy(message = it.userMessage()) } }
    }

    fun pass() = viewModelScope.launch {
        _ui.value.profile?.let { runCatching { matches.pass(it.id) } }
        _ui.value = _ui.value.copy(leave = true)
    }

    fun report(reason: ReportReason, details: String, block: Boolean) = viewModelScope.launch {
        val p = _ui.value.profile ?: return@launch
        val reported = safety.report(p.id, reason, details, attachRecentMessages = true)
        val blocked = if (block) safety.block(p.id) else Result.success(Unit)
        val failure = reported.exceptionOrNull() ?: blocked.exceptionOrNull()
        _ui.value = if (failure != null) _ui.value.copy(message = failure.userMessage())
        else _ui.value.copy(message = "Thank you. Our safety team acknowledges every report within 24 hours.", leave = block)
    }

    fun block() = viewModelScope.launch {
        val p = _ui.value.profile ?: return@launch
        safety.block(p.id)
            .onSuccess { _ui.value = _ui.value.copy(leave = true) }
            .onFailure { _ui.value = _ui.value.copy(message = it.userMessage()) }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(id: String, nav: Nav, vm: ProfileDetailViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.load(id) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val shortlist by vm.shortlist.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var showInterest by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(ui.leave) { if (ui.leave) nav.back() }

    val p = ui.profile
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { AppSnackbarHost(snackbar) },
        bottomBar = {
            if (p != null) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AanganColors.extended.hairline))
                    Row(Modifier.padding(horizontal = Gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            ui.accepted && ui.matchId != null -> PrimaryButton("Open chat with ${p.firstName}", { nav.openChat(ui.matchId!!) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.Chat)
                            ui.accepted -> SecondaryButton("You're connected", {}, Modifier.weight(1f), enabled = false)
                            ui.interestSent -> SecondaryButton("Interest sent · waiting", {}, Modifier.weight(1f), enabled = false)
                            else -> {
                                CircleIconButton(Icons.Filled.Close, "Pass on ${p.firstName}", vm::pass, size = 54.dp)
                                AccentButton("Send interest", { showInterest = true }, Modifier.weight(1f), icon = Icons.Filled.Favorite)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            ui.notFound -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", nav.back)
                Text("This profile isn't available", style = MaterialTheme.typography.headlineMedium)
                Text("The member may have paused or deleted their profile, or changed who can see it.", color = AanganColors.extended.muted)
            }
            p == null -> LoadingBox(Modifier.padding(padding))
            else -> Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Hero(p, me, ui.accepted)
                    Vitals(p)
                    Column(Modifier.padding(horizontal = Gutter, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        if (p.verification.count > 0) VerificationBadges(p.verification)
                        ui.compatibility?.let { c -> WhyYouMightClick(p, c, ui.insight, ui.insightLoading, aiEnabled) }
                        Section(title = "About ${p.firstName}") {
                            Text(p.about.ifBlank { "${p.firstName} hasn't written a bio yet." }, style = MaterialTheme.typography.bodyLarge)
                            if (p.interests.isNotEmpty()) TagRow(p.interests.sorted())
                        }
                        Section(title = "Details") {
                            InfoRow(Icons.Outlined.Cake, "Age and status", "${p.age} · ${p.maritalStatus.label}${if (p.hasChildren) " · has children" else ""}")
                            InfoRow(Icons.Outlined.LocationOn, "Lives in", "${p.location.city}, ${p.location.country}" + (p.residencyStatus?.let { " · $it" } ?: "") + if (p.openToRelocate) " · open to relocating" else "")
                            InfoRow(Icons.Outlined.School, "Education", "${p.education.label}${if (p.educationField.isNotBlank()) " · ${p.educationField}" else ""}")
                            InfoRow(Icons.Outlined.Work, "Work", p.profession + if (p.showIncome) " · ${p.incomeBand.label}" else "")
                            InfoRow(Icons.Outlined.Language, "Languages", (listOf(p.motherTongue) + (p.languages - p.motherTongue)).joinToString(", "))
                            InfoRow(Icons.Outlined.SelfImprovement, "Faith", p.religion?.label ?: "Prefers not to say")
                            if (p.showCommunity && p.community != null) InfoRow(Icons.Outlined.SelfImprovement, "Community", p.community)
                            InfoRow(Icons.Outlined.Restaurant, "Lifestyle", "${p.diet.label} · smokes ${p.smoking.label.lowercase()} · drinks ${p.drinking.label.lowercase()}")
                            InfoRow(Icons.Outlined.FamilyRestroom, "Family", p.familyType.label + if (p.managedBy != ManagedBy.SELF) " · profile managed with ${p.managedBy.label.lowercase()}" else "")
                        }
                        Section(title = "Looking for") {
                            val pr = p.preferences
                            Text(
                                buildString {
                                    append("Age ${pr.minAge}–${pr.maxAge}")
                                    if (pr.religions.isNotEmpty()) append(" · ${pr.religions.joinToString { it.label }}")
                                    if (pr.diets.isNotEmpty()) append(" · ${pr.diets.joinToString { it.label.lowercase() }}")
                                    pr.maxDistanceKm?.let { append(" · within $it km") }
                                    if (pr.maritalStatuses.size < 5) append(" · ${pr.maritalStatuses.joinToString { it.label.lowercase() }}")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Notice(
                            icon = Icons.Outlined.Shield, tone = NoticeTone.NEUTRAL, title = "Stay safe",
                            text = "Never send money to anyone you meet here, whatever the reason. Report anyone who asks.",
                        )
                    }
                }
                // Floating controls over the hero.
                Row(Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", nav.back, container = Color.Black.copy(alpha = 0.32f), content = Color.White, bordered = false)
                    Spacer(Modifier.weight(1f))
                    val listed = p.id in shortlist
                    CircleIconButton(
                        if (listed) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (listed) "Remove from shortlist" else "Shortlist", vm::toggleShortlist,
                        container = Color.Black.copy(alpha = 0.32f), content = Color.White, bordered = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        CircleIconButton(Icons.Filled.MoreHoriz, "More options", { menu = true }, container = Color.Black.copy(alpha = 0.32f), content = Color.White, bordered = false)
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Report ${p.firstName}") }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }, onClick = { menu = false; showReport = true })
                            DropdownMenuItem(text = { Text("Block ${p.firstName}") }, leadingIcon = { Icon(Icons.Outlined.Block, null) }, onClick = { menu = false; confirmBlock = true })
                        }
                    }
                }
            }
        }
    }

    if (showInterest && p != null) {
        InterestSheet(p.firstName, sending = ui.sending, onDismiss = { showInterest = false }, onSend = { note ->
            vm.sendInterest(note) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                showInterest = false
            }
        })
    }
    if (showReport && p != null) {
        ReportSheet(p.firstName, onDismiss = { showReport = false }, onSubmit = { r, d, b -> vm.report(r, d, b); showReport = false })
    }
    if (confirmBlock && p != null) {
        BlockDialog(p.firstName, onDismiss = { confirmBlock = false }, onConfirm = { confirmBlock = false; vm.block() })
    }
}

@Composable
private fun Hero(p: Profile, me: Profile?, accepted: Boolean) {
    ProfilePhoto(
        p,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.82f),
        shape = RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp),
        blurred = !canSeePhoto(p, me, accepted),
        watermark = me?.memberCode,
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = Gutter, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.firstName} ${p.lastInitial.take(1)}., ${p.age}", fontFamily = DisplayFamily, fontSize = 42.sp, lineHeight = 44.sp, color = Color.White)
                if (p.verification.isVerified) { Spacer(Modifier.width(10.dp)); VerifiedSeal(24.dp, onPhoto = true) }
            }
            Text("${p.profession} · ${p.location.city}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

/** Hinge-style strip of key facts. */
@Composable
private fun Vitals(p: Profile) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("${p.age}", icon = Icons.Outlined.Cake)
        Tag(heightLabel(p.heightCm).substringBefore(" ("), icon = Icons.Outlined.Height)
        Tag(p.location.city, icon = Icons.Outlined.LocationOn)
        p.religion?.let { Tag(it.label, icon = Icons.Outlined.SelfImprovement) }
        Tag(p.diet.label, icon = Icons.Outlined.Restaurant)
        Tag(p.motherTongue, icon = Icons.Outlined.Language)
        Tag(p.maritalStatus.label, icon = Icons.Outlined.FamilyRestroom)
    }
}

@Composable
private fun WhyYouMightClick(p: Profile, c: CompatibilityResult, insight: MatchInsight?, loading: Boolean, aiEnabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("Why you might click", Modifier.padding(horizontal = 4.dp))
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MatchScoreRing(c.score, size = 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${p.firstName} fits ${c.theyFitYou}% of what you're looking for; you fit ${c.youFitThem}% of theirs.",
                        style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted,
                    )
                }
            }
            when {
                loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(16.dp))
                    Skeleton(Modifier.fillMaxWidth(0.9f).height(16.dp))
                    Skeleton(Modifier.fillMaxWidth(0.6f).height(16.dp))
                }
                insight != null -> {
                    Text(insight.summary, fontFamily = DisplayFamily, fontStyle = FontStyle.Italic, fontSize = 21.sp, lineHeight = 27.sp)
                    if (insight.talkingPoints.isNotEmpty()) {
                        Eyebrow("Worth talking about")
                        insight.talkingPoints.forEach { tp ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.padding(top = 8.dp).size(5.dp).background(AanganColors.extended.accent, RoundedCornerShape(3.dp)))
                                Text(tp, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text(
                        if (insight.generatedByAi) "Written by AI from your compatibility factors — never from private data."
                        else if (aiEnabled) "Summary of your compatibility factors." else "Summary of your compatibility factors (AI assistance is off).",
                        style = MaterialTheme.typography.labelSmall, color = AanganColors.extended.muted,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AanganColors.extended.hairline))
            c.factors.forEach { FactorBar(it.factor.label, it.detail, it.score) }
        }
        if (c.frictions.isNotEmpty()) {
            Notice(tone = NoticeTone.CAUTION, title = "Things to discuss early", text = c.frictions.take(3).joinToString("\n"))
        }
    }
}

private val noteStarters = listOf(
    "Your answer about family really stood out to me.",
    "We seem to want similar things — would love to talk.",
    "I'd like to know more about your work.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterestSheet(name: String, sending: Boolean, onDismiss: () -> Unit, onSend: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Send interest to $name", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A short, personal note makes a real difference. Contact details are removed automatically — you can share your number privately once you both say yes.",
                style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted,
            )
            AppTextField(note, { note = it.take(300) }, placeholder = "Say why you'd like to connect (optional)", singleLine = false, minLines = 3, supporting = "${note.length}/300")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                noteStarters.forEach { s -> com.vachan.app.ui.components.SelectChip(s, selected = false, onClick = { note = s }) }
            }
            AccentButton("Send interest", { onSend(note) }, Modifier.fillMaxWidth(), loading = sending, icon = Icons.Filled.Favorite)
            QuietButton("Not now", onDismiss, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(name: String, onDismiss: () -> Unit, onSubmit: (ReportReason, String, Boolean) -> Unit) {
    var reason by rememberSaveable { mutableStateOf(ReportReason.SCAM) }
    var details by rememberSaveable { mutableStateOf("") }
    var block by rememberSaveable { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(horizontal = Gutter).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Report $name", style = MaterialTheme.typography.headlineMedium)
            Text("Reports are confidential — $name won't know it was you. We acknowledge every report within 24 hours.", style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted)
            ListGroup(title = "What happened?") {
                ReportReason.entries.forEachIndexed { i, r ->
                    if (i > 0) ListDivider()
                    RadioRow(r.label, null, selected = reason == r, onClick = { reason = r })
                }
            }
            AppTextField(details, { details = it.take(1000) }, label = "Details (optional)", singleLine = false, minLines = 2)
            ListGroup {
                ToggleRow("Also block $name", "You won't see each other anywhere again", block, { block = it })
            }
            PrimaryButton("Submit report", { onSubmit(reason, details, block) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun BlockDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AanganColors.extended.card,
        title = { Text("Block $name?", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("You won't see each other in introductions, search or chat, and any conversation closes. You can unblock later from Safety Center.") },
        confirmButton = { QuietButton("Block", onConfirm, color = AanganColors.extended.danger) },
        dismissButton = { QuietButton("Cancel", onDismiss) },
    )
}
