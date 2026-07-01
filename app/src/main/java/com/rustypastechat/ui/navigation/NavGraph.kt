package com.rustypastechat.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.CHAT_LIST
    val showBottomNav = currentRoute in listOf(Routes.CHAT_LIST, Routes.SETTINGS)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {}
    }

    Column {
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT_LIST,
            enterTransition = { fadeIn(androidx.compose.animation.core.tween(300)) },
            exitTransition = { fadeOut(androidx.compose.animation.core.tween(300)) },
            modifier = Modifier.weight(1f)
        ) {
                composable(Routes.CHAT_LIST) {
                    ChatListScreen(
                        onChatClick = { navController.navigate(Routes.chatRoute(it)) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onCamera = { cameraLauncher.launch(null) }
                    )
                }
                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: "default"
                    ChatScreen(
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
        if (showBottomNav) {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.CHAT_LIST) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
