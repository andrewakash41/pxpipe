package com.vachan.app.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dagger.hilt.android.lifecycle.HiltViewModel
import com.vachan.app.ui.root.Nav
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(private val ai: AiRepository) : ViewModel() {
    private val _turns = MutableStateFlow(
        listOf(AssistantTurn(false, "Namaste! I'm Vachan, your matchmaking assistant. I can help you refine preferences, write your bio, understand a match, or stay safe. I can't see your chats, and I never share anything about other members.")),
    )
    val turns = _turns.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val aiEnabled = ai.aiEnabled

    fun ask(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        _turns.value = _turns.value + AssistantTurn(true, text.trim().take(1000))
        _busy.value = true
        val reply = ai.ask(_turns.value)
        _turns.value = _turns.value + AssistantTurn(false, reply)
        _busy.value = false
    }
}

private val starters = listOf("How can I get better matches?", "Help me write my bio", "How do I spot a scam?", "How does Family Mode work?", "Why am I seeing these picks?")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(nav: Nav, vm: AssistantViewModel = hiltViewModel()) {
    val turns by vm.turns.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val aiEnabled by vm.aiEnabled.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size) { list.animateScrollToItem(turns.size) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Ask Vachan"); Text(if (aiEnabled) "AI assistant" else "Offline helper — AI assistance is off", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(starters) { s -> AssistChip(onClick = { vm.ask(s) }, label = { Text(s) }) }
                }
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Ask anything about finding a partner") }, shape = RoundedCornerShape(24.dp), maxLines = 4)
                    IconButton(onClick = { vm.ask(draft); draft = "" }, enabled = draft.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(turns) { t ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (t.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (t.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (t.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp),
                    ) { Text(t.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (busy) item { CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp) }
        }
    }
}
