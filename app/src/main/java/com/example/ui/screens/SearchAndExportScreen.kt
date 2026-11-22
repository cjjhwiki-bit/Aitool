package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SearchMessageResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndExportScreen(
    searchResults: List<SearchMessageResult>,
    hasActiveConversation: Boolean,
    onBack: () -> Unit,
    onSearch: (query: String, includeReasoning: Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    onExportJson: suspend () -> String,
    onImportJson: (String) -> Unit,
    onExportMarkdown: suspend () -> String,
    onShowMessage: (String) -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var includeReasoning by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val data = onExportJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess { onShowMessage("完整 JSON 備份匯出成功") }
                    .onFailure { onShowMessage("匯出失敗：${it.localizedMessage}") }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }.orEmpty()
                }.onSuccess { json ->
                    if (json.isNotBlank()) onImportJson(json)
                }.onFailure { onShowMessage("讀取備份失敗：${it.localizedMessage}") }
            }
        }
    }

    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val md = onExportMarkdown()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(md.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess { onShowMessage("Markdown 匯出成功") }
                    .onFailure { onShowMessage("匯出失敗：${it.localizedMessage}") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地全量搜尋與資料匯出入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回對話")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("資料可攜性（SAF 零權限安全存取）", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { exportJsonLauncher.launch("polychat_backup.json") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_json_btn"),
                            ) {
                                Icon(Icons.Outlined.Upload, contentDescription = null)
                                Spacer(Modifier.height(4.dp))
                                Text("匯出 JSON 備份")
                            }
                            OutlinedButton(
                                onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_json_btn"),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(Modifier.height(4.dp))
                                Text("匯入還原")
                            }
                        }
                        if (hasActiveConversation) {
                            OutlinedButton(
                                onClick = { exportMdLauncher.launch("conversation.md") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("匯出目前對話為純閱讀 Markdown (.md)")
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onSearch(it, includeReasoning)
                        },
                        label = { Text("搜尋對話標題或訊息關鍵字") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_search_input"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("搜尋範圍包含深度思考過程 (Reasoning)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = includeReasoning,
                            onCheckedChange = {
                                includeReasoning = it
                                onSearch(query, it)
                            },
                        )
                    }
                }
            }

            if (query.isNotBlank() && searchResults.isEmpty()) {
                item {
                    Text(
                        text = "找不到符合「$query」的本地紀錄。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(searchResults, key = { it.versionId }) { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenConversation(item.conversationId) },
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = item.conversationTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (item.role == "USER") "使用者" else item.modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = item.content.ifBlank { item.reasoningContent },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
