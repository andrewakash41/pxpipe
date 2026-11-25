package com.vachan.app.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.model.ManagedBy
import com.vachan.app.core.model.PhotoVisibility
import com.vachan.app.core.model.Profile
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.brand.AanganMark
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.common.greeting
import com.vachan.app.ui.common.userMessage
import com.vachan.app.ui.components.AppSnackbarHost
import com.vachan.app.ui.components.CircleIconButton
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeAction
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PrimaryButton
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.ScorePill
import com.vachan.app.ui.components.Skeleton
import com.vachan.app.ui.components.Tag
import com.vachan.app.ui.components.VerifiedSeal
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DiscoverUi(val loading: Boolean = true, val picks: List<RankedMatch> = emptyList(), val message: String? = null)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val matches: MatchRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DiscoverUi())
    val ui = _ui.asStateFlow()
    val shortlist = matches.shortlist
    val learned = matches.learnedInsight
    val me = profiles.myProfile

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _ui.value = _ui.value.copy(loading = true)
        val picks = runCatching { matches.dailyPicks() }
        _ui.value = DiscoverUi(
            loading = false,
            picks = picks.getOrDefault(emptyList()),
            message = picks.exceptionOrNull()?.userMessage("We couldn't load today's introductions. Pull to try again."),
        )
    }

    fun pass(id: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(picks = _ui.value.picks.filterNot { it.profile.id == id })
        runCatching { matches.pass(id) }
    }

    fun toggleShortlist(id: String) = viewModelScope.launch {
        matches.toggleShortlist(id).onFailure { _ui.value = _ui.value.copy(message = it.userMessage()) }
    }

    fun resetLearning() = viewModelScope.launch { matches.resetLearning() }
    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
}

