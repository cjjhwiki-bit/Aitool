package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CanonicalAliasEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderEntity
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalMapperScreen(
    aliases: List<CanonicalAliasEntity>,
    models: List<ModelEntity>,
    providers: List<ProviderEntity>,
    onBack: () -> Unit,
    onAddAlias: (familyId: String, displayName: String, pattern: String) -> Unit,
    onDeleteAlias: (String) -> Unit,
    onBindModelCanonicalId: (modelKey: String, canonicalId: String) -> Unit,
) {
    BackHandler { onBack() }

    val providersById = remember(providers) { providers.associateBy { it.id } }
    var familyIdInput by remember { mutableStateOf("") }
    var patternInput by remember { mutableStateOf("") }
    var editingModel by remember { mutableStateOf<ModelEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型正規化映射管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "將不同供應商寫法的同一模型歸類至相同標準家族 ID（如 llama-3.3-70b），當發生 429 額度限制時即可自動識別同模型備援。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("新增自訂別名規則", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = familyIdInput,
                                onValueChange = { familyIdInput = it },
                                label = { Text("標準家族 ID") },
                                placeholder = { Text("llama-3.3-70b") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = patternInput,
                                onValueChange = { patternInput = it },
                                label = { Text("比對關鍵字") },
                                placeholder = { Text("llama3.3-70b") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
                            enabled = familyIdInput.isNotBlank() && patternInput.isNotBlank(),
                            onClick = {
                                onAddAlias(familyIdInput, familyIdInput, patternInput)
                                familyIdInput = ""
                                patternInput = ""
                            },
                        ) {
                            Text("新增規則並重新配對")
                        }
                    }
                }
            }

            item {
                Text("別名規則 (${aliases.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }

            items(aliases, key = { it.id }) { alias ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alias.canonicalFamilyId, fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold)
                        Text("比對關鍵字：${alias.matchPattern}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (alias.isUserDefined) {
                        IconButton(onClick = { onDeleteAlias(alias.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "刪除規則")
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("已同步模型綁定清單 (${models.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }

            items(models, key = { it.key }) { model ->
                val pName = providersById[model.providerId]?.name ?: model.providerId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$pName · ${model.modelId}", style = MaterialTheme.typography.bodyMedium)
                        Text("目前家族：${model.canonicalId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(onClick = { editingModel = model }) {
                        Text("手動綁定")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editingModel?.let { target ->
        var newCanonical by remember(target.key) { mutableStateOf(target.canonicalId) }
        AlertDialog(
            onDismissRequest = { editingModel = null },
            title = { Text("手動綁定標準家族 ID") },
            text = {
                OutlinedTextField(
                    value = newCanonical,
                    onValueChange = { newCanonical = it },
                    label = { Text("標準家族 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = newCanonical.isNotBlank(),
                    onClick = {
                        onBindModelCanonicalId(target.key, newCanonical)
                        editingModel = null
                    },
                ) { Text("確定") }
            },
            dismissButton = {
                TextButton(onClick = { editingModel = null }) { Text("取消") }
            },
        )
    }
}
