package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MessageStatus
import com.example.data.local.MessageTurnEntity
import com.example.data.local.MessageVersionEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderEntity

@Composable
fun UserMessageCard(
    turn: MessageTurnEntity,
    version: MessageVersionEntity,
    isInterjection: Boolean,
    onEditAndResend: (String) -> Unit,
    onEditLocalOnly: (String) -> Unit,
    onResendPrompt: () -> Unit,
    onDeleteSingleTurn: () -> Unit,
    onDeleteWithFollowing: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            color = if (isInterjection) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (isInterjection) {
                    Text(
                        text = "主持人中途插話引導",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = version.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isInterjection) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(version.content)) },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "複製提問", modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = { showEditDialog = true },
                modifier = Modifier
                    .size(38.dp)
                    .testTag("edit_user_msg_${turn.id}"),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "編輯提問", modifier = Modifier.size(16.dp))
            }
            if (!isInterjection) {
                IconButton(
                    onClick = onResendPrompt,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "重新發送", modifier = Modifier.size(16.dp))
                }
            }
            Box {
                IconButton(
                    onClick = { showDeleteMenu = true },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "刪除提問", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showDeleteMenu,
                    onDismissRequest = { showDeleteMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("僅刪除此則提問") },
                        onClick = {
                            showDeleteMenu = false
                            onDeleteSingleTurn()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("連同此輪及後續回覆一併刪除") },
                        onClick = {
                            showDeleteMenu = false
                            onDeleteWithFollowing()
                        },
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var draft by remember(version.content) { mutableStateOf(version.content) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("編輯使用者輸入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_edit_textfield"),
                    )
                    Text(
                        text = "「僅修改本地文字紀錄」不會重送 API、不消耗額度；「儲存並重新向 AI 請求」將從此輪重新生成後續回覆。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            showEditDialog = false
                            onEditAndResend(draft)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_save_and_resend_btn"),
                    ) {
                        Text("儲存並重新向 AI 請求")
                    }
                    OutlinedButton(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            showEditDialog = false
                            onEditLocalOnly(draft)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_save_local_only_btn"),
                    ) {
                        Text("僅修改本地文字紀錄")
                    }
                    TextButton(
                        onClick = { showEditDialog = false },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageCard(
    turn: MessageTurnEntity,
    versionsInBranch: List<MessageVersionEntity>,
    activeVersion: MessageVersionEntity,
    collapseThinkingByDefault: Boolean,
    renderMarkdown: Boolean,
    renderLatex: Boolean,
    showAdoptButton: Boolean,
    isAdoptedBranch: Boolean,
    availableModels: List<ModelEntity>,
    providersById: Map<String, ProviderEntity>,
    onSelectVersion: (String) -> Unit,
    onRegenerate: (overrideModelKey: String?) -> Unit,
    onEditLocal: (String) -> Unit,
    onForkFromHere: () -> Unit,
    onDeleteTurn: () -> Unit,
    onAdoptBranch: () -> Unit,
    onInspectDebug: (MessageVersionEntity) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var thinkingExpanded by remember(activeVersion.id, collapseThinkingByDefault) {
        mutableStateOf(!collapseThinkingByDefault)
    }
    var showCopyMenu by remember { mutableStateOf(false) }
    var showSwitchModelMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showEditAiDialog by remember { mutableStateOf(false) }

    val currentIdx = versionsInBranch.indexOfFirst { it.id == activeVersion.id }.coerceAtLeast(0)
    val totalVersions = versionsInBranch.size.coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Model & Debug Badge Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onInspectDebug(activeVersion) }
                        .testTag("ai_debug_badge_${activeVersion.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val badgeLabel = buildString {
                            if (activeVersion.participantName.isNotBlank()) {
                                append("${activeVersion.participantName} · ")
                            }
                            append("${activeVersion.providerName} · ${activeVersion.modelId}")
                            if (activeVersion.isFailover) append(" ↻")
                        }
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (showAdoptButton) {
                    FilterChip(
                        selected = isAdoptedBranch,
                        onClick = onAdoptBranch,
                        leadingIcon = {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (isAdoptedBranch) "已採用為主線" else "✓ 採用此回答繼續") },
                        modifier = Modifier.testTag("adopt_branch_btn_${activeVersion.branchKey}"),
                    )
                }
            }

            if (activeVersion.messageStatus() == MessageStatus.STREAMING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Collapsible Thinking / Reasoning block
            if (activeVersion.reasoningContent.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { thinkingExpanded = !thinkingExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "深度思考過程 (${activeVersion.reasoningContent.length} 字)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Icon(
                                imageVector = if (thinkingExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (thinkingExpanded) "摺疊思考過程" else "展開思考過程",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        AnimatedVisibility(visible = thinkingExpanded) {
                            Text(
                                text = activeVersion.reasoningContent,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            // Main Content
            if (activeVersion.content.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                RichMarkdownText(
                    text = activeVersion.content,
                    renderMarkdown = renderMarkdown,
                    renderLatex = renderLatex,
                )
            }

            // Frozen / Error State Banner
            if (activeVersion.messageStatus() == MessageStatus.FROZEN_QUOTA ||
                activeVersion.messageStatus() == MessageStatus.ERROR
            ) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (activeVersion.messageStatus() == MessageStatus.FROZEN_QUOTA) {
                            "額度凍結保護（已保留上述半截內容）：${activeVersion.errorMessage}"
                        } else {
                            "錯誤：${activeVersion.errorMessage}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // AI Output Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Version Switcher ◀ 1 / N ▶
                if (totalVersions > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            enabled = currentIdx > 0,
                            onClick = { onSelectVersion(versionsInBranch[currentIdx - 1].id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一版本")
                        }
                        Text(
                            text = "${currentIdx + 1} / $totalVersions",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        IconButton(
                            enabled = currentIdx < versionsInBranch.lastIndex,
                            onClick = { onSelectVersion(versionsInBranch[currentIdx + 1].id) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一版本")
                        }
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy Menu
                    Box {
                        IconButton(
                            onClick = { showCopyMenu = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "複製選項", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showCopyMenu, onDismissRequest = { showCopyMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("純複製回答") },
                                onClick = {
                                    showCopyMenu = false
                                    clipboard.setText(AnnotatedString(activeVersion.content))
                                },
                            )
                            if (activeVersion.reasoningContent.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("連同思考過程複製") },
                                    onClick = {
                                        showCopyMenu = false
                                        val full = "<think>\n${activeVersion.reasoningContent}\n</think>\n\n${activeVersion.content}"
                                        clipboard.setText(AnnotatedString(full))
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("去除 Markdown 純文字複製") },
                                onClick = {
                                    showCopyMenu = false
                                    clipboard.setText(AnnotatedString(stripMarkdownFormatting(activeVersion.content)))
                                },
                            )
                        }
                    }

                    // Regenerate (tap = same model, long press = switch model menu)
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(40.dp)
                                .combinedClickable(
                                    onClick = { onRegenerate(null) },
                                    onLongClick = { showSwitchModelMenu = true },
                                ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "重新生成（長按換模型）", modifier = Modifier.size(18.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showSwitchModelMenu,
                            onDismissRequest = { showSwitchModelMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("選擇模型重新生成", fontWeight = FontWeight.Bold) },
                                onClick = {},
                                enabled = false,
                            )
                            availableModels.take(12).forEach { m ->
                                val pName = providersById[m.providerId]?.name ?: m.providerId
                                DropdownMenuItem(
                                    text = { Text("${m.displayName} ($pName)") },
                                    onClick = {
                                        showSwitchModelMenu = false
                                        onRegenerate(m.key)
                                    },
                                )
                            }
                        }
                    }

                    // Overflow actions
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多訊息操作", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("換模型重新生成") },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showSwitchModelMenu = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("編輯 AI 回覆內容") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showEditAiDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("從此處分岔新對話") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onForkFromHere()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("檢視參數快照與除錯") },
                                onClick = {
                                    showMoreMenu = false
                                    onInspectDebug(activeVersion)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("刪除此則回覆") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onDeleteTurn()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditAiDialog) {
        var draft by remember(activeVersion.content) { mutableStateOf(activeVersion.content) }
        AlertDialog(
            onDismissRequest = { showEditAiDialog = false },
            title = { Text("本地編輯 AI 回覆") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    showEditAiDialog = false
                    onEditLocal(draft)
                }) { Text("儲存修改") }
            },
            dismissButton = {
                TextButton(onClick = { showEditAiDialog = false }) { Text("取消") }
            },
        )
    }
}
