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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustypastechat.ui.chat.components.EmptyChatState
import com.rustypastechat.ui.chat.components.MessageInput
import com.rustypastechat.ui.chat.components.SwipeableMessageBubble
import com.rustypastechat.ui.chat.components.TypingIndicator
import com.rustypastechat.ui.theme.Blue

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
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectMode = selectedIds.isNotEmpty()
    val editedId = uiState.editingMessageId

    val displayMessages = if (uiState.searchQuery.isNotBlank()) viewModel.getFilteredMessages() else uiState.messages

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
            text = { Text("Remove from chat and paste server?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteMessage(msgId); deleteConfirmId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            when {
                uiState.isSearchMode -> {
                    // Search mode bar
                    CenterAlignedTopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { viewModel.toggleSearchMode() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                            }
                        },
                        title = {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                placeholder = { Text("Search messages", style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
                isSelectMode -> {
                    CenterAlignedTopAppBar(
                        title = { Text("${selectedIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                            }
                        },
                        actions = {
                            if (selectedIds.size == 1) {
                                IconButton(onClick = {
                                    viewModel.startEditingMessage(selectedIds.first())
                                    selectedIds = emptySet()
                                }) { Icon(Icons.Default.Edit, "Edit") }
                            }
                            IconButton(onClick = {
                                selectedIds.forEach { viewModel.copyMessage(it) }; selectedIds = emptySet()
                            }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            IconButton(onClick = {
                                selectedIds.forEach { viewModel.forwardMessage(it) }; selectedIds = emptySet()
                            }) { Icon(Icons.Default.Forward, "Forward") }
                            IconButton(onClick = {
                                selectedIds.forEach { viewModel.deleteMessage(it) }; selectedIds = emptySet()
                            }) { Icon(Icons.Default.Delete, "Delete") }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
                else -> {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("RustyPaste Chat", style = MaterialTheme.typography.titleMedium)
                                AnimatedVisibility(visible = uiState.isLlmTyping) {
                                    Text("AI typing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleSearchMode() }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                            IconButton(onClick = {
                                viewModel.loadChatHistory()
                            }, enabled = !uiState.isRefreshing) {
                                if (uiState.isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, "Refresh")
                            }
                            Box {
                                IconButton(onClick = { overflowMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, "More")
                                }
                                DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Refresh chat") }, onClick = { viewModel.loadChatHistory(); overflowMenuExpanded = false },
                                        leadingIcon = { Icon(Icons.Default.Refresh, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("View once mode") }, onClick = { viewModel.toggleOneshotMode(); overflowMenuExpanded = false },
                                        leadingIcon = { Icon(Icons.Default.Whatshot, null, tint = if (uiState.isOneshotMode) Blue else MaterialTheme.colorScheme.onSurfaceVariant) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.messageTtlSeconds > 0) "Expiry: ${formatTtl(uiState.messageTtlSeconds)}" else "Message expiry") },
                                        onClick = { viewModel.setMessageTtl(when (uiState.messageTtlSeconds) { 0L -> 300L; 300L -> 3600L; 3600L -> 86400L; else -> 0L }); overflowMenuExpanded = false },
                                        leadingIcon = { Icon(Icons.Default.Timer, null, tint = if (uiState.messageTtlSeconds > 0) Blue else MaterialTheme.colorScheme.onSurfaceVariant) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") }, onClick = { onNavigateToSettings(); overflowMenuExpanded = false },
                                        leadingIcon = { Icon(Icons.Default.MoreVert, null) }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isSelectMode && !uiState.isSearchMode) {
                Column {
                    // Edit mode bar
                    AnimatedVisibility(visible = editedId != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 2.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, null, tint = Blue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Editing message", style = MaterialTheme.typography.labelSmall, color = Blue, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { viewModel.cancelEditing() }) { Text("Cancel") }
                                FilledTonalButton(onClick = { viewModel.saveEditedMessage() }) {
                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }
                    // Reply bar
                    AnimatedVisibility(
                        visible = uiState.replyTarget != null && editedId == null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        uiState.replyTarget?.let { reply ->
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Reply, null, tint = Blue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (reply.isOutgoing) "Replying to yourself" else "Replying", style = MaterialTheme.typography.labelSmall, color = Blue, fontWeight = FontWeight.SemiBold)
                                    Text(reply.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { viewModel.setReplyTarget(null) }) { Text("Cancel") }
                            }
                        }
                    }

                    // Formatting toolbar
                    FormattingToolbar(onFormat = { viewModel.insertFormatting(it) })

                    // Input
                    if (editedId != null) {
                        EditingInput(
                            value = uiState.editingMessageText,
                            onValueChange = viewModel::updateEditingText,
                            onSend = viewModel::saveEditedMessage
                        )
                    } else {
                        MessageInput(
                            value = uiState.typingMessage,
                            onValueChange = viewModel::updateTypingMessage,
                            onSend = viewModel::sendTextMessage,
                            onMediaSelected = { uri -> viewModel.sendMediaMessage(uri.toString(), "img_${System.currentTimeMillis()}.jpg") },
                            enabled = uiState.isConnected
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding)) {
            when {
                uiState.isLoading && !uiState.historyLoaded -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(); Spacer(Modifier.height(8.dp))
                            Text("Loading chat...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                displayMessages.isEmpty() -> {
                    PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = { viewModel.loadChatHistory() }) {
                        if (uiState.searchQuery.isNotBlank()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No messages matching '${uiState.searchQuery}'", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else EmptyChatState()
                    }
                }
                else -> {
                    PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = { viewModel.loadChatHistory() }) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                            items(items = displayMessages, key = { it.id }) { message ->
                                SwipeableMessageBubble(
                                    message = message, isSelected = message.id in selectedIds,
                                    onRetry = { viewModel.retryMessage(it) },
                                    onDelete = { deleteConfirmId = it },
                                    onCopy = { viewModel.copyMessage(it) },
                                    onReply = { viewModel.replyToMessage(it) },
                                    onForward = { viewModel.forwardMessage(it) },
                                    onLongPress = { id ->
                                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                    }
                                )
                            }
                            if (uiState.isLlmTyping) item(key = "typing") { TypingIndicator() }
                            item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattingToolbar(onFormat: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            FormatButton(Icons.Default.FormatBold, "Bold", " **bold** ") { onFormat("**") }
            FormatButton(Icons.Default.FormatItalic, "Italic", " *italic* ") { onFormat("*") }
            FormatButton(Icons.Default.FormatUnderlined, "Underline", " __underline__ ") { onFormat("__") }
            FormatButton(Icons.Default.StrikethroughS, "Strike", " ~~strike~~ ") { onFormat("~~") }
        }
    }
}

@Composable
private fun FormatButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EditingInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 4, textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.width(4.dp))
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.size(44.dp), shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(Icons.Default.Check, "Save edit", modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun formatTtl(seconds: Long): String = when {
    seconds < 120 -> "${seconds}s"; seconds < 7200 -> "${seconds / 60}min"; seconds < 172800 -> "${seconds / 3600}h"; else -> "${seconds / 86400}d"
}
