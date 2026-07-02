package com.rustypastechat.ui.chatlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.data.model.Message
import com.rustypastechat.ui.components.GlassCard
import com.rustypastechat.ui.components.GlassShape
import com.rustypastechat.ui.theme.Blue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    state: ChatListState,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCreateChat: (String) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onRefresh: () -> Unit,
    onChatClick: (String) -> Unit,
    onSettings: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newChatName by remember { mutableStateOf("") }
    var overflowExpanded by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        // Camera capture handled here
    }

    Scaffold(
        topBar = {
            if (state.isSearching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search messages...", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Default.Close, "Close search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "RustyPaste Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    actions = {
                        IconButton(onClick = { onSearchQueryChanged(state.searchQuery) }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { cameraLauncher.launch(null) }) {
                            Icon(Icons.Default.CameraAlt, "Camera")
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(text = { Text("New group") }, onClick = { showCreateDialog = true; overflowExpanded = false },
                                    leadingIcon = { Icon(Icons.Default.Chat, null) })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { onSettings(); overflowExpanded = false },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = Blue) {
                Icon(Icons.Default.Chat, "New chat", tint = Color.White)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when {
                state.isLoading && state.chats.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(); Spacer(Modifier.height(8.dp))
                            Text("Loading...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                state.isSearching -> {
                    if (state.searchResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results for \"${state.searchQuery}\"", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.searchResults, key = { it.id }) { msg ->
                                SearchResultItem(msg = msg, onClick = { msg.chatId?.let { onChatClick(it) } })
                            }
                        }
                    }
                }
                state.chats.isEmpty() && !state.isLoading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No chats yet.\nTap + to start.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                else ->
                    LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
                        items(state.chats, key = { it.id }) { chat ->
                            ChatListItem(chat = chat, onClick = { onChatClick(chat.id) })
                        }
                    }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(onDismissRequest = { showCreateDialog = false }, title = { Text("New Chat") },
            text = {
                OutlinedTextField(value = newChatName, onValueChange = { newChatName = it },
                    label = { Text("Chat name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newChatName.isNotBlank()) { onCreateChat(newChatName); newChatName = ""; showCreateDialog = false }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun ChatListItem(chat: ChatThread, onClick: () -> Unit) {
    val timeText = remember(chat.lastTimestamp) {
        if (chat.lastTimestamp == 0L) ""
        else {
            val fmt = if (System.currentTimeMillis() - chat.lastTimestamp > 86400000L) "dd.MM." else "HH:mm"
            SimpleDateFormat(fmt, Locale.getDefault()).format(Date(chat.lastTimestamp))
        }
    }

    GlassCard(
        onClick = onClick,
        shape = GlassShape.Small,
        containerColor = MaterialTheme.colorScheme.surface,
        borderAlpha = 0.04f,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Blue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.name.take(1).uppercase(), color = Blue, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    if (timeText.isNotBlank()) {
                        Text(timeText, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (chat.lastMessage.isNotBlank()) {
                    Text(chat.lastMessage, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (chat.messageCount > 0) {
                    Text("${chat.messageCount} imported pastes", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(msg: Message, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        shape = GlassShape.Small,
        containerColor = MaterialTheme.colorScheme.surface,
        borderAlpha = 0.04f,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                msg.text.take(120),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Chat: ${msg.chatId.take(8)} - ${SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(msg.timestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
