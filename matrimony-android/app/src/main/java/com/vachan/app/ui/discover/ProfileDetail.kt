package com.vachan.app.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.matching.CompatibilityResult
import com.vachan.app.core.matching.PreferenceLearner
import com.vachan.app.core.model.InterestStatus
import com.vachan.app.core.model.MatchInsight
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ReportReason
import com.vachan.app.core.model.heightLabel
import com.vachan.app.data.AiRepository
import com.vachan.app.data.InterestRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.data.SafetyRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.CompatibilityRing
import com.vachan.app.ui.components.FactorBar
import com.vachan.app.ui.components.InfoRow
import com.vachan.app.ui.components.PillRow
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.components.VerificationBadges
import com.vachan.app.ui.root.LoadingBox
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUi(
    val profile: Profile? = null,
    val compatibility: CompatibilityResult? = null,
    val insight: MatchInsight? = null,
    val insightLoading: Boolean = true,
    val interestSent: Boolean = false,
    val accepted: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val matches: MatchRepository,
    private val interests: InterestRepository,
    private val ai: AiRepository,
    private val safety: SafetyRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DetailUi())
    val ui = _ui.asStateFlow()
    val me = profiles.myProfile
    val shortlist = matches.shortlist
    val aiEnabled = ai.aiEnabled

    fun load(id: String) = viewModelScope.launch {
        val p = profiles.getProfile(id) ?: return@launch
        val c = matches.compatibilityWith(id)
        val myId = me.value?.id
        val rel = interests.interests.value.filter { (it.fromId == myId && it.toId == id) || (it.fromId == id && it.toId == myId) }
        _ui.value = _ui.value.copy(
            profile = p, compatibility = c,
            interestSent = rel.any { it.fromId == myId && it.status == InterestStatus.PENDING },
            accepted = rel.any { it.status == InterestStatus.ACCEPTED },
        )
        if (c != null) {
            _ui.value = _ui.value.copy(insight = ai.matchInsight(p, c), insightLoading = false)
        }
        // Reading a profile for a while is a weak positive signal for the on-device learner.
        delay(8_000)
        matches.recordFeedback(id, PreferenceLearner.Feedback.VIEWED_LONG)
    }

    fun sendInterest(note: String?) = viewModelScope.launch {
        val p = _ui.value.profile ?: return@launch
        interests.sendInterest(p.id, note?.ifBlank { null })
            .onSuccess { _ui.value = _ui.value.copy(interestSent = true, message = "Interest sent to ${p.firstName}. They have 7 days to respond.") }
            .onFailure { _ui.value = _ui.value.copy(message = it.message) }
    }

    fun toggleShortlist() = viewModelScope.launch { _ui.value.profile?.let { matches.toggleShortlist(it.id) } }

    fun report(reason: ReportReason, details: String, block: Boolean) = viewModelScope.launch {
        val p = _ui.value.profile ?: return@launch
        safety.report(p.id, reason, details, attachRecentMessages = true)
        if (block) safety.block(p.id)
        _ui.value = _ui.value.copy(message = "Thanks — our safety team will acknowledge your report within 24 hours." + if (block) " ${p.firstName} is blocked." else "")
    }

    fun block() = viewModelScope.launch {
        val p = _ui.value.profile ?: return@launch
        safety.block(p.id)
        _ui.value = _ui.value.copy(message = "${p.firstName} is blocked. Neither of you will see the other again.")
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
    var showInterest by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val p = ui.profile
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(p?.displayName ?: "") },
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (p?.id in shortlist) "Remove from shortlist" else "Shortlist") }, onClick = { menu = false; vm.toggleShortlist() })
                        DropdownMenuItem(text = { Text("Report") }, leadingIcon = { Icon(Icons.Filled.Flag, null) }, onClick = { menu = false; showReport = true })
                        DropdownMenuItem(text = { Text("Block") }, leadingIcon = { Icon(Icons.Filled.Block, null) }, onClick = { menu = false; vm.block(); nav.back() })
                    }
                },
            )
        },
        bottomBar = {
            if (p != null) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showInterest = true }, enabled = !ui.interestSent && !ui.accepted,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.Favorite, null)
                        Spacer(Modifier.size(8.dp))
                        Text(when { ui.accepted -> "You're connected"; ui.interestSent -> "Interest sent"; else -> "Send interest" })
                    }
                }
            }
        },
    ) { padding ->
        if (p == null) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                ProfilePhoto(p, Modifier.fillMaxWidth().aspectRatio(1f), blurred = !canSeePhoto(p, me, ui.accepted), watermark = me?.memberCode)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${p.displayName}, ${p.age}", style = MaterialTheme.typography.headlineSmall)
                    Text("${p.profession} · ${p.location.city}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    VerificationBadges(p.verification)
                }
                ui.compatibility?.let {
                    Spacer(Modifier.size(12.dp))
                    CompatibilityRing(it.score, size = 72.dp, stroke = 8.dp)
                }
            }

            ui.compatibility?.let { c -> CompatibilitySection(c, ui.insight, ui.insightLoading, aiEnabled) }

            SectionCard(title = "About ${p.firstName}") {
                Text(p.about, style = MaterialTheme.typography.bodyLarge)
                if (p.interests.isNotEmpty()) PillRow(p.interests.sorted())
            }
            SectionCard(title = "Details") {
                InfoRow(Icons.Filled.Cake, "Age", "${p.age} · ${p.maritalStatus.label}${if (p.hasChildren) " · has children" else ""}")
                InfoRow(Icons.Filled.Height, "Height", heightLabel(p.heightCm))
                InfoRow(Icons.Filled.LocationOn, "Lives in", "${p.location.city}, ${p.location.country}" + (p.residencyStatus?.let { " · $it" } ?: "") + if (p.openToRelocate) " · open to relocating" else "")
                InfoRow(Icons.Filled.School, "Education", "${p.education.label}${if (p.educationField.isNotBlank()) " · ${p.educationField}" else ""}")
                InfoRow(Icons.Filled.Work, "Work", p.profession + if (p.showIncome) " · ${p.incomeBand.label}" else "")
                InfoRow(Icons.Filled.Language, "Languages", (listOf(p.motherTongue) + (p.languages - p.motherTongue)).joinToString(", "))
                InfoRow(Icons.Filled.SelfImprovement, "Religion", p.religion?.label ?: "Prefers not to say")
                if (p.showCommunity && p.community != null) InfoRow(Icons.Filled.SelfImprovement, "Community", p.community)
                InfoRow(Icons.Filled.Restaurant, "Lifestyle", "${p.diet.label} · smokes ${p.smoking.label.lowercase()} · drinks ${p.drinking.label.lowercase()}")
                InfoRow(Icons.Filled.FamilyRestroom, "Family", "${p.familyType.label} · profile managed by ${p.managedBy.label.lowercase()}")
            }
            SectionCard(title = "Looking for") {
                val pr = p.preferences
                Text(
                    buildString {
                        append("Age ${pr.minAge}–${pr.maxAge}")
                        if (pr.religions.isNotEmpty()) append(" · ${pr.religions.joinToString { it.label }}")
                        if (pr.diets.isNotEmpty()) append(" · ${pr.diets.joinToString { it.label.lowercase() }}")
                        pr.maxDistanceKm?.let { append(" · within $it km") }
                        if (pr.maritalStatuses.size < 5) append(" · ${pr.maritalStatuses.joinToString { it.label.lowercase() }}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Banner(
                tone = BannerTone.INFO,
                text = "Never send money to anyone you meet here — no matter the reason. Report anyone who asks.",
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showInterest && p != null) {
        InterestDialog(p.firstName, onDismiss = { showInterest = false }, onSend = { vm.sendInterest(it); showInterest = false })
    }
    if (showReport && p != null) {
        ReportDialog(p.firstName, onDismiss = { showReport = false }, onSubmit = { r, d, b -> vm.report(r, d, b); showReport = false; if (b) nav.back() })
    }
}

@Composable
private fun CompatibilitySection(c: CompatibilityResult, insight: MatchInsight?, loading: Boolean, aiEnabled: Boolean) {
    SectionCard(title = "Why you might click", subtitle = "${c.label} · they fit ${c.theyFitYou}% of your preferences, you fit ${c.youFitThem}% of theirs") {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text("Preparing insights…")
            }
            insight != null -> {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(insight.summary, style = MaterialTheme.typography.bodyMedium)
                }
                if (insight.talkingPoints.isNotEmpty()) {
                    Text("Worth talking about", style = MaterialTheme.typography.labelLarge)
                    insight.talkingPoints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                Text(
                    if (insight.generatedByAi) "Written by AI from your compatibility factors — not from private data."
                    else if (aiEnabled) "Smart summary from your compatibility factors." else "Smart summary (AI assistance is off).",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        c.factors.forEach { FactorBar(it.factor.label, it.detail, it.score) }
        if (c.frictions.isNotEmpty()) {
            Banner(tone = BannerTone.CAUTION, title = "Things to discuss early", text = c.frictions.take(3).joinToString("\n"))
        }
    }
}

@Composable
private fun InterestDialog(name: String, onDismiss: () -> Unit, onSend: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send interest to $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a short, personal note — it makes a real difference. Contact details are removed automatically.")
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("Note (optional)") }, minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onSend(note) }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ReportDialog(name: String, onDismiss: () -> Unit, onSubmit: (ReportReason, String, Boolean) -> Unit) {
    var reason by rememberSaveable { mutableStateOf(ReportReason.SCAM) }
    var details by rememberSaveable { mutableStateOf("") }
    var block by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reports are confidential. $name won't know it was you.", style = MaterialTheme.typography.bodySmall)
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().selectable(reason == r, role = Role.RadioButton) { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = null)
                        Text(r.label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(details, { details = it.take(1000) }, label = { Text("What happened? (optional)") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(block, { block = it }); Text("Also block $name")
                }
            }
        },
        confirmButton = { Button(onClick = { onSubmit(reason, details, block) }) { Text("Submit report") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
