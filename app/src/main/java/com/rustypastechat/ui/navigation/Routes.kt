package com.rustypastechat.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ChatList : NavKey

@Serializable
data class Chat(val chatId: String) : NavKey

@Serializable
data object Settings : NavKey

val TOP_LEVEL_ROUTES: Set<NavKey> = setOf(ChatList, Settings)
