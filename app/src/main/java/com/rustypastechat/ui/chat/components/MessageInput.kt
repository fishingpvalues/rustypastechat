package com.rustypastechat.ui.chat.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rustypastechat.ui.components.GlassCard
import com.rustypastechat.ui.components.GlassShape
import com.rustypastechat.ui.theme.Blue

private val TTL_PRESETS = listOf(
    0L to "",
    60L to "1m",
    300L to "5m",
    3600L to "1h",
    86400L to "1d"
)

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMediaSelected: (Uri) -> Unit,
    enabled: Boolean = true,
    isOneshotMode: Boolean = false,
    onToggleOneshot: () -> Unit = {},
    ttlSeconds: Long = 0L,
    onSetTtl: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onMediaSelected(it) } }
    var showTtlMenu by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = GlassShape.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Oneshot toggle
            IconButton(
                onClick = onToggleOneshot,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "View once",
                    tint = if (isOneshotMode) Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // TTL timer with dropdown
            Box {
                IconButton(
                    onClick = { showTtlMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Message expiry",
                        tint = if (ttlSeconds > 0) Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showTtlMenu,
                    onDismissRequest = { showTtlMenu = false }
                ) {
                    TTL_PRESETS.forEach { (secs, label) ->
                        DropdownMenuItem(
                            text = { Text(if (secs == 0L) "No expiry" else "Expire in $label") },
                            onClick = { onSetTtl(secs); showTtlMenu = false },
                            leadingIcon = {
                                if (ttlSeconds == secs) {
                                    Icon(Icons.Default.Timer, null, tint = Blue, modifier = Modifier.size(18.dp))
                                }
                            }
                        )
                    }
                }
            }

            // Gallery picker
            IconButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text field
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Message",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.width(2.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(40.dp),
                shape = CircleShape
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}
