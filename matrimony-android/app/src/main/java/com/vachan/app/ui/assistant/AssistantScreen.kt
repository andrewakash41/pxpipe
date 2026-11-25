package com.vachan.app.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.data.AiRepository
import com.vachan.app.data.AssistantTurn
import com.vachan.app.ui.brand.AanganMark
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.components.AppTextField
import com.vachan.app.ui.components.CircleIconButton
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.SelectChip
import com.vachan.app.ui.components.Skeleton
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(private val ai: AiRepository) : ViewModel() {
    private val _turns = MutableStateFlow(
        listOf(AssistantTurn(false, "Namaste! I'm the ${Brand.ASSISTANT}. I can help you refine preferences, write your bio, understand a match, or stay safe. I can't see your chats, and I never share anything about other members.")),
    )
    val turns = _turns.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val aiEnabled = ai.aiEnabled

    fun ask(text: String) = viewModelScope.launch {
        if (text.isBlank() || _busy.value) return@launch
        _turns.value = _turns.value + AssistantTurn(true, text.trim().take(1000))
        _busy.value = true
        val reply = runCatching { ai.ask(_turns.value) }.getOrDefault("Sorry — I couldn't answer that just now. Please try again in a moment.")
        _turns.value = _turns.value + AssistantTurn(false, reply)
        _busy.value = false
    }
}

private val starters = listOf("How can I get better matches?", "Help me write my bio", "How do I spot a scam?", "How does Family Mode work?", "Why am I seeing these picks?")

@Composable
fun AssistantScreen(nav: Nav, vm: AssistantViewModel = hiltViewModel()) {
    val turns by vm.turns.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size, busy) { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopBar(onBack = nav.back) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (turns.size <= 1) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(starters) { s -> SelectChip(s, selected = false, enabled = !busy, onClick = { vm.ask(s) }) }
                    }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(draft, { draft = it.take(1000) }, Modifier.weight(1f), placeholder = "Ask anything about finding a partner", singleLine = false, maxLines = 4, shape = RoundedCornerShape(26.dp))
                    CircleIconButton(
                        Icons.AutoMirrored.Filled.Send, "Send", { vm.ask(draft); draft = "" }, size = 52.dp,
                        container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary, bordered = false,
                        enabled = draft.isNotBlank() && !busy,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = list, contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    AanganMark(size = 34.dp)
                    ScreenTitle(Brand.ASSISTANT, subtitle = if (aiEnabled) "AI help with preferences, bios and safety." else "Offline helper — AI assistance is off in Privacy Center.")
                }
            }
            itemsIndexed(turns) { _, t ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (t.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (t.fromUser) MaterialTheme.colorScheme.primary else AanganColors.extended.card,
                        contentColor = if (t.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (t.fromUser) null else BorderStroke(1.dp, AanganColors.extended.hairline),
                        shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 320.dp),
                    ) { Text(t.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (busy) item { Skeleton(Modifier.width(180.dp).height(46.dp), RoundedCornerShape(22.dp)) }
        }
    }
}
