package com.rustypastechat.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rustypastechat.ui.chat.ChatScreen
import com.rustypastechat.ui.chatlist.ChatListScreen
import com.rustypastechat.ui.settings.SettingsScreen

object Routes {
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}"
    const val SETTINGS = "settings"

    fun chatRoute(chatId: String) = "chat/$chatId"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT_LIST,
        enterTransition = { fadeIn(androidx.compose.animation.core.tween(300)) },
        exitTransition = { fadeOut(androidx.compose.animation.core.tween(300)) }
    ) {
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onChatClick = { chatId -> navController.navigate(Routes.chatRoute(chatId)) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: "default"
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
