package com.rustypastechat.ui.chat.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rustypastechat.data.model.LinkPreview
import com.rustypastechat.di.LinkPreviewEntryPoint
import com.rustypastechat.ui.components.GlassShape
import dagger.hilt.android.EntryPointAccessors

/**
 * Self-fetching URL unfurl card. Reached from a leaf composable (message bubble) via a Hilt
 * entry point rather than threading a repository callback through every intermediate composable.
 * Renders nothing until/unless a preview is actually available (fails silently — this is a
 * nice-to-have, not core functionality).
 */
@Composable
fun UrlPreview(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, LinkPreviewEntryPoint::class.java)
            .linkPreviewRepository()
    }
    var preview by remember(url) { mutableStateOf<LinkPreview?>(null) }

    LaunchedEffect(url) {
        preview = repository.fetch(url)
    }

    val p = preview ?: return

    Surface(
        onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
        shape = GlassShape.Small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            if (!p.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = p.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(10.dp)) {
                if (!p.title.isNullOrBlank()) {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!p.description.isNullOrBlank()) {
                    Text(
                        p.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    p.siteName?.takeIf { it.isNotBlank() } ?: (Uri.parse(url).host ?: url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
