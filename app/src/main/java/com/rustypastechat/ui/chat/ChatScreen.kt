package com.rustypastechat.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustypastechat.ui.chat.components.EmptyChatState
import com.rustypastechat.ui.chat.components.MessageBubble
import com.rustypastechat.ui.chat.components.MessageInput
import com.rustypastechat.ui.chat.components.TypingIndicator
import com.rustypastechat.ui.theme.Blue

private val TTL_OPTIONS = listOf(0L to "Off", 60L to "1m", 300L to "5m", 3600L to "1h", 86400L to "1d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    deleteConfirmId?.let { msgId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete message") },
            text = { Text("Remove this message from the chat and server?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMessage(msgId); deleteConfirmId = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RustyPaste Chat", style = MaterialTheme.typography.titleMedium)
                        AnimatedVisibility(visible = uiState.isLlmTyping) {
                            Text("AI typing...", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (uiState.isConnected && !uiState.isLoading) {
                        IconButton(onClick = { viewModel.loadChatHistory() }, enabled = !uiState.isRefreshing) {
                            if (uiState.isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.MoreVert, "Settings") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                // Reply bar
                AnimatedVisibility(
                    visible = uiState.replyTarget != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    uiState.replyTarget?.let { reply ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Reply, null, tint = Blue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (reply.isOutgoing) "Replying to yourself" else "Replying",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Blue, fontWeight = FontWeight.SemiBold
                                )
                                Text(reply.text, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.setReplyTarget(null) }) { Text("Cancel") }
                        }
                    }
                }
                MessageInput(
                    value = uiState.typingMessage,
                    onValueChange = viewModel::updateTypingMessage,
                    onSend = viewModel::sendTextMessage,
                    onMediaSelected = { uri ->
                        viewModel.sendMediaMessage(uri.toString(), "image_${System.currentTimeMillis()}.jpg")
                    },
                    enabled = uiState.isConnected,
                    isOneshotMode = uiState.isOneshotMode,
                    onToggleOneshot = { viewModel.toggleOneshotMode() },
                    ttlSeconds = uiState.messageTtlSeconds,
                    onSetTtl = { viewModel.setMessageTtl(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding)
        ) {
            when {
                uiState.isLoading && !uiState.historyLoaded -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Loading chat history...", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                uiState.messages.isEmpty() -> {
                    PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = { viewModel.loadChatHistory() }) {
                        EmptyChatState()
                    }
                }
                else -> {
                    PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = { viewModel.loadChatHistory() }) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                        ) {
                            items(items = uiState.messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    onRetry = { viewModel.retryMessage(it) },
                                    onDelete = { deleteConfirmId = it },
                                    onCopy = { viewModel.copyMessage(it) },
                                    onReply = { viewModel.replyToMessage(it) },
                                    onForward = { viewModel.forwardMessage(it) }
                                )
                            }
                            if (uiState.isLlmTyping) {
                                item(key = "typing") { TypingIndicator() }
                            }
                            item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}
