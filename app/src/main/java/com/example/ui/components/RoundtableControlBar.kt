package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.RoundtableRuntimeState

@Composable
fun RoundtableControlBar(
    state: RoundtableRuntimeState,
    participantCount: Int,
    onResumeOrStart: () -> Unit,
    onPause: () -> Unit,
    onStepNext: () -> Unit,
    onStop: () -> Unit,
    onInjectGuidance: (String) -> Unit,
) {
    var guidanceText by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isRunning) {
                            "圓桌進行中 · 第 ${state.currentRound}/${state.maxRounds} 回合"
                        } else if (state.isPaused) {
                            "圓桌已暫停 · 第 ${state.currentRound}/${state.maxRounds} 回合"
                        } else {
                            "AI 圓桌互聊就緒（$participantCount 位模型）"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (state.currentSpeakerName.isNotBlank()) {
                        Text(
                            text = "目前發言者：${state.currentSpeakerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.isRunning) {
                        FilledTonalIconButton(
                            onClick = onPause,
                            modifier = Modifier.testTag("roundtable_pause_btn"),
                        ) {
                            Icon(Icons.Outlined.Pause, contentDescription = "暫停互聊")
                        }
                    } else {
                        FilledTonalIconButton(
                            enabled = participantCount >= 2,
                            onClick = onResumeOrStart,
                            modifier = Modifier.testTag("roundtable_play_btn"),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = "開始或繼續互聊")
                        }
                    }

                    FilledTonalIconButton(
                        enabled = participantCount >= 2 && !state.isRunning,
                        onClick = onStepNext,
                        modifier = Modifier.testTag("roundtable_step_btn"),
                    ) {
                        Icon(Icons.Outlined.SkipNext, contentDescription = "單步下一輪")
                    }

                    FilledTonalIconButton(
                        enabled = state.isRunning || state.isPaused,
                        onClick = onStop,
                        modifier = Modifier.testTag("roundtable_stop_btn"),
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = "結束互聊")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = guidanceText,
                    onValueChange = { guidanceText = it },
                    placeholder = { Text("使用者中途插話引導（下一位發言前插入）...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("roundtable_interject_input"),
                )
                IconButton(
                    enabled = guidanceText.isNotBlank(),
                    onClick = {
                        onInjectGuidance(guidanceText)
                        guidanceText = ""
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("roundtable_interject_send_btn"),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "送出插話引導")
                }
            }
        }
    }
}
