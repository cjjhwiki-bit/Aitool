package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MonoFontFamily

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
}

@Composable
fun RichMarkdownText(
    text: String,
    renderMarkdown: Boolean,
    renderLatex: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!renderMarkdown) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier,
            )
        }
        return
    }

    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val clipboard = LocalClipboardManager.current

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        Text(
                            text = formatInlineStyles(block.text, renderLatex),
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    is MdBlock.Paragraph -> {
                        Text(
                            text = formatInlineStyles(block.text, renderLatex),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    is MdBlock.Bullet -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = formatInlineStyles(block.text, renderLatex),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    is MdBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 20.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = formatInlineStyles(block.text, renderLatex),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is MdBlock.CodeBlock -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = block.language.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(block.code)) },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "複製程式碼",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = block.code,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseMarkdownBlocks(input: String): List<MdBlock> {
    if (input.isEmpty()) return emptyList()
    val lines = input.lines()
    val result = mutableListOf<MdBlock>()
    val paraBuf = mutableListOf<String>()
    var inCode = false
    var codeLang = ""
    val codeBuf = mutableListOf<String>()

    fun flushPara() {
        if (paraBuf.isNotEmpty()) {
            result.add(MdBlock.Paragraph(paraBuf.joinToString("\n")))
            paraBuf.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushPara()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuf.clear()
            } else {
                result.add(MdBlock.CodeBlock(codeLang, codeBuf.joinToString("\n")))
                inCode = false
                codeLang = ""
                codeBuf.clear()
            }
            continue
        }
        if (inCode) {
            codeBuf.add(line)
            continue
        }
        when {
            trimmed.isEmpty() -> flushPara()
            trimmed.startsWith("### ") -> {
                flushPara()
                result.add(MdBlock.Heading(3, trimmed.removePrefix("### ")))
            }
            trimmed.startsWith("## ") -> {
                flushPara()
                result.add(MdBlock.Heading(2, trimmed.removePrefix("## ")))
            }
            trimmed.startsWith("# ") -> {
                flushPara()
                result.add(MdBlock.Heading(1, trimmed.removePrefix("# ")))
            }
            trimmed.startsWith("> ") -> {
                flushPara()
                result.add(MdBlock.Quote(trimmed.removePrefix("> ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushPara()
                result.add(MdBlock.Bullet(trimmed.drop(2)))
            }
            else -> paraBuf.add(line)
        }
    }
    if (inCode) {
        result.add(MdBlock.CodeBlock(codeLang, codeBuf.joinToString("\n")))
    }
    flushPara()
    return result
}

@Composable
private fun formatInlineStyles(raw: String, renderLatex: Boolean): AnnotatedString {
    val processed = if (renderLatex) {
        raw.replace("\\frac", "÷")
            .replace("\\times", "×")
            .replace("\\cdot", "·")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace("\\neq", "≠")
            .replace("\\rightarrow", "→")
    } else {
        raw
    }

    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeFg = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        var i = 0
        while (i < processed.length) {
            when {
                processed.startsWith("**", i) -> {
                    val end = processed.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(processed.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(processed[i])
                        i++
                    }
                }
                processed[i] == '`' -> {
                    val end = processed.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg,
                                color = codeFg,
                                fontSize = 13.sp,
                            )
                        ) {
                            append(" ${processed.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append(processed[i])
                        i++
                    }
                }
                processed[i] == '*' && (i + 1 < processed.length && processed[i + 1] != ' ') -> {
                    val end = processed.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(processed.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(processed[i])
                        i++
                    }
                }
                else -> {
                    append(processed[i])
                    i++
                }
            }
        }
    }
}

fun stripMarkdownFormatting(input: String): String {
    return input
        .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), "")
        .replace("```", "")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        .trim()
}
