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
        onCategoryFilter = viewModel::setCategoryFilter,
        onCreateChat = viewModel::createChat,
        onRenameChat = viewModel::renameChat,
        onSetCategory = viewModel::setChatCategory,
        onDeleteChat = viewModel::deleteChat,
        onToggleArchived = viewModel::toggleArchived,
        onToggleMuted = viewModel::toggleMuted,
        onSetShowArchived = viewModel::setShowArchived,
        onRefresh = viewModel::loadChats,
        onImportWhatsAppChat = viewModel::importWhatsAppChat,
        onChatClick = { chatId ->
            // Opening a chat clears its badge and any notification still in
            // the shade, so the two never disagree.
            viewModel.markChatRead(chatId)
            onChatClick(chatId)
        },
        onSettings = onSettings
    )
}
