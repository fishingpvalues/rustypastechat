package com.rustypastechat.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rustypastechat.ui.components.GlassCard
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/**
 * A small, curated set of offline developer utilities (audited against corentinth/it-tools'
 * full 86-tool catalog and narrowed to the handful that fit a paste/chat app without turning
 * it into a general tool suite): UUID, Base64, hashing, JSON formatting, QR codes, text diff.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsPage(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Tools", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            ToolCard("UUID Generator") { UuidTool() }
            Spacer(Modifier.height(12.dp))
            ToolCard("Base64 Encode / Decode") { Base64Tool() }
            Spacer(Modifier.height(12.dp))
            ToolCard("Hash Generator") { HashTool() }
            Spacer(Modifier.height(12.dp))
            ToolCard("JSON Formatter") { JsonTool() }
            Spacer(Modifier.height(12.dp))
            ToolCard("QR Code Generator") { QrTool() }
            Spacer(Modifier.height(12.dp))
            ToolCard("Text Diff") { DiffTool() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToolCard(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            // content() is always composed (never gated behind `if`) so each tool's remember{}
            // state (typed input, generated results) survives collapsing and re-expanding the
            // card — only visually collapsed to zero height, not removed from composition.
            val bodyModifier = if (expanded) {
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp)
            } else {
                Modifier.height(0.dp).clipToBounds()
            }
            Column(modifier = bodyModifier) {
                content()
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun ResultBox(text: String, isError: Boolean = false) {
    val context = LocalContext.current
    if (text.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (!isError) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { copyToClipboard(context, "tool-result", text) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun UuidTool() {
    var uuid by remember { mutableStateOf(UUID.randomUUID().toString()) }
    Column {
        FilledTonalButton(onClick = { uuid = UUID.randomUUID().toString() }, modifier = Modifier.fillMaxWidth()) {
            Text("Generate")
        }
        Spacer(Modifier.height(8.dp))
        ResultBox(uuid)
    }
}

@Composable
private fun Base64Tool() {
    var input by remember { mutableStateOf("") }
    var encode by remember { mutableStateOf(true) }
    val result = remember(input, encode) {
        if (input.isBlank()) "" else runCatching {
            if (encode) java.util.Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
            else String(java.util.Base64.getDecoder().decode(input.trim()), Charsets.UTF_8)
        }.getOrElse { "Invalid input for ${if (encode) "encoding" else "decoding"}" }
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = encode, onClick = { encode = true }, label = { Text("Encode") })
            FilterChip(selected = !encode, onClick = { encode = false }, label = { Text("Decode") })
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text(if (encode) "Plain text" else "Base64 text") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
            shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.height(8.dp))
        ResultBox(result)
    }
}

@Composable
private fun HashTool() {
    var input by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf("SHA-256") }
    val result = remember(input, algorithm) {
        if (input.isBlank()) "" else runCatching {
            MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }.getOrElse { "Could not hash input" }
    }
    Column {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Text") }, modifier = Modifier.fillMaxWidth(),
            minLines = 2, maxLines = 5, shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MD5", "SHA-1", "SHA-256").forEach { alg ->
                FilterChip(selected = algorithm == alg, onClick = { algorithm = alg }, label = { Text(alg, style = MaterialTheme.typography.labelSmall) })
            }
        }
        Spacer(Modifier.height(8.dp))
        ResultBox(result)
    }
}

@Composable
private fun JsonTool() {
    var input by remember { mutableStateOf("") }
    var pretty by remember { mutableStateOf(true) }
    val result = remember(input, pretty) {
        if (input.isBlank()) "" else runCatching {
            val element = Json.parseToJsonElement(input)
            val json = Json { prettyPrint = pretty }
            json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
        }.getOrElse { "Invalid JSON" }
    }
    val isError = result == "Invalid JSON"
    Column {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("JSON") }, modifier = Modifier.fillMaxWidth(),
            minLines = 3, maxLines = 8, shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = pretty, onClick = { pretty = true }, label = { Text("Pretty") })
            FilterChip(selected = !pretty, onClick = { pretty = false }, label = { Text("Minify") })
        }
        Spacer(Modifier.height(8.dp))
        ResultBox(result, isError)
    }
}

@Composable
private fun QrTool() {
    var input by remember { mutableStateOf("") }
    val bitmap = remember(input) {
        if (input.isBlank()) null else runCatching {
            val size = 512
            val matrix = QRCodeWriter().encode(input, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }
    Column {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            label = { Text("Text or URL") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.height(8.dp))
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR code",
                modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally)
            )
        } else if (input.isNotBlank()) {
            Text("Could not generate QR code", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private sealed interface DiffLine {
    data class Same(val text: String) : DiffLine
    data class Added(val text: String) : DiffLine
    data class Removed(val text: String) : DiffLine
}

private fun computeLineDiff(oldLines: List<String>, newLines: List<String>): List<DiffLine> {
    val m = oldLines.size
    val n = newLines.size
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in m - 1 downTo 0) {
        for (j in n - 1 downTo 0) {
            dp[i][j] = if (oldLines[i] == newLines[j]) dp[i + 1][j + 1] + 1
            else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val result = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < m && j < n) {
        when {
            oldLines[i] == newLines[j] -> { result.add(DiffLine.Same(oldLines[i])); i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> { result.add(DiffLine.Removed(oldLines[i])); i++ }
            else -> { result.add(DiffLine.Added(newLines[j])); j++ }
        }
    }
    while (i < m) { result.add(DiffLine.Removed(oldLines[i])); i++ }
    while (j < n) { result.add(DiffLine.Added(newLines[j])); j++ }
    return result
}

@Composable
private fun DiffTool() {
    var before by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    val diff = remember(before, after) {
        if (before.isBlank() && after.isBlank()) emptyList()
        else computeLineDiff(before.split("\n"), after.split("\n"))
    }
    Column {
        OutlinedTextField(
            value = before, onValueChange = { before = it },
            label = { Text("Before") }, modifier = Modifier.fillMaxWidth(),
            minLines = 2, maxLines = 5, shape = MaterialTheme.shapes.medium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = after, onValueChange = { after = it },
            label = { Text("After") }, modifier = Modifier.fillMaxWidth(),
            minLines = 2, maxLines = 5, shape = MaterialTheme.shapes.medium
        )
        if (diff.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                diff.forEach { line ->
                    val (prefix, color, text) = when (line) {
                        is DiffLine.Same -> Triple("  ", MaterialTheme.colorScheme.onSurfaceVariant, line.text)
                        is DiffLine.Added -> Triple("+ ", com.rustypastechat.ui.theme.RustyColors.Success, line.text)
                        is DiffLine.Removed -> Triple("- ", MaterialTheme.colorScheme.error, line.text)
                    }
                    Text(
                        "$prefix$text",
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
