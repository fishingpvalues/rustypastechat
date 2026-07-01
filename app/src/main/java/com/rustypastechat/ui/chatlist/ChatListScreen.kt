package com.rustypastechat.ui.chatlist

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.ui.theme.Blue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newChatName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.MoreVert, "Settings") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = Blue) {
                Icon(Icons.Default.Chat, "New chat", tint = Color.White)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.chats.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Loading chats...", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.chats.isEmpty() && !state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No chats yet.\nSend a message to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.chats, key = { it.id }) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id) },
                                onRename = { name -> viewModel.renameChat(chat.id, name) },
                                onDelete = { viewModel.deleteChat(chat.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Chat") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newChatName, onValueChange = { newChatName = it },
                    label = { Text("Chat name") }, singleLine = true
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (newChatName.isNotBlank()) {
                        viewModel.createChat(newChatName)
                        newChatName = ""; showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChatListItem(chat: ChatThread, onClick: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    val timeText = remember(chat.lastTimestamp) {
        if (chat.lastTimestamp == 0L) ""
        else {
            val fmt = if (System.currentTimeMillis() - chat.lastTimestamp > 86400000L) "dd.MM." else "HH:mm"
            SimpleDateFormat(fmt, Locale.getDefault()).format(Date(chat.lastTimestamp))
        }
    }

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                }
                if (chat.messageCount > 0 && chat.lastMessage.isBlank()) {
                    Text("${chat.messageCount} imported pastes", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