/** Whether a viewer may see [p]'s photo unblurred, given photo privacy and their verification. */
fun canSeePhoto(p: Profile, viewer: Profile?, accepted: Boolean = false): Boolean = when (p.photoVisibility) {
    PhotoVisibility.ALL_MEMBERS -> true
    PhotoVisibility.VERIFIED_MEMBERS -> viewer?.verification?.isVerified == true || accepted
    PhotoVisibility.AFTER_ACCEPT -> accepted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(nav: Nav, modifier: Modifier = Modifier, vm: DiscoverViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shortlist by vm.shortlist.collectAsStateWithLifecycle()
    val learned by vm.learned.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    val now = remember { Date() }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    Box(modifier) {
        PullToRefreshBox(isRefreshing = ui.loading && ui.picks.isNotEmpty(), onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(key = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AanganMark(size = 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Eyebrow(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), Modifier.weight(1f))
                            CircleIconButton(Icons.Filled.AutoAwesome, "Ask ${Brand.ASSISTANT}", nav.assistant, size = 42.dp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${greeting(hour)}${me?.firstName?.let { ",\n$it" } ?: ""}",
                                style = MaterialTheme.typography.displaySmall,
                            )
                            Text(
                                when {
                                    ui.loading -> "Choosing today's introductions…"
                                    ui.picks.isEmpty() -> "You've seen everyone for today."
                                    ui.picks.size == 1 -> "One introduction, chosen because it works both ways."
                                    else -> "${ui.picks.size} introductions, chosen because they work both ways."
                                },
                                style = MaterialTheme.typography.bodyLarge, color = AanganColors.extended.muted,
                            )
                        }
                    }
                }
                if (me?.verification?.isVerified == false) {
                    item(key = "verify") {
                        Notice(
                            tone = NoticeTone.INFO, title = "Get verified — it's free",
                            text = "Verified members get more replies and can see photos shared with verified members only.",
                            action = { NoticeAction("Verify now", nav.verification) },
                        )
                    }
                }
                learned?.let { insight ->
                    item(key = "learned") {
                        Notice(
                            tone = NoticeTone.NEUTRAL, icon = Icons.Filled.Psychology, title = "Tuned to you",
                            text = insight, action = { NoticeAction("Reset", vm::resetLearning) },
                        )
                    }
                }
                if (ui.loading && ui.picks.isEmpty()) {
                    items(2) { PickSkeleton() }
                }
                if (!ui.loading && ui.picks.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            Icons.Filled.AutoAwesome, "That's everyone for today",
                            "New introductions arrive tomorrow. Widening your age range or turning a must-have into a soft preference can help.",
                            action = { QuietButton("Ask ${Brand.ASSISTANT} for tips", nav.assistant) },
                        )
                    }
                }
                items(ui.picks, key = { it.profile.id }) { m ->
                    PickCard(
                        m, me = me, shortlisted = m.profile.id in shortlist,
                        onOpen = { nav.openProfile(m.profile.id) },
                        onPass = { vm.pass(m.profile.id) },
                        onShortlist = { vm.toggleShortlist(m.profile.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (ui.picks.isNotEmpty()) {
                    item(key = "footer") {
                        Text(
                            "That's today's set. Fewer, better introductions — new ones arrive tomorrow.",
                            style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

/** One introduction: an editorial portrait with the reason to click, and a deliberate choice. */
@Composable
fun PickCard(
    m: RankedMatch,
    me: Profile?,
    shortlisted: Boolean,
    onOpen: () -> Unit,
    onPass: (() -> Unit)?,
    onShortlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = m.profile
    val ext = AanganColors.extended
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ProfilePhoto(
            p,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clickable(role = Role.Button, onClickLabel = "View ${p.firstName}'s profile", onClick = onOpen),
            shape = RoundedCornerShape(30.dp),
            blurred = !canSeePhoto(p, me),
            watermark = me?.memberCode,
        ) {
            ScorePill(m.compatibility.score, Modifier.align(Alignment.TopStart).padding(14.dp))
            CircleIconButton(
                if (shortlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                if (shortlisted) "Remove ${p.firstName} from shortlist" else "Shortlist ${p.firstName}",
                onShortlist,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                size = 42.dp, container = Color.Black.copy(alpha = 0.32f), content = Color.White, bordered = false,
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${p.firstName}, ${p.age}", fontFamily = DisplayFamily, fontSize = 38.sp, color = Color.White)
                    if (p.verification.isVerified) {
                        Spacer(Modifier.width(8.dp))
                        VerifiedSeal(22.dp, onPhoto = true)
                    }
                }
                Text(
                    "${p.profession} · ${p.location.city}${if (p.isNri) ", ${p.location.country}" else ""}",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val reasons = m.compatibility.highlights.take(2)
        if (reasons.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(2.dp).height(if (reasons.size > 1) 52.dp else 26.dp).background(ext.accent))
                Text(
                    reasons.joinToString(" · ") { it.replaceFirstChar(Char::uppercase) },
                    fontFamily = DisplayFamily, fontStyle = FontStyle.Italic, fontSize = 21.sp, lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.managedBy != ManagedBy.SELF) Tag("With ${p.managedBy.label.lowercase()}", icon = Icons.Outlined.FamilyRestroom)
            p.responseRate?.let { if (it >= 0.7f) Tag("Usually replies", icon = Icons.Outlined.ChatBubbleOutline) }
            Tag(p.activity.label, icon = Icons.Outlined.Schedule)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onPass != null) CircleIconButton(Icons.Filled.Close, "Not for me — pass on ${p.firstName}", onPass, size = 54.dp)
            PrimaryButton("Get to know ${p.firstName}", onOpen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PickSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(0.8f), RoundedCornerShape(30.dp))
        Skeleton(Modifier.fillMaxWidth(0.8f).height(22.dp))
        Skeleton(Modifier.fillMaxWidth(0.5f).height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.size(54.dp), RoundedCornerShape(27.dp))
            Skeleton(Modifier.weight(1f).height(54.dp), RoundedCornerShape(27.dp))
        }
    }
}
