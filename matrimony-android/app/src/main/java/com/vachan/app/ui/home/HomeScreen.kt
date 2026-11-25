package com.vachan.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.InterestStatus
import com.vachan.app.data.ChatRepository
import com.vachan.app.data.InterestRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.chat.ChatListScreen
import com.vachan.app.ui.components.NavItem
import com.vachan.app.ui.components.PillNavBar
import com.vachan.app.ui.discover.DiscoverScreen
import com.vachan.app.ui.interests.InterestsScreen
import com.vachan.app.ui.me.MeScreen
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.search.SearchScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    interests: InterestRepository,
    chat: ChatRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    val pendingReceived = combine(interests.interests, profiles.myProfile) { list, me ->
        list.count { it.toId == me?.id && it.status == InterestStatus.PENDING }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val unreadChats = chat.conversations.map { c -> c.count { it.unread > 0 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}

/** Space the floating tab bar occupies, so scrolling content can clear it. */
val NavBarClearance = PaddingValues(bottom = 96.dp)

@Composable
fun HomeScreen(nav: Nav, vm: HomeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pending by vm.pendingReceived.collectAsStateWithLifecycle()
    val unread by vm.unreadChats.collectAsStateWithLifecycle()
    // Back from any tab returns to Today before leaving the app.
    BackHandler(enabled = tab != 0) { tab = 0 }

    val items = listOf(
        NavItem("Today", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
        NavItem("Search", Icons.Outlined.Search, Icons.Filled.Search),
        NavItem("Interests", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite, badge = pending),
        NavItem("Chats", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat, badge = unread),
        NavItem("You", Icons.Outlined.Person, Icons.Filled.Person),
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                val m = Modifier.fillMaxSize()
                when (t) {
                    0 -> DiscoverScreen(nav, m)
                    1 -> SearchScreen(nav, m)
                    2 -> InterestsScreen(nav, m)
                    3 -> ChatListScreen(nav, m)
                    else -> MeScreen(nav, m)
                }
            }
            PillNavBar(items, selected = tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
