package com.vachan.app.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.core.model.InterestStatus
import com.vachan.app.data.ChatRepository
import com.vachan.app.data.InterestRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.chat.ChatListScreen
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

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Daily picks", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Tab("Search", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("Interests", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    Tab("Chats", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    Tab("Me", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun HomeScreen(nav: Nav, vm: HomeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pending by vm.pendingReceived.collectAsStateWithLifecycle()
    val unread by vm.unreadChats.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    val badge = when (i) { 2 -> pending; 3 -> unread; else -> 0 }
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
                                Icon(if (tab == i) t.selectedIcon else t.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> DiscoverScreen(nav, m)
            1 -> SearchScreen(nav, m)
            2 -> InterestsScreen(nav, m)
            3 -> ChatListScreen(nav, m)
            else -> MeScreen(nav, m)
        }
    }
}
