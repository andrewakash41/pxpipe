package com.vachan.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.model.SearchFilters
import com.vachan.app.data.AiRepository
import com.vachan.app.data.MatchRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.components.EmptyState
import com.vachan.app.ui.discover.MatchCard
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val shortlist = matches.shortlist
    val aiEnabled = ai.aiEnabled

    fun onQuery(q: String) { _ui.value = _ui.value.copy(query = q.take(200)) }

    /** The AI (or offline parser) only turns words into filters; the search itself is deterministic. */
    fun search() = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = true)
        val filters = if (_ui.value.query.isBlank()) SearchFilters() else ai.parseSearch(_ui.value.query)
        _ui.value = _ui.value.copy(filters = filters)
        run(filters)
    }

    fun removeChip(index: Int) = viewModelScope.launch {
        val f = _ui.value.filters
        val label = f.describe().getOrNull(index) ?: return@launch
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
        run(next)
    }

    private suspend fun run(filters: SearchFilters) {
        _ui.value = _ui.value.copy(busy = true)
        _ui.value = _ui.value.copy(results = matches.search(filters), busy = false, searched = true)
    }

    fun toggleShortlist(id: String) = viewModelScope.launch { matches.toggleShortlist(id) }
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
    val shortlist by vm.shortlist.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Search in your own words", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = ui.query, onValueChange = vm::onQuery, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Kannada engineer in Bengaluru, 27 to 31") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (ui.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (ui.query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Text(
                    if (aiEnabled) "  AI turns your words into filters you can see and edit." else "  Smart parsing on this phone (AI assistance is off).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!ui.searched) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Try", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        examples.forEach { e -> AssistChip(onClick = { vm.onQuery(e); vm.search() }, label = { Text(e) }) }
                    }
                }
            }
        }
        val chips = ui.filters.describe()
        if (chips.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Filters we understood — tap to remove", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEachIndexed { i, c ->
                            InputChip(selected = true, onClick = { vm.removeChip(i) }, label = { Text(c) }, trailingIcon = { Icon(Icons.Filled.Close, "Remove $c", Modifier.size(16.dp)) })
                        }
                    }
                }
            }
        }
        if (ui.searched && !ui.busy && ui.results.isEmpty()) {
            item {
                EmptyState(Icons.Filled.Search, "No one matches all of that yet", "Try removing a filter or two.", action = { TextButton(onClick = nav.assistant) { Text("Ask Vachan") } })
            }
        }
        if (ui.results.isNotEmpty()) {
            item { Text("${ui.results.size} people, ranked by two-way compatibility", style = MaterialTheme.typography.labelLarge) }
        }
        items(ui.results, key = { it.profile.id }) { m ->
            MatchCard(m, me, m.profile.id in shortlist, onOpen = { nav.openProfile(m.profile.id) }, onPass = null, onShortlist = { vm.toggleShortlist(m.profile.id) })
        }
    }
}
