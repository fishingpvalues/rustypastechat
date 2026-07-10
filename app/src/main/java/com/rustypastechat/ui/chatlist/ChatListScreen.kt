package com.rustypastechat.ui.chatlist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rustypastechat.ui.animations.rememberFadeInScaleAnim
import com.rustypastechat.ui.animations.shimmerEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rustypastechat.data.model.ChatCategory
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.data.model.Message
import com.rustypastechat.ui.components.GlassCard
import com.rustypastechat.ui.components.RustyMark
import com.rustypastechat.ui.components.GlassShape
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatListScreen(
    state: ChatListState,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCategoryFilter: (ChatCategory) -> Unit,
    onCreateChat: (String, ChatCategory) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onSetCategory: (String, ChatCategory) -> Unit,
    onDeleteChat: (String) -> Unit,
    onRefresh: () -> Unit,
    onChatClick: (String) -> Unit,
    onSettings: () -> Unit
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<ChatThread?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { /* placeholder */ }

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
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Chats",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    actions = {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { onSettings(); overflowExpanded = false },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        floatingActionButton = {
            if (!state.isSearching) {
                FloatingActionButton(
                    onClick = { showCreateSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Add, "New chat")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            // Category filter chips
            if (!state.isSearching && state.chats.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { onCategoryFilter(ChatCategory.GENERAL) }, // toggle off
                            label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = if (state.selectedCategory == null) {
                                { Icon(Icons.Rounded.Category, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                    items(ChatCategory.values().toList()) { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { onCategoryFilter(category) },
                            label = {
                                Text(category.displayName, style = MaterialTheme.typography.labelMedium)
                            }
                        )
                    }
                }
            }

            // Content
            when {
                state.isLoading && state.chats.isEmpty() -> {
                    ChatListSkeleton()
                }
                state.isSearching -> {
                    if (state.searchResults.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results for \"${state.searchQuery}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.searchResults, key = { it.id }) { msg ->
                                SearchResultItem(msg = msg, onClick = { msg.chatId?.let { onChatClick(it) } })
                            }
                        }
                    }
                }
                state.chats.isEmpty() && !state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RustyMark(
                                modifier = Modifier.size(56.dp),
                                markColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                contentColor = MaterialTheme.colorScheme.background
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No chats yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap + to create your first chat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
                        items(state.chats, key = { it.id }) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onChatClick(chat.id) },
                                onLongClick = { showDetailDialog = chat }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    // Create chat ModalBottomSheet
    if (showCreateSheet) {
        CreateChatSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, category ->
                onCreateChat(name, category)
                showCreateSheet = false
            }
        )
    }

    // Chat detail dialog
    showDetailDialog?.let { chat ->
        ChatDetailDialog(
            chat = chat,
            onDismiss = { showDetailDialog = null },
            onRename = { newName -> onRenameChat(chat.id, newName); showDetailDialog = null },
            onSetCategory = { cat -> onSetCategory(chat.id, cat); showDetailDialog = null },
            onDelete = { showDeleteConfirm = chat.id; showDetailDialog = null }
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { chatId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Chat") },
            text = { Text("This will remove the chat from your list. Messages on the server are not affected.") },
            confirmButton = {
                TextButton(onClick = { onDeleteChat(chatId); showDeleteConfirm = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Avatar(chat: ChatThread, size: androidx.compose.ui.unit.Dp, textStyle: androidx.compose.ui.text.TextStyle) {
    Box(
        Modifier.size(size).clip(CircleShape).background(chat.avatarColorCompose),
        contentAlignment = Alignment.Center
    ) {
        Text(
            chat.name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = textStyle
        )
    }
}

@Composable
private fun ChatListSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
        repeat(6) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(base)
                        .shimmerEffect(base, highlight)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(base)
                            .shimmerEffect(base, highlight)
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(base)
                            .shimmerEffect(base, highlight)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(chat: ChatThread, onClick: () -> Unit, onLongClick: () -> Unit) {
    val timeText = remember(chat.lastTimestamp) {
        if (chat.lastTimestamp == 0L) ""
        else {
            val now = System.currentTimeMillis()
            val diff = now - chat.lastTimestamp
            if (diff < 86400000L) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastTimestamp))
            else if (diff < 604800000L) SimpleDateFormat("EEE", Locale.getDefault()).format(Date(chat.lastTimestamp))
            else SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(chat.lastTimestamp))
        }
    }

    val entryAnim = rememberFadeInScaleAnim(initialScale = 0.94f, targetScale = 1f)
    GlassCard(
        onClick = onClick,
        shape = GlassShape.Small,
        containerColor = MaterialTheme.colorScheme.surface,
        borderAlpha = 0.04f,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = entryAnim.value
                scaleY = entryAnim.value
                alpha = entryAnim.value
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(chat = chat, size = 48.dp, textStyle = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    if (chat.unreadCount > 0) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${chat.unreadCount.coerceAtMost(99)}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (timeText.isNotBlank()) {
                        Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (chat.lastMessage.isNotBlank()) {
                    Text(
                        chat.lastMessage, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (chat.messageCount > 0) {
                    Text(
                        "${chat.messageCount} messages", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateChatSheet(
    onDismiss: () -> Unit,
    onCreate: (String, ChatCategory) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chatName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ChatCategory.GENERAL) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            Text("New Chat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Create a new chat topic for organizing your conversations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = chatName,
                onValueChange = { chatName = it.take(30) },
                label = { Text("Chat name") },
                placeholder = { Text("e.g. Project Alpha, Weekend Plans") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(Modifier.height(16.dp))

            Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChatCategory.values().forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.displayName, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            androidx.compose.material3.Button(
                onClick = { onCreate(chatName.ifBlank { selectedCategory.name.lowercase().replaceFirstChar { it.uppercase() } }, selectedCategory) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create Chat")
            }
        }
    }
}

@Composable
private fun ChatDetailDialog(
    chat: ChatThread,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onSetCategory: (ChatCategory) -> Unit,
    onDelete: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(chat.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(chat = chat, size = 36.dp, textStyle = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                Text(chat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("Category") },
                    supportingContent = { Text(chat.category.displayName) },
                    leadingContent = { Icon(Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { showCategory = true })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (showCategory) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChatCategory.values().forEach { cat ->
                            FilterChip(
                                selected = chat.category == cat,
                                onClick = { onSetCategory(cat) },
                                label = { Text(cat.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Messages") },
                    supportingContent = { Text("${chat.messageCount} messages in this chat") },
                    leadingContent = { Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Rename", color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { showRename = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (showRename) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it.take(30) },
                        label = { Text("New name") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { onRename(renameText.ifBlank { chat.name }) }) { Text("Save") }
                        },
                        shape = MaterialTheme.shapes.medium
                    )
                }

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onDelete() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun SearchResultItem(msg: Message, onClick: () -> Unit) {
    val entryAnim = rememberFadeInScaleAnim(initialScale = 0.94f, targetScale = 1f)
    GlassCard(
        onClick = onClick,
        shape = GlassShape.Small,
        containerColor = MaterialTheme.colorScheme.surface,
        borderAlpha = 0.04f,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = entryAnim.value
                scaleY = entryAnim.value
                alpha = entryAnim.value
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(msg.text.take(120), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "Chat: ${msg.chatId.take(8)} \u00B7 ${SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(Date(msg.timestamp))}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
