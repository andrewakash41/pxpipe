package com.vachan.app.ui.discover

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.model.PhotoVisibility
import com.vachan.app.core.model.Profile
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.CompatibilityRing
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.Pill
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.VerificationBadges
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverUi(val loading: Boolean = true, val picks: List<RankedMatch> = emptyList())

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
        _ui.value = DiscoverUi(loading = false, picks = matches.dailyPicks())
    }

    fun pass(id: String) = viewModelScope.launch {
        matches.pass(id)
        _ui.value = _ui.value.copy(picks = _ui.value.picks.filterNot { it.profile.id == id })
    }

    fun toggleShortlist(id: String) = viewModelScope.launch { matches.toggleShortlist(id) }
    fun resetLearning() = viewModelScope.launch { matches.resetLearning() }
}

/** Whether a viewer may see [p]'s photo unblurred, given photo privacy and their verification. */
fun canSeePhoto(p: Profile, viewer: Profile?, accepted: Boolean = false): Boolean = when (p.photoVisibility) {
    PhotoVisibility.ALL_MEMBERS -> true
    PhotoVisibility.VERIFIED_MEMBERS -> viewer?.verification?.isVerified == true
    PhotoVisibility.AFTER_ACCEPT -> accepted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(nav: Nav, modifier: Modifier = Modifier, vm: DiscoverViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shortlist by vm.shortlist.collectAsStateWithLifecycle()
    val learned by vm.learned.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = { vm.refresh() }, modifier = modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column {
                    Text("Today's picks", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "A few people who fit you — and whom you fit — chosen for quality, not quantity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (me?.verification?.isVerified == false) {
                item {
                    Banner(
                        tone = BannerTone.INFO, title = "Get verified — it's free",
                        text = "Verified members get more responses and can see photos shared with verified members only.",
                        action = { TextButton(onClick = nav.verification) { Text("Verify now") } },
                    )
                }
            }
            learned?.let { insight ->
                item {
                    Banner(
                        tone = BannerTone.SUCCESS, icon = Icons.Filled.Psychology, title = "Learning what you like", text = insight,
                        action = { TextButton(onClick = vm::resetLearning) { Text("Reset to defaults") } },
                    )
                }
            }
            if (!ui.loading && ui.picks.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.AutoAwesome, "That's everyone for today",
                        "New picks arrive tomorrow. Widening your age range or turning a must-have into a soft preference can help.",
                        action = { TextButton(onClick = nav.assistant) { Text("Ask Vachan for tips") } },
                    )
                }
            }
            items(ui.picks, key = { it.profile.id }) { m ->
                MatchCard(
                    m, me = me, shortlisted = m.profile.id in shortlist,
                    onOpen = { nav.openProfile(m.profile.id) },
                    onPass = { vm.pass(m.profile.id) },
                    onShortlist = { vm.toggleShortlist(m.profile.id) },
                )
            }
        }
    }
}

@Composable
fun MatchCard(m: RankedMatch, me: Profile?, shortlisted: Boolean, onOpen: () -> Unit, onPass: (() -> Unit)?, onShortlist: () -> Unit) {
    val p = m.profile
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box {
            ProfilePhoto(
                p, modifier = Modifier.fillMaxWidth().aspectRatio(1.25f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                blurred = !canSeePhoto(p, me), watermark = me?.memberCode,
            )
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                IconButton(onClick = onShortlist) {
                    Icon(if (shortlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = if (shortlisted) "Remove from shortlist" else "Shortlist", tint = Color.White)
                }
                if (onPass != null) IconButton(onClick = onPass) { Icon(Icons.Filled.Close, contentDescription = "Not for me", tint = Color.White) }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.displayName}, ${p.age}", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.size(6.dp))
                        VerificationBadges(p.verification, compact = true)
                    }
                    Text(
                        "${p.profession} · ${p.location.city}${if (p.isNri) ", ${p.location.country}" else ""}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CompatibilityRing(m.compatibility.score)
                    Text(m.compatibility.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            m.compatibility.highlights.take(2).forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.managedBy.name != "SELF") Pill("Managed by ${p.managedBy.label.lowercase()}")
                p.responseRate?.let { if (it >= 0.7f) Pill("Usually replies") }
                Pill(p.activity.label, container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
