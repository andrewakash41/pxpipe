package com.vachan.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.SearchFilters
import com.vachan.app.data.AiRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.ScorePill
import com.vachan.app.ui.components.Skeleton
import com.vachan.app.ui.components.VerifiedSeal
import com.vachan.app.ui.discover.canSeePhoto
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUi(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val results: List<RankedMatch> = emptyList(),
    val busy: Boolean = false,
    val searched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val ai: AiRepository,
    private val matches: MatchRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()
    val me = profiles.myProfile
    val aiEnabled = ai.aiEnabled
    private var running: Job? = null

    fun onQuery(q: String) { _ui.value = _ui.value.copy(query = q.take(200)) }

    /** The AI (or offline parser) only turns words into filters; the search itself is deterministic. */
    fun search() {
        running?.cancel()
        running = viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val q = _ui.value.query.trim()
            val filters = if (q.isBlank()) SearchFilters() else runCatching { ai.parseSearch(q) }.getOrDefault(SearchFilters(keywords = setOf(q.take(30))))
            _ui.value = _ui.value.copy(filters = filters)
            run(filters)
        }
    }

    fun clear() {
        running?.cancel()
        _ui.value = SearchUi()
    }

    fun removeChip(index: Int) {
        val f = _ui.value.filters
        val label = f.describe().getOrNull(index) ?: return
        val next = when {
            label.startsWith("Age ") -> f.copy(minAge = null, maxAge = null)
            label == "Open to relocating" -> f.copy(openToRelocate = null)
            label == "Lives abroad" || label == "Lives in India" -> f.copy(livesAbroad = null)
            label == "Verified only" -> f.copy(verifiedOnly = false)
            label.startsWith("Taller than") -> f.copy(minHeightCm = null)
            label.endsWith("+") -> f.copy(minEducation = null)
            else -> f.copy(
                cities = f.cities - label,
                religions = f.religions.filterNot { it.label == label }.toSet(),
                motherTongues = f.motherTongues - label,
                diets = f.diets.filterNot { it.label == label }.toSet(),
                professions = f.professions.filterNot { it.equals(label, ignoreCase = true) }.toSet(),
                maritalStatuses = f.maritalStatuses.filterNot { it.label == label }.toSet(),
                keywords = f.keywords.filterNot { "“$it”" == label }.toSet(),
            )
        }
        _ui.value = _ui.value.copy(filters = next)
        running?.cancel()
        running = viewModelScope.launch { run(next) }
    }

    private suspend fun run(filters: SearchFilters) {
        _ui.value = _ui.value.copy(busy = true)
        val results = runCatching { matches.search(filters) }.getOrDefault(emptyList())
        _ui.value = _ui.value.copy(results = results, busy = false, searched = true)
    }
}

private val examples = listOf(
    "Vegetarian doctor in Pune, 28–33",
    "Tamil, settled abroad, verified",
    "Open to relocating, MBA, under 32",
    "Second marriage, Mumbai or Delhi",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(nav: Nav, modifier: Modifier = Modifier, vm: SearchViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val full = { _: Any? -> GridItemSpan(2) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle("Search", eyebrow = "In your own words", subtitle = "Describe who you hope to meet. We turn it into filters you can see and edit.")
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(
                    value = ui.query, onValueChange = vm::onQuery,
                    placeholder = "e.g. Kannada engineer in Bengaluru, 27 to 31",
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        when {
                            ui.busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                            ui.query.isNotEmpty() -> IconButton(onClick = vm::clear) { Icon(Icons.Filled.Close, "Clear search") }
                        }
                    },
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = AanganColors.extended.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (aiEnabled) "AI reads your words; ranking is our transparent two-way score." else "Understood on this phone — AI assistance is off.",
                        style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted,
                    )
                }
            }
        }
        if (!ui.searched && !ui.busy) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Try", Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    examples.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AanganColors.extended.card)
                                .border(1.dp, AanganColors.extended.hairline, RoundedCornerShape(16.dp))
                                .clickable(role = Role.Button) { vm.onQuery(e); vm.search() }
                                .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(e, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.NorthEast, contentDescription = null, tint = AanganColors.extended.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        val chips = ui.filters.describe()
        if (chips.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Eyebrow("We understood · tap to remove", Modifier.padding(horizontal = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEachIndexed { i, c -> RemovableChip(c) { vm.removeChip(i) } }
                    }
                }
            }
        }
        if (ui.busy && ui.results.isEmpty()) {
            items(4) { Skeleton(Modifier.fillMaxWidth().aspectRatio(0.72f), RoundedCornerShape(22.dp)) }
        }
        if (ui.searched && !ui.busy && ui.results.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Filled.Search, "No one matches all of that yet", "Try removing a filter or two — must-haves are often softer than they feel.",
                    action = { QuietButton("Ask ${Brand.ASSISTANT}", nav.assistant) })
            }
        }
        if (ui.results.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "${ui.results.size} ${if (ui.results.size == 1) "person" else "people"}, best two-way fit first",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        items(ui.results, key = { it.profile.id }) { m -> ResultTile(m, me) { nav.openProfile(m.profile.id) } }
    }
}

@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClickLabel = "Remove $text", onClick = onRemove)
            .heightIn(min = 40.dp).padding(start = 15.dp, end = 11.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ResultTile(m: RankedMatch, me: Profile?, onOpen: () -> Unit) {
    val p = m.profile
    ProfilePhoto(
        p,
        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clickable(role = Role.Button, onClickLabel = "View ${p.firstName}'s profile", onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        blurred = !canSeePhoto(p, me),
        watermark = me?.memberCode,
    ) {
        ScorePill(m.compatibility.score, Modifier.align(Alignment.TopStart).padding(10.dp))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.firstName}, ${p.age}", fontFamily = DisplayFamily, fontSize = 25.sp, color = Color.White, maxLines = 1)
                if (p.verification.isVerified) { Spacer(Modifier.width(6.dp)); VerifiedSeal(16.dp, onPhoto = true) }
            }
            Text("${p.profession} · ${p.location.city}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.88f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
