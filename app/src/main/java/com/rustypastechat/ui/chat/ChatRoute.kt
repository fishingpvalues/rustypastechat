package com.rustypastechat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatRoute(
    chatId: String,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(chatId) {
        viewModel.setChatId(chatId)
    }

    ChatScreen(
        chatId = chatId,
        uiState = uiState,
        onTypingChange = viewModel::updateTypingMessage,
        onSend = viewModel::sendTextMessage,
        onMediaSelected = { uri -> viewModel.compressAndSendMedia(uri) },
        onReplyTargetSet = viewModel::setReplyTarget,
        onToggleOneshot = viewModel::toggleOneshotMode,
        onSetTtl = viewModel::setMessageTtl,
        onSetSearchQuery = viewModel::setSearchQuery,
        onToggleSearch = viewModel::toggleSearchMode,
        onStartEditing = viewModel::startEditingMessage,
        onCancelEditing = viewModel::cancelEditing,
        onUpdateEditingText = viewModel::updateEditingText,
        onSaveEdit = viewModel::saveEditedMessage,
        onInsertFormatting = viewModel::insertFormatting,
        onCopyMessage = viewModel::copyMessage,
        onReplyToMessage = viewModel::replyToMessage,
        onDeleteMessage = viewModel::deleteMessage,
        onForwardMessage = viewModel::forwardMessage,
        onRetryMessage = viewModel::retryMessage,
        onRefresh = viewModel::loadChatHistory,
        getFilteredMessages = viewModel::getFilteredMessages,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateBack = onNavigateBack
    )
}
