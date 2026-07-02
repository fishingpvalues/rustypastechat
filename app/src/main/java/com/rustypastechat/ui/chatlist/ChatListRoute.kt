package com.rustypastechat.ui.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatListRoute(
    onChatClick: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatListScreen(
        state = state,
        onSearchQueryChanged = viewModel::setSearchQuery,
        onClearSearch = viewModel::clearSearch,
        onCreateChat = viewModel::createChat,
        onRenameChat = viewModel::renameChat,
        onDeleteChat = viewModel::deleteChat,
        onRefresh = viewModel::loadChats,
        onChatClick = onChatClick,
        onSettings = onSettings
    )
}
