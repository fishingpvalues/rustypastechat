package com.rustypastechat.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.rustypastechat.ui.chat.ChatRoute
import com.rustypastechat.ui.chatlist.ChatListRoute
import com.rustypastechat.ui.settings.SettingsRoute

@Composable
fun NavGraph() {
    val navigationState = rememberNavigationState(
        startRoute = ChatList,
        topLevelRoutes = TOP_LEVEL_ROUTES
    )
    val navigator = remember { Navigator(navigationState) }
    val currentTopLevelRoute = navigationState.topLevelRoute

    val entryProvider = remember(navigator) {
        entryProvider<NavKey> {
            entry<ChatList> {
                ChatListRoute(
                    onChatClick = { chatId -> navigator.navigate(Chat(chatId)) },
                    onSettings = { navigator.navigate(Settings) }
                )
            }
            entry<Chat> { key ->
                ChatRoute(
                    chatId = key.chatId,
                    onNavigateToSettings = { navigator.navigate(Settings) },
                    onNavigateBack = { navigator.goBack() }
                )
            }
            entry<Settings> {
                SettingsRoute(
                    onNavigateBack = { navigator.goBack() }
                )
            }
        }
    }

    val navBarItems = listOf(
        AnimatedNavItem(ChatList, "Chats", Icons.Rounded.Chat, Icons.Outlined.Chat),
        AnimatedNavItem(Settings, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
    )

    val showBottomNav = !navigationState.isDeepInStack

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                AnimatedBottomNavBar(
                    items = navBarItems,
                    currentRoute = currentTopLevelRoute,
                    onNavigate = { route -> navigator.navigate(route) }
                )
            }
        }
    ) { innerPadding ->
        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            modifier = Modifier
        )
    }
}
